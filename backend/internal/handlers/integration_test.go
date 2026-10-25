package handlers_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"image"
	"image/png"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/internal/handlers"
	"github.com/kairagz27/master-coffee-go/internal/models"
	"github.com/kairagz27/master-coffee-go/internal/repository"
	"github.com/kairagz27/master-coffee-go/internal/seed"
	"github.com/kairagz27/master-coffee-go/internal/service"
	"github.com/kairagz27/master-coffee-go/migrations"
)

type harness struct {
	server *httptest.Server
	pool   *pgxpool.Pool
	svc    *service.Service
	t      *testing.T
}

func setup(t *testing.T) *harness {
	t.Helper()
	dsn := os.Getenv("TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("TEST_DATABASE_URL not set: PostgreSQL integration tests not executed")
	}
	ctx := context.Background()
	admin, e := pgxpool.New(ctx, dsn)
	if e != nil {
		t.Fatal(e)
	}
	schema := "test_" + strings.ReplaceAll(service.Token()[:16], "-", "_")
	schema = strings.ToLower(schema)
	quoted := `"` + schema + `"`
	if _, e = admin.Exec(ctx, "CREATE SCHEMA "+quoted); e != nil {
		admin.Close()
		t.Fatal(e)
	}
	cfg, e := pgxpool.ParseConfig(dsn)
	if e != nil {
		t.Fatal(e)
	}
	cfg.ConnConfig.RuntimeParams["search_path"] = schema
	cfg.ConnConfig.RuntimeParams["timezone"] = "UTC"
	cfg.MaxConns = 15
	p, e := pgxpool.NewWithConfig(ctx, cfg)
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() { p.Close(); _, _ = admin.Exec(ctx, "DROP SCHEMA "+quoted+" CASCADE"); admin.Close() })
	if e = migrations.Apply(ctx, p); e != nil {
		t.Fatal(e)
	}
	if e = migrations.Apply(ctx, p); e != nil {
		t.Fatal("repeated migration", e)
	}
	if e = seed.Apply(ctx, p, true, "integration-password-123"); e != nil {
		t.Fatal(e)
	}
	if e = seed.Apply(ctx, p, true, "integration-password-123"); e != nil {
		t.Fatal("repeated seed", e)
	}
	svc := &service.Service{Repo: &repository.Repository{Pool: p}, SessionTTL: time.Hour, QRExpiry: 30 * time.Minute}
	h := &handlers.Handler{Service: svc, UploadDir: t.TempDir(), Logger: slog.New(slog.NewTextHandler(io.Discard, nil))}
	server := httptest.NewServer(h.Router())
	t.Cleanup(server.Close)
	return &harness{server, p, svc, t}
}
func (h *harness) request(method, path, token string, body any, key string) (int, []byte) {
	h.t.Helper()
	var raw []byte
	if body != nil {
		raw, _ = json.Marshal(body)
	}
	req, e := http.NewRequest(method, h.server.URL+path, bytes.NewReader(raw))
	if e != nil {
		h.t.Fatal(e)
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if key != "" {
		req.Header.Set("Idempotency-Key", key)
	}
	res, e := http.DefaultClient.Do(req)
	if e != nil {
		h.t.Fatal(e)
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(res.Body)
	return res.StatusCode, b
}
func (h *harness) ok(method, path, token string, body any, want int, key string) []byte {
	h.t.Helper()
	status, b := h.request(method, path, token, body, key)
	if status != want {
		h.t.Fatalf("%s %s status%d want%d: %s", method, path, status, want, b)
	}
	return b
}
func parse[T any](t *testing.T, b []byte) T {
	t.Helper()
	var v T
	if e := json.Unmarshal(b, &v); e != nil {
		t.Fatal(e, string(b))
	}
	return v
}
func (h *harness) login(role string) models.Session {
	return parse[models.Session](h.t, h.ok("POST", "/api/v1/auth/login", "", map[string]string{"email": role + "@mastercoffee.local", "password": "integration-password-123"}, 200, ""))
}
func (h *harness) quote(token string, cafe int, product int) models.Quote {
	return parse[models.Quote](h.t, h.ok("POST", "/api/v1/orders/quote", token, models.QuoteInput{CafeID: cafe, Items: []models.ItemInput{{ProductID: product, SizeID: fmt.Sprintf("%d-350", product), Quantity: 1, AddonIDs: []string{"essence-cherry"}, OrderType: "takeaway", Temperature: "standard"}}}, 201, ""))
}
func (h *harness) order(token, quote, key string) models.Order {
	return parse[models.Order](h.t, h.ok("POST", "/api/v1/orders", token, map[string]string{"quote_id": quote}, 201, key))
}
func TestPostgresEndToEndAndSecurity(t *testing.T) {
	h := setup(t)
	customer := h.login("customer")
	staff := h.login("staff")
	admin := h.login("admin")
	h.ok("GET", "/readyz", "", nil, 200, "")
	catalog := parse[models.Catalog](t, h.ok("GET", "/api/v1/catalog?cafe_id=1", "", nil, 200, ""))
	if len(catalog.Products) != 18 || len(catalog.Cafes) != 12 || len(catalog.Addons) != 9 {
		t.Fatalf("incomplete catalogue %+v", catalog)
	}
	h.ok("GET", "/api/v1/auth/me", customer.Token, nil, 200, "")
	h.ok("GET", "/api/v1/staff/orders", customer.Token, nil, 403, "")
	h.ok("PUT", "/api/v1/admin/products/20", customer.Token, map[string]any{}, 403, "")
	h.ok("POST", "/api/menu", customer.Token, map[string]any{}, 404, "")
	h.ok("POST", "/api/v1/auth/register", "", map[string]any{"email": "escalate@example.com", "name": "Escalate", "password": "integration-password-123", "role": "admin"}, 400, "")
	other := parse[models.Session](t, h.ok("POST", "/api/v1/auth/register", "", map[string]string{"email": "other@example.com", "name": "Other", "password": "integration-password-123"}, 201, ""))
	q := h.quote(customer.Token, 1, 3)
	if q.Subtotal != 1350 || q.Total != 1310 || q.Discount != 40 {
		t.Fatalf("server price %+v", q)
	}
	h.ok("POST", "/api/v1/orders", customer.Token, map[string]any{"quote_id": q.ID, "total": 1}, 400, "tamper-amount-01")
	o := h.order(customer.Token, q.ID, "workflow-key-001")
	if o.QR == nil || strings.Contains(o.QR.Payload, customer.User.Email) {
		t.Fatal("missing or PII QR")
	}
	again := parse[models.Order](t, h.ok("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": q.ID}, 200, "workflow-key-001"))
	if again.ID != o.ID {
		t.Fatal("idempotency failed")
	}
	h.ok("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": q.ID}, 409, "another-key-001")
	h.ok("GET", "/api/v1/orders/"+o.ID, other.Token, nil, 404, "")
	h.ok("POST", "/api/v1/orders/"+o.ID+"/cancel", other.Token, map[string]any{}, 404, "")
	q2 := h.quote(customer.Token, 1, 3)
	h.ok("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": q2.ID}, 409, "workflow-key-001")
	h.ok("PATCH", "/api/v1/staff/orders/"+o.ID+"/status", staff.Token, map[string]string{"status": "accepted"}, 409, "")
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": o.QR.Payload}, 200, "")
	h.ok("POST", "/api/v1/staff/orders/"+o.ID+"/payment", customer.Token, map[string]any{}, 403, "")
	h.ok("POST", "/api/v1/staff/orders/"+o.ID+"/payment", staff.Token, map[string]string{"qr_token": o.QR.Payload}, 200, "")
	h.ok("POST", "/api/v1/staff/orders/"+o.ID+"/payment", staff.Token, map[string]any{}, 200, "")
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": o.QR.Payload}, 409, "")
	h.ok("POST", "/api/v1/staff/orders/"+o.ID+"/payment", staff.Token, map[string]string{"qr_token": o.QR.Token}, 409, "")
	h.ok("PATCH", "/api/v1/staff/orders/"+o.ID+"/status", staff.Token, map[string]string{"status": "ready"}, 409, "")
	h.ok("POST", "/api/v1/orders/"+o.ID+"/cancel", customer.Token, map[string]any{}, 409, "")
	for _, state := range []string{"accepted", "preparing", "ready", "completed", "completed"} {
		h.ok("PATCH", "/api/v1/staff/orders/"+o.ID+"/status", staff.Token, map[string]string{"status": state}, 200, "")
	}
	h.ok("PATCH", "/api/v1/staff/orders/"+o.ID+"/status", staff.Token, map[string]string{"status": ""}, 409, "")
	finished := parse[models.Order](t, h.ok("GET", "/api/v1/orders/"+o.ID, customer.Token, nil, 200, ""))
	if finished.Status != "completed" || finished.PaymentStatus != "paid" || finished.Items[0].Name != "Капучино" {
		t.Fatalf("workflow %+v", finished)
	}
	l := parse[models.Loyalty](t, h.ok("GET", "/api/v1/loyalty", customer.Token, nil, 200, ""))
	if l.QualifyingOrders != 1 || l.DiscountPercent != 3 {
		t.Fatalf("loyalty %+v", l)
	}
	lo := parse[models.Loyalty](t, h.ok("GET", "/api/v1/loyalty", other.Token, nil, 200, ""))
	if lo.QualifyingOrders != 0 {
		t.Fatal("loyalty leaked across owners")
	}
	var count int
	if e := h.pool.QueryRow(context.Background(), "SELECT count(*) FROM loyalty_ledger WHERE order_id=$1", o.ID).Scan(&count); e != nil || count != 1 {
		t.Fatalf("ledger%d %v", count, e)
	}
	var queue struct {
		Orders []models.Order `json:"orders"`
	}
	queue = parse[struct {
		Orders []models.Order `json:"orders"`
	}](t, h.ok("GET", "/api/v1/staff/orders", staff.Token, nil, 200, ""))
	if len(queue.Orders) != 0 {
		t.Fatal("terminal order left in active queue")
	}
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": o.Number}, 200, "")
	// Editing the menu cannot rewrite an existing receipt.
	p := catalog.Products[2]
	p.Name = "Changed cappuccino"
	p.Sizes[0].Price += 100
	h.ok("PUT", "/api/v1/admin/products/3", admin.Token, p, 200, "")
	unchanged := parse[models.Order](t, h.ok("GET", "/api/v1/orders/"+o.ID, customer.Token, nil, 200, ""))
	if unchanged.Items[0].Name != "Капучино" || unchanged.Total != o.Total {
		t.Fatal("history snapshot changed")
	}
	h.ok("POST", "/api/v1/auth/logout", customer.Token, map[string]any{}, 204, "")
	h.ok("GET", "/api/v1/auth/me", customer.Token, nil, 401, "")
	h.ok("GET", "/api/v1/auth/me", other.Token, nil, 200, "")
}
func TestPostgresConcurrentCreationAwardAndQuoteChanges(t *testing.T) {
	h := setup(t)
	customer := h.login("customer")
	staff := h.login("staff")
	admin := h.login("admin")
	q := h.quote(customer.Token, 1, 3)
	const concurrency = 10
	var wg sync.WaitGroup
	ids := make(chan string, concurrency)
	errs := make(chan string, concurrency)
	for range concurrency {
		wg.Add(1)
		go func() {
			defer wg.Done()
			status, b := h.request("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": q.ID}, "concurrent-key-001")
			if status != 200 && status != 201 {
				errs <- fmt.Sprintf("%d %s", status, b)
				return
			}
			var o models.Order
			if e := json.Unmarshal(b, &o); e != nil {
				errs <- e.Error()
				return
			}
			ids <- o.ID
		}()
	}
	wg.Wait()
	close(ids)
	close(errs)
	for e := range errs {
		t.Error(e)
	}
	id := ""
	for got := range ids {
		if id != "" && got != id {
			t.Fatal("duplicate orders")
		}
		id = got
	}
	if id == "" {
		t.Fatal("no order")
	}
	h.ok("POST", "/api/v1/staff/orders/"+id+"/payment", staff.Token, map[string]any{}, 200, "")
	for _, state := range []string{"accepted", "preparing", "ready"} {
		h.ok("PATCH", "/api/v1/staff/orders/"+id+"/status", staff.Token, map[string]string{"status": state}, 200, "")
	}
	for range concurrency {
		wg.Add(1)
		go func() {
			defer wg.Done()
			status, b := h.request("PATCH", "/api/v1/staff/orders/"+id+"/status", staff.Token, map[string]string{"status": "completed"}, "")
			if status != 200 {
				t.Errorf("completion %d %s", status, b)
			}
		}()
	}
	wg.Wait()
	var n int
	if e := h.pool.QueryRow(context.Background(), "SELECT qualifying_orders FROM users WHERE id=$1", customer.User.ID).Scan(&n); e != nil || n != 1 {
		t.Fatalf("duplicate award %d %v", n, e)
	}
	stale := h.quote(customer.Token, 1, 3)
	catalog := parse[models.Catalog](t, h.ok("GET", "/api/v1/catalog", "", nil, 200, ""))
	p := catalog.Products[2]
	p.Sizes[0].Price++
	h.ok("PUT", "/api/v1/admin/products/3", admin.Token, p, 200, "")
	h.ok("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": stale.ID}, 409, "stale-price-key01")
	h.ok("PATCH", "/api/v1/admin/cafes/1/products/3", admin.Token, map[string]bool{"available": false}, 200, "")
	h.ok("POST", "/api/v1/orders/quote", customer.Token, models.QuoteInput{CafeID: 1, Items: []models.ItemInput{{ProductID: 3, SizeID: "3-350", Quantity: 1, AddonIDs: []string{}, OrderType: "takeaway", Temperature: "standard"}}}, 409, "")
	h.ok("PATCH", "/api/v1/admin/cafes/1/products/3", admin.Token, map[string]bool{"available": true}, 200, "")
	exp := h.quote(customer.Token, 1, 3)
	_, e := h.pool.Exec(context.Background(), "UPDATE quotes SET expires_at='2000-01-01T00:00:00Z' WHERE id=$1", exp.ID)
	if e != nil {
		t.Fatal(e)
	}
	h.ok("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": exp.ID}, 409, "expired-quote-01")
	// Eligibility is snapshotted: a juice-only receipt gets a discount, no loyalty receipt.
	juice := h.quote(customer.Token, 1, 19)
	jo := h.order(customer.Token, juice.ID, "juice-key-00001")
	h.ok("POST", "/api/v1/staff/orders/"+jo.ID+"/payment", staff.Token, map[string]any{}, 200, "")
	for _, state := range []string{"accepted", "preparing", "ready", "completed"} {
		h.ok("PATCH", "/api/v1/staff/orders/"+jo.ID+"/status", staff.Token, map[string]string{"status": state}, 200, "")
	}
	l := parse[models.Loyalty](t, h.ok("GET", "/api/v1/loyalty", customer.Token, nil, 200, ""))
	if l.QualifyingOrders != 1 || juice.Discount <= 0 {
		t.Fatalf("juice eligibility %+v quote %+v", l, juice)
	}
}
func TestPostgresQRCafeExpiryCancellationSessionAndImages(t *testing.T) {
	h := setup(t)
	customer := h.login("customer")
	staff := h.login("staff")
	admin := h.login("admin")
	q := h.quote(customer.Token, 2, 3)
	o := h.order(customer.Token, q.ID, "foreign-cafe-key01")
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": o.QR.Payload}, 403, "")
	h.ok("GET", "/api/v1/staff/orders/"+o.ID, staff.Token, nil, 403, "")
	h.ok("POST", "/api/v1/staff/orders/"+o.ID+"/payment", staff.Token, map[string]any{}, 403, "")
	h.ok("POST", "/api/v1/orders/"+o.ID+"/cancel", customer.Token, map[string]any{}, 200, "")
	h.ok("POST", "/api/v1/staff/orders/"+o.ID+"/payment", admin.Token, map[string]any{}, 409, "")
	q = h.quote(customer.Token, 1, 3)
	o = h.order(customer.Token, q.ID, "expire-qr-key001")
	if _, e := h.pool.Exec(context.Background(), "UPDATE orders SET qr_expires_at=now()-interval '1 second' WHERE id=$1", o.ID); e != nil {
		t.Fatal(e)
	}
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": o.QR.Payload}, 410, "")
	fresh := parse[models.Order](t, h.ok("POST", "/api/v1/orders/"+o.ID+"/qr", customer.Token, map[string]any{}, 200, ""))
	if fresh.QR.Token == o.QR.Token {
		t.Fatal("refresh didn't rotate QR")
	}
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": o.QR.Payload}, 404, "")
	h.ok("POST", "/api/v1/staff/orders/lookup", staff.Token, map[string]string{"code": fresh.QR.Payload}, 200, "")
	// New service/repository instances restore the existing DB-backed session.
	replacement := &service.Service{Repo: &repository.Repository{Pool: h.pool}}
	if u, e := replacement.Authenticate(context.Background(), customer.Token); e != nil || u.ID != customer.User.ID {
		t.Fatal("session restore", u, e)
	}
	if _, e := h.pool.Exec(context.Background(), "UPDATE sessions SET expires_at=now()-interval '1 second' WHERE token_hash=$1", service.Hash(customer.Token)); e != nil {
		t.Fatal(e)
	}
	h.ok("GET", "/api/v1/auth/me", customer.Token, nil, 401, "")
	var b bytes.Buffer
	if e := png.Encode(&b, image.NewRGBA(image.Rect(0, 0, 2, 2))); e != nil {
		t.Fatal(e)
	}
	req, _ := http.NewRequest("POST", h.server.URL+"/api/v1/admin/images", bytes.NewReader(b.Bytes()))
	req.Header.Set("Authorization", "Bearer "+admin.Token)
	req.Header.Set("Content-Type", "image/png")
	res, e := http.DefaultClient.Do(req)
	if e != nil {
		t.Fatal(e)
	}
	raw, _ := io.ReadAll(res.Body)
	res.Body.Close()
	if res.StatusCode != 201 {
		t.Fatal(string(raw))
	}
	var uploaded map[string]string
	if e = json.Unmarshal(raw, &uploaded); e != nil {
		t.Fatal(e)
	}
	r, e := http.Get(h.server.URL + uploaded["url"])
	if e != nil {
		t.Fatal(e)
	}
	data, _ := io.ReadAll(r.Body)
	r.Body.Close()
	if r.StatusCode != 200 || !bytes.Equal(data, b.Bytes()) {
		t.Fatal("uploaded image mismatch")
	}
	h.ok("PUT", "/api/v1/admin/stories/new-story", admin.Token, map[string]any{"title": "Welcome", "image_url": uploaded["url"], "active": true}, 200, "")
	h.ok("DELETE", "/api/v1/admin/stories/new-story", admin.Token, nil, 204, "")
}

func TestPostgresLegacyMigrationAndPagination(t *testing.T) {
	h := setup(t)
	customer := h.login("customer")
	staff := h.login("staff")
	ctx := context.Background()
	old := h.quote(customer.Token, 1, 3)
	order := h.order(customer.Token, old.ID, "legacy-order-key01")
	pending := h.quote(customer.Token, 1, 3)
	for _, sql := range []string{
		"UPDATE products SET document=document-'loyalty_eligible' WHERE id IN(3,19)",
		"UPDATE products SET document=jsonb_set(document,'{loyalty_eligible}','false'::jsonb) WHERE id=4",
		"UPDATE order_items SET snapshot=snapshot-'loyalty_eligible'",
		"UPDATE quotes SET snapshot=jsonb_set(snapshot,'{items,0}',(snapshot#>'{items,0}')-'loyalty_eligible')",
		"DELETE FROM schema_migrations WHERE version='002_explicit_loyalty_eligibility.sql'",
	} {
		if _, e := h.pool.Exec(ctx, sql); e != nil {
			t.Fatal(e)
		}
	}
	if e := migrations.Apply(ctx, h.pool); e != nil {
		t.Fatal(e)
	}
	for _, v := range []struct {
		id   int
		want bool
	}{{3, true}, {4, false}, {19, false}} {
		var eligible bool
		if e := h.pool.QueryRow(ctx, "SELECT (document->>'loyalty_eligible')::boolean FROM products WHERE id=$1", v.id).Scan(&eligible); e != nil || eligible != v.want {
			t.Fatalf("backfill product%d eligible%v want%v %v", v.id, eligible, v.want, e)
		}
	}
	var itemEligible bool
	if e := h.pool.QueryRow(ctx, "SELECT (snapshot->>'loyalty_eligible')::boolean FROM order_items WHERE order_id=$1", order.ID).Scan(&itemEligible); e != nil || !itemEligible {
		t.Fatal("legacy snapshot not backfilled", e)
	}
	h.ok("POST", "/api/v1/orders", customer.Token, map[string]string{"quote_id": pending.ID}, 409, "legacy-expired-key01")
	for i := 0; i < 2; i++ {
		q := h.quote(customer.Token, 1, 3)
		o := h.order(customer.Token, q.ID, fmt.Sprintf("pagination-order-key%d", i))
		h.ok("POST", "/api/v1/orders/"+o.ID+"/cancel", customer.Token, map[string]any{}, 200, "")
	}
	var page struct {
		Orders     []models.Order `json:"orders"`
		NextBefore *int64         `json:"next_before"`
	}
	page = parse[struct {
		Orders     []models.Order `json:"orders"`
		NextBefore *int64         `json:"next_before"`
	}](t, h.ok("GET", "/api/v1/orders?limit=1", customer.Token, nil, 200, ""))
	if len(page.Orders) != 1 || page.NextBefore == nil {
		t.Fatal("missing history cursor")
	}
	firstID := page.Orders[0].ID
	page = parse[struct {
		Orders     []models.Order `json:"orders"`
		NextBefore *int64         `json:"next_before"`
	}](t, h.ok("GET", fmt.Sprintf("/api/v1/orders?limit=1&before=%d", *page.NextBefore), customer.Token, nil, 200, ""))
	if len(page.Orders) != 1 || page.Orders[0].ID == firstID {
		t.Fatal("history cursor overlap")
	}
	queue := parse[struct {
		Orders []models.Order `json:"orders"`
	}](t, h.ok("GET", "/api/v1/staff/orders?limit=1", staff.Token, nil, 200, ""))
	if len(queue.Orders) != 1 || queue.Orders[0].ID != order.ID {
		t.Fatal("active filtering must precede SQL limit")
	}
	if _, e := h.pool.Exec(ctx, "UPDATE schema_migrations SET checksum='corrupted' WHERE version='001_initial.sql'"); e != nil {
		t.Fatal(e)
	}
	if e := migrations.Apply(ctx, h.pool); e == nil {
		t.Fatal("corrupted applied migration checksum accepted")
	}
}

func TestPostgresOperatorProvisioningAndExactJSON(t *testing.T) {
	h := setup(t)
	customer := h.login("customer")
	admin := h.login("admin")
	ctx := context.Background()
	// A failed session insert must not leave a registered account behind.
	failedUser := models.User{ID: service.Token(), Email: "atomic-register@example.test", Name: "Atomic", Role: "customer"}
	err := h.svc.Repo.RegisterSession(ctx, failedUser, "unused-test-hash", service.Hash(customer.Token), time.Now().Add(time.Hour))
	if err == nil {
		t.Fatal("expected duplicate session constraint failure")
	}
	var accountCount int
	if err = h.pool.QueryRow(ctx, "SELECT count(*) FROM users WHERE id=$1", failedUser.ID).Scan(&accountCount); err != nil || accountCount != 0 {
		t.Fatal("partial registration persisted", accountCount, err)
	}
	staffUser, err := h.svc.ProvisionOperator(ctx, service.OperatorInput{Email: "cafe2@example.test", Name: "Кофейня 2", Role: "staff", CafeID: 2}, "operator-password-123")
	if err != nil || staffUser.CafeID == nil || *staffUser.CafeID != 2 || staffUser.EmailVerified {
		t.Fatal("staff provisioning", staffUser, err)
	}
	staff := parse[models.Session](t, h.ok("POST", "/api/v1/auth/login", "", map[string]string{"email": staffUser.Email, "password": "operator-password-123"}, 200, ""))
	quote := h.quote(customer.Token, 1, 3)
	order := h.order(customer.Token, quote.ID, "provision-check-key")
	h.ok("GET", "/api/v1/staff/orders/"+order.ID, staff.Token, nil, 403, "")
	h.ok("POST", "/api/v1/staff/orders/"+order.ID+"/payment", staff.Token, map[string]any{}, 403, "")
	for _, input := range []service.OperatorInput{{Email: customer.User.Email, Name: "Escalation", Role: "admin"}, {Email: "missing@example.test", Name: "Missing", Role: "staff", CafeID: 999}, {Email: "invalid@example.test", Name: "Invalid", Role: "customer"}} {
		if _, err = h.svc.ProvisionOperator(ctx, input, "operator-password-123"); err == nil {
			t.Fatal("invalid or existing account provisioned", input.Role)
		}
	}
	unchanged := parse[models.User](t, h.ok("GET", "/api/v1/auth/me", customer.Token, nil, 200, ""))
	if unchanged.Role != "customer" {
		t.Fatal("customer silently promoted")
	}
	newAdmin, err := h.svc.ProvisionOperator(ctx, service.OperatorInput{Email: "operator-admin@example.test", Name: "Admin", Role: "admin"}, "operator-password-123")
	if err != nil || newAdmin.Role != "admin" || newAdmin.CafeID != nil {
		t.Fatal("admin provisioning", err)
	}
	for _, test := range []struct{ path, body, token string }{
		{"/api/v1/orders/quote", `{"CAFE_ID":1,"items":[]}`, customer.Token},
		{"/api/v1/orders/quote", `{"cafe_id":1,"cafe_id":2,"items":[]}`, customer.Token},
		{"/api/v1/orders/quote", `{"cafe_id":1,"items":[null]}`, customer.Token},
		{"/api/v1/orders/quote", `{"cafe_id":1,"items":[{"product_id":3,"size_id":"3-350","quantity":1,"addon_ids":null,"sugar":0,"order_type":"takeaway","temperature":"standard"}]}`, customer.Token},
		{"/api/v1/admin/products/3", `{"id":3,"name":"Кофе","category":"Кофе","description":"","image_key":"cappuccino","is_drink":true,"loyalty_eligible":true,"sizes":[{"id":"3-350","label":"350 мл","price":null}]}`, admin.Token},
	} {
		method := "POST"
		if strings.Contains(test.path, "admin/products") {
			method = "PUT"
		}
		req, e := http.NewRequest(method, h.server.URL+test.path, strings.NewReader(test.body))
		if e != nil {
			t.Fatal(e)
		}
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+test.token)
		res, e := http.DefaultClient.Do(req)
		if e != nil {
			t.Fatal(e)
		}
		body, _ := io.ReadAll(res.Body)
		res.Body.Close()
		if res.StatusCode != 400 {
			t.Fatalf("invalid JSON accepted: %d %s", res.StatusCode, body)
		}
	}
	status, body := h.request("GET", "/uploads/"+strings.Repeat("a", 43)+".png", "", nil, "")
	if status != 404 {
		t.Fatal("missing image", status)
	}
	var jsonError map[string]any
	if err = json.Unmarshal(body, &jsonError); err != nil || jsonError["error"] == nil {
		t.Fatal("image error is not JSON", string(body))
	}
}
