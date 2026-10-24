package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"image"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"log/slog"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/kairagz27/master-coffee-go/internal/models"
	"github.com/kairagz27/master-coffee-go/internal/service"
)

type Handler struct {
	Service   *service.Service
	Origins   []string
	UploadDir string
	Logger    *slog.Logger
	limiter   *rateLimiter
}
type userKey struct{}
type rateBucket struct {
	count int
	until time.Time
}
type rateLimiter struct {
	mu      sync.Mutex
	entries map[string]rateBucket
}

func (l *rateLimiter) allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	if len(l.entries) > 10000 {
		for k, v := range l.entries {
			if now.After(v.until) {
				delete(l.entries, k)
			}
		}
		if len(l.entries) > 10000 {
			return false
		}
	}
	v := l.entries[key]
	if now.After(v.until) {
		v = rateBucket{until: now.Add(time.Minute)}
	}
	v.count++
	l.entries[key] = v
	return v.count <= 20
}
func (h *Handler) Router() http.Handler {
	if h.Logger == nil {
		h.Logger = slog.Default()
	}
	h.limiter = &rateLimiter{entries: map[string]rateBucket{}}
	r := chi.NewRouter()
	r.Use(middleware.RequestID, h.recoverJSON, requestTimeout, h.headers)
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) { write(w, 200, map[string]string{"status": "ok"}) })
	r.Get("/readyz", func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()
		if e := h.Service.Repo.Pool.Ping(ctx); e != nil {
			h.fail(w, r, service.Fail(503, "database_unavailable", "Database unavailable"))
			return
		}
		write(w, 200, map[string]string{"status": "ready"})
	})
	r.Get("/uploads/{name}", h.uploadRead)
	r.Route("/api/v1", func(r chi.Router) {
		r.With(h.authLimit).Post("/auth/register", h.register)
		r.With(h.authLimit).Post("/auth/login", h.login)
		r.Get("/catalog", h.catalog)
		r.Get("/cafes", h.cafes)
		r.Group(func(r chi.Router) {
			r.Use(h.authenticate)
			r.Get("/auth/me", func(w http.ResponseWriter, r *http.Request) { write(w, 200, current(r)) })
			r.Post("/auth/logout", h.logout)
			r.Get("/loyalty", h.loyalty)
			r.Post("/orders/quote", h.quote)
			r.Post("/orders", h.create)
			r.Get("/orders", h.orders)
			r.Get("/orders/{id}", h.order)
			r.Post("/orders/{id}/cancel", h.mutation("cancel"))
			r.Post("/orders/{id}/qr", h.mutation("qr"))
			r.Group(func(r chi.Router) {
				r.Use(role("staff", "admin"))
				r.Get("/staff/orders", h.staffOrders)
				r.Post("/staff/orders/lookup", h.lookup)
				r.Get("/staff/orders/{id}", h.staffOrder)
				r.Post("/staff/orders/{id}/payment", h.mutation("payment"))
				r.Patch("/staff/orders/{id}/status", h.mutation("status"))
			})
			r.Group(func(r chi.Router) {
				r.Use(role("admin"))
				r.Get("/admin/catalog", h.adminCatalog)
				r.Put("/admin/products/{id}", h.saveProduct)
				r.Patch("/admin/cafes/{cafe}/products/{id}", h.availability)
				r.Put("/admin/stories/{id}", h.saveStory)
				r.Delete("/admin/stories/{id}", h.deleteStory)
				r.Post("/admin/images", h.upload)
			})
		})
	})
	r.NotFound(func(w http.ResponseWriter, r *http.Request) {
		h.fail(w, r, service.Fail(404, "not_found", "Route not found"))
	})
	r.MethodNotAllowed(func(w http.ResponseWriter, r *http.Request) {
		h.fail(w, r, service.Fail(405, "method_not_allowed", "Method not allowed"))
	})
	return r
}
func (h *Handler) headers(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		origin := r.Header.Get("Origin")
		for _, v := range h.Origins {
			if v == origin && v != "" {
				w.Header().Set("Access-Control-Allow-Origin", origin)
				w.Header().Set("Vary", "Origin")
				w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, Idempotency-Key, If-None-Match")
				w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
			}
		}
		if r.Method == "OPTIONS" {
			w.WriteHeader(204)
			return
		}
		start := time.Now()
		rec := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(rec, r)
		h.Logger.Info("request", "method", r.Method, "route", chi.RouteContext(r.Context()).RoutePattern(), "status", rec.Status(), "elapsed_ms", time.Since(start).Milliseconds(), "request_id", middleware.GetReqID(r.Context()))
	})
}
func write(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if v != nil {
		_ = json.NewEncoder(w).Encode(v)
	}
}
func (h *Handler) fail(w http.ResponseWriter, r *http.Request, e error) {
	if errors.Is(e, context.DeadlineExceeded) {
		e = service.Fail(504, "request_timeout", "Request timed out; retry safely with the same idempotency key")
	}
	var v *service.Error
	if !errors.As(e, &v) {
		h.Logger.Error("request failed", "error", e.Error(), "request_id", middleware.GetReqID(r.Context()))
		v = &service.Error{Status: 500, Code: "internal_error", Message: "Internal server error"}
	}
	write(w, v.Status, map[string]any{"error": map[string]string{"code": v.Code, "message": v.Message, "request_id": middleware.GetReqID(r.Context())}})
}
func decode(w http.ResponseWriter, r *http.Request, v any) error {
	if !strings.HasPrefix(strings.ToLower(r.Header.Get("Content-Type")), "application/json") {
		return service.Fail(415, "unsupported_media_type", "Use application/json")
	}
	r.Body = http.MaxBytesReader(w, r.Body, 64*1024)
	d := json.NewDecoder(r.Body)
	var raw json.RawMessage
	if e := d.Decode(&raw); e != nil {
		return service.Fail(400, "invalid_json", "Invalid JSON, unknown fields or body larger than 64 KiB")
	}
	raw = bytes.TrimSpace(raw)
	if len(raw) == 0 || raw[0] != '{' {
		return service.Fail(400, "invalid_json", "JSON object required")
	}
	strict := json.NewDecoder(bytes.NewReader(raw))
	strict.DisallowUnknownFields()
	if e := strict.Decode(v); e != nil {
		return service.Fail(400, "invalid_json", "Invalid JSON or unknown fields")
	}
	var trailing any
	if e := d.Decode(&trailing); e != io.EOF {
		return service.Fail(400, "invalid_json", "Exactly one JSON object required")
	}
	return nil
}
func (h *Handler) authLimit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host, _, e := net.SplitHostPort(r.RemoteAddr)
		if e != nil {
			host = r.RemoteAddr
		}
		if !h.limiter.allow(host) {
			w.Header().Set("Retry-After", "60")
			h.fail(w, r, service.Fail(429, "rate_limited", "Too many attempts; try again later"))
			return
		}
		next.ServeHTTP(w, r)
	})
}
func (h *Handler) authenticate(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := bearer(r)
		u, e := h.Service.Authenticate(r.Context(), token)
		if e != nil {
			h.fail(w, r, e)
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), userKey{}, u)))
	})
}
func bearer(r *http.Request) string {
	v := r.Header.Get("Authorization")
	if !strings.HasPrefix(v, "Bearer ") {
		return ""
	}
	return strings.TrimPrefix(v, "Bearer ")
}
func current(r *http.Request) models.User { return r.Context().Value(userKey{}).(models.User) }
func role(roles ...string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			u := current(r)
			for _, role := range roles {
				if u.Role == role {
					next.ServeHTTP(w, r)
					return
				}
			}
			write(w, 403, map[string]any{"error": map[string]string{"code": "forbidden", "message": "Role does not permit this operation", "request_id": middleware.GetReqID(r.Context())}})
		})
	}
}
func (h *Handler) register(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Email    string `json:"email"`
		Password string `json:"password"`
		Name     string `json:"name"`
	}
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	v, e := h.Service.Register(r.Context(), in.Email, in.Password, in.Name)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 201, v)
}
func (h *Handler) login(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	v, e := h.Service.Login(r.Context(), in.Email, in.Password)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, v)
}
func (h *Handler) logout(w http.ResponseWriter, r *http.Request) {
	if e := h.Service.Logout(r.Context(), bearer(r)); e != nil {
		h.fail(w, r, e)
		return
	}
	w.WriteHeader(204)
}
func queryInt(r *http.Request, key string) int {
	v, _ := strconv.Atoi(r.URL.Query().Get(key))
	return v
}
func (h *Handler) catalog(w http.ResponseWriter, r *http.Request)      { h.catalogResponse(w, r, false) }
func (h *Handler) adminCatalog(w http.ResponseWriter, r *http.Request) { h.catalogResponse(w, r, true) }
func (h *Handler) catalogResponse(w http.ResponseWriter, r *http.Request, all bool) {
	v, e := h.Service.Catalog(r.Context(), queryInt(r, "cafe_id"), all)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, v)
}
func (h *Handler) cafes(w http.ResponseWriter, r *http.Request) {
	v, e := h.Service.Catalog(r.Context(), 1, false)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, map[string]any{"cafes": v.Cafes})
}
func (h *Handler) loyalty(w http.ResponseWriter, r *http.Request) {
	v, e := h.Service.Loyalty(r.Context(), current(r))
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, v)
}
func (h *Handler) quote(w http.ResponseWriter, r *http.Request) {
	var in models.QuoteInput
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	v, e := h.Service.Quote(r.Context(), current(r), in)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 201, v)
}
func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	var in struct {
		QuoteID string `json:"quote_id"`
	}
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	v, created, e := h.Service.Create(r.Context(), current(r), in.QuoteID, r.Header.Get("Idempotency-Key"))
	if e != nil {
		h.fail(w, r, e)
		return
	}
	status := 200
	if created {
		status = 201
	}
	w.Header().Set("Location", "/api/v1/orders/"+v.ID)
	write(w, status, v)
}
func (h *Handler) order(w http.ResponseWriter, r *http.Request)      { h.orderResponse(w, r, false) }
func (h *Handler) staffOrder(w http.ResponseWriter, r *http.Request) { h.orderResponse(w, r, true) }
func (h *Handler) orderResponse(w http.ResponseWriter, r *http.Request, staff bool) {
	v, e := h.Service.Order(r.Context(), current(r), chi.URLParam(r, "id"), staff)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, v)
}
func (h *Handler) orders(w http.ResponseWriter, r *http.Request)      { h.list(w, r, false) }
func (h *Handler) staffOrders(w http.ResponseWriter, r *http.Request) { h.list(w, r, true) }
func (h *Handler) list(w http.ResponseWriter, r *http.Request, staff bool) {
	limit := queryInt(r, "limit")
	if limit < 1 || limit > 100 {
		limit = 20
	}
	before, _ := strconv.ParseInt(r.URL.Query().Get("before"), 10, 64)
	v, e := h.Service.Orders(r.Context(), current(r), staff, queryInt(r, "cafe_id"), r.URL.Query().Get("search"), before, limit)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	var next *int64
	if len(v) == limit {
		n := v[len(v)-1].Sequence
		next = &n
	}
	write(w, 200, map[string]any{"orders": v, "next_before": next})
}
func (h *Handler) lookup(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Code string `json:"code"`
	}
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	if len(in.Code) == 0 || len(in.Code) > 200 {
		h.fail(w, r, service.Fail(422, "validation", "Order number or QR required"))
		return
	}
	v, e := h.Service.Lookup(r.Context(), current(r), in.Code)
	if e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, v)
}
func (h *Handler) mutation(action string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		value := ""
		switch action {
		case "status":
			var in struct {
				Status string `json:"status"`
			}
			if e := decode(w, r, &in); e != nil {
				h.fail(w, r, e)
				return
			}
			value = in.Status
		case "payment":
			var in struct {
				Token string `json:"qr_token"`
			}
			if e := decode(w, r, &in); e != nil {
				h.fail(w, r, e)
				return
			}
			value = in.Token
		default:
			var in struct{}
			if e := decode(w, r, &in); e != nil {
				h.fail(w, r, e)
				return
			}
		}
		v, e := h.Service.Mutate(r.Context(), current(r), chi.URLParam(r, "id"), action, value)
		if e != nil {
			h.fail(w, r, e)
			return
		}
		write(w, 200, v)
	}
}
func (h *Handler) saveProduct(w http.ResponseWriter, r *http.Request) {
	var p models.Product
	if e := decode(w, r, &p); e != nil {
		h.fail(w, r, e)
		return
	}
	id, _ := strconv.Atoi(chi.URLParam(r, "id"))
	if e := h.Service.SaveProduct(r.Context(), id, p, r.Header.Get("If-None-Match") == "*"); e != nil {
		h.fail(w, r, e)
		return
	}
	p.ID = id
	write(w, 200, p)
}
func (h *Handler) availability(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Available *bool `json:"available"`
	}
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	c, _ := strconv.Atoi(chi.URLParam(r, "cafe"))
	p, _ := strconv.Atoi(chi.URLParam(r, "id"))
	if in.Available == nil || c < 1 || p < 1 {
		h.fail(w, r, service.Fail(422, "validation", "Cafe, product and available boolean required"))
		return
	}
	if e := h.Service.SetAvailability(r.Context(), c, p, *in.Available); e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, map[string]bool{"available": *in.Available})
}
func (h *Handler) saveStory(w http.ResponseWriter, r *http.Request) {
	var in models.Story
	if e := decode(w, r, &in); e != nil {
		h.fail(w, r, e)
		return
	}
	in.ID = chi.URLParam(r, "id")
	if e := h.Service.SaveStory(r.Context(), in.ID, in); e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 200, in)
}
func (h *Handler) deleteStory(w http.ResponseWriter, r *http.Request) {
	if e := h.Service.DeleteStory(r.Context(), chi.URLParam(r, "id")); e != nil {
		h.fail(w, r, e)
		return
	}
	w.WriteHeader(204)
}
func (h *Handler) upload(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 5*1024*1024)
	b, e := io.ReadAll(r.Body)
	if e != nil {
		h.fail(w, r, service.Fail(413, "image_too_large", "Image maximum is 5 MiB"))
		return
	}
	mime := http.DetectContentType(b)
	ext := ""
	if mime == "image/png" {
		ext = ".png"
	}
	if mime == "image/jpeg" {
		ext = ".jpg"
	}
	if ext == "" {
		h.fail(w, r, service.Fail(415, "invalid_image", "PNG or JPEG bytes required"))
		return
	}
	cfg, _, e := image.DecodeConfig(bytes.NewReader(b))
	if e != nil || cfg.Width < 1 || cfg.Height < 1 || cfg.Width > 4096 || cfg.Height > 4096 {
		h.fail(w, r, service.Fail(422, "invalid_image", "Image must decode and be at most 4096 x 4096"))
		return
	}
	if _, _, e = image.Decode(bytes.NewReader(b)); e != nil {
		h.fail(w, r, service.Fail(422, "invalid_image", "Image is truncated or cannot be decoded"))
		return
	}
	if e = os.MkdirAll(h.UploadDir, 0750); e != nil {
		h.fail(w, r, e)
		return
	}
	name := service.Token() + ext
	if e = os.WriteFile(filepath.Join(h.UploadDir, name), b, 0640); e != nil {
		h.fail(w, r, e)
		return
	}
	write(w, 201, map[string]string{"url": "/uploads/" + name})
}
func (h *Handler) uploadRead(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if len(name) < 47 || len(name) > 48 || strings.ContainsAny(name, "/\\") || (filepath.Ext(name) != ".png" && filepath.Ext(name) != ".jpg") {
		h.fail(w, r, service.Fail(404, "not_found", "Image not found"))
		return
	}
	w.Header().Set("Cache-Control", "public, max-age=86400, immutable")
	w.Header().Set("Content-Security-Policy", "default-src 'none'")
	http.ServeFile(w, r, filepath.Join(h.UploadDir, name))
}

func requestTimeout(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
		defer cancel()
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}
func (h *Handler) recoverJSON(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if recover() != nil {
				h.Logger.Error("request panic", "request_id", middleware.GetReqID(r.Context()))
				h.fail(w, r, service.Fail(500, "internal_error", "Internal server error"))
			}
		}()
		next.ServeHTTP(w, r)
	})
}
