package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net/mail"
	"net/url"
	"reflect"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/kairagz27/master-coffee-go/internal/models"
	"github.com/kairagz27/master-coffee-go/internal/repository"
	"golang.org/x/crypto/bcrypt"
)

type Error struct {
	Status        int
	Code, Message string
}

func (e *Error) Error() string                    { return e.Code + ": " + e.Message }
func Fail(status int, code, message string) error { return &Error{status, code, message} }
func Missing(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return Fail(404, "not_found", "Resource not found")
	}
	return err
}

type Service struct {
	Repo                 *repository.Repository
	SessionTTL, QRExpiry time.Duration
	Now                  func() time.Time
}

func (s *Service) now() time.Time {
	if s.Now != nil {
		return s.Now().UTC()
	}
	return time.Now().UTC()
}
func Token() string {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(err)
	}
	return base64.RawURLEncoding.EncodeToString(b[:])
}
func Hash(t string) string { h := sha256.Sum256([]byte(t)); return hex.EncodeToString(h[:]) }
func (s *Service) Register(ctx context.Context, email, password, name string) (models.Session, error) {
	email = strings.ToLower(strings.TrimSpace(email))
	name = strings.TrimSpace(name)
	a, e := mail.ParseAddress(email)
	if e != nil || a.Address != email || len(email) > 254 || len(password) < 12 || len(password) > 72 || utf8.RuneCountInString(name) < 1 || utf8.RuneCountInString(name) > 100 {
		return models.Session{}, Fail(422, "validation", "Valid email, name (1–100 characters), and password (12–72 bytes) required")
	}
	hash, e := bcrypt.GenerateFromPassword([]byte(password), 12)
	if e != nil {
		return models.Session{}, e
	}
	u := models.User{ID: Token(), Email: email, Name: name, Role: "customer"}
	v := s.newSession(u)
	if e = s.Repo.RegisterSession(ctx, u, string(hash), Hash(v.Token), v.ExpiresAt); e != nil {
		if repository.Unique(e) {
			return models.Session{}, Fail(409, "account_exists", "Account already exists")
		}
		return models.Session{}, e
	}
	return v, nil
}

var dummyHash, _ = bcrypt.GenerateFromPassword([]byte("invalid-account-timing-password"), 12)

func (s *Service) Login(ctx context.Context, email, password string) (models.Session, error) {
	u, hash, e := s.Repo.Credentials(ctx, strings.ToLower(strings.TrimSpace(email)))
	if e != nil && !errors.Is(e, pgx.ErrNoRows) {
		return models.Session{}, e
	}
	if e != nil {
		hash = string(dummyHash)
	}
	valid := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) == nil
	if !valid || e != nil {
		return models.Session{}, Fail(401, "invalid_credentials", "Email or password is incorrect")
	}
	return s.session(ctx, u)
}
func (s *Service) session(ctx context.Context, u models.User) (models.Session, error) {
	v := s.newSession(u)
	e := s.Repo.InsertSession(ctx, Hash(v.Token), u.ID, v.ExpiresAt)
	return v, e
}
func (s *Service) newSession(u models.User) models.Session {
	ttl := s.SessionTTL
	if ttl == 0 {
		ttl = 7 * 24 * time.Hour
	}
	return models.Session{Token: Token(), ExpiresAt: s.now().Add(ttl), User: u}
}
func (s *Service) Authenticate(ctx context.Context, token string) (models.User, error) {
	if len(token) != 43 {
		return models.User{}, Fail(401, "unauthorized", "Valid session required")
	}
	u, e := s.Repo.SessionUser(ctx, Hash(token), s.now())
	if errors.Is(e, pgx.ErrNoRows) {
		return u, Fail(401, "session_expired", "Session expired or signed out")
	}
	return u, e
}
func (s *Service) Logout(ctx context.Context, token string) error {
	return s.Repo.DeleteSession(ctx, Hash(token))
}
func Discount(n int) int {
	if n >= 100 {
		return 10
	}
	if n >= 70 {
		return 7
	}
	if n >= 50 {
		return 5
	}
	return 3
}
func Loyalty(n int) models.Loyalty {
	l := models.Loyalty{QualifyingOrders: n, DiscountPercent: Discount(n)}
	for _, v := range [][2]int{{50, 5}, {70, 7}, {100, 10}} {
		if n < v[0] {
			a, b := v[0], v[1]
			l.NextThreshold = &a
			l.NextDiscountPercent = &b
			break
		}
	}
	return l
}
func (s *Service) Loyalty(ctx context.Context, u models.User) (models.Loyalty, error) {
	n, e := s.Repo.Receipts(ctx, s.Repo.Pool, u.ID)
	return Loyalty(n), e
}
func (s *Service) Catalog(ctx context.Context, cafe int, all bool) (models.Catalog, error) {
	if cafe == 0 {
		cafe = 1
	}
	return s.Repo.Catalog(ctx, s.Repo.Pool, cafe, all)
}

// Calculate rounds the final discounted beverage subtotal half up to whole KZT.
// The receipt discount is the difference, so subtotal-discount always equals total.
func Calculate(in models.QuoteInput, c models.Catalog, receipts int) (models.Quote, error) {
	q := models.Quote{CafeID: in.CafeID, Items: []models.Item{}, DiscountPercent: Discount(receipts), Currency: "KZT", MoneyUnit: "whole_kzt"}
	active := false
	for _, v := range c.Cafes {
		if v.ID == in.CafeID && v.Active {
			active = true
		}
	}
	if !active {
		return q, Fail(422, "cafe_unavailable", "Choose an active cafe")
	}
	if len(in.Items) < 1 || len(in.Items) > 30 || utf8.RuneCountInString(in.Comment) > 500 {
		return q, Fail(422, "validation", "Cart requires 1–30 items and comment at most 500 characters")
	}
	products := map[int]models.Product{}
	for _, p := range c.Products {
		products[p.ID] = p
	}
	addons := map[string]models.Addon{}
	for _, a := range c.Addons {
		addons[a.ID] = a
	}
	drinkSubtotal := 0
	for _, v := range in.Items {
		p, ok := products[v.ProductID]
		if !ok || !p.Available {
			return q, Fail(409, "product_unavailable", "Product is unavailable at this cafe")
		}
		if v.Quantity < 1 || v.Quantity > 20 || v.Sugar < 0 || v.Sugar > 5 || (v.OrderType != "takeaway" && v.OrderType != "dine_in") || (v.Temperature != "standard" && v.Temperature != "hot" && v.Temperature != "cool") {
			return q, Fail(422, "invalid_options", "Invalid quantity, sugar, order type or temperature")
		}
		item := models.Item{ProductID: p.ID, Name: p.Name, SizeID: v.SizeID, Quantity: v.Quantity, AddonIDs: []string{}, AddonNames: []string{}, Sugar: v.Sugar, OrderType: v.OrderType, Temperature: v.Temperature, IsDrink: p.IsDrink, LoyaltyEligible: p.LoyaltyEligible}
		found := false
		for _, size := range p.Sizes {
			if size.ID == v.SizeID {
				item.SizeLabel = size.Label
				item.UnitPrice = size.Price
				found = true
			}
		}
		if !found {
			return q, Fail(422, "invalid_size", "Size does not belong to product")
		}
		if !p.IsDrink && (len(v.AddonIDs) > 0 || v.Sugar > 0 || v.Temperature != "standard") {
			return q, Fail(422, "invalid_options", "Food cannot have beverage options")
		}
		groups := map[string]int{}
		seen := map[string]bool{}
		if len(v.AddonIDs) > 6 {
			return q, Fail(422, "invalid_addons", "At most two additions from each group")
		}
		for _, id := range v.AddonIDs {
			a, ok := addons[id]
			groups[a.Group]++
			if !ok || seen[id] || groups[a.Group] > 2 {
				return q, Fail(422, "invalid_addons", "Unknown, duplicate or incompatible additions")
			}
			seen[id] = true
			item.AddonIDs = append(item.AddonIDs, id)
			item.AddonNames = append(item.AddonNames, a.Name)
			item.UnitPrice += a.Price
		}
		item.LineTotal = item.UnitPrice * item.Quantity
		q.Subtotal += item.LineTotal
		if item.IsDrink {
			drinkSubtotal += item.LineTotal
		}
		q.Items = append(q.Items, item)
	}
	roundedBeverages := (drinkSubtotal*(100-q.DiscountPercent) + 50) / 100
	q.Discount = drinkSubtotal - roundedBeverages
	q.Total = q.Subtotal - q.Discount
	return q, nil
}
func (s *Service) Quote(ctx context.Context, u models.User, in models.QuoteInput) (models.Quote, error) {
	if u.Role != "customer" {
		return models.Quote{}, Fail(403, "forbidden", "Customer account required")
	}
	c, e := s.Repo.Catalog(ctx, s.Repo.Pool, in.CafeID, false)
	if e != nil {
		return models.Quote{}, e
	}
	n, e := s.Repo.Receipts(ctx, s.Repo.Pool, u.ID)
	if e != nil {
		return models.Quote{}, e
	}
	q, e := Calculate(in, c, n)
	if e != nil {
		return q, e
	}
	q.ID = Token()
	q.ExpiresAt = s.now().Add(5 * time.Minute)
	e = s.Repo.SaveQuote(ctx, u.ID, in, q)
	return q, e
}
func (s *Service) Create(ctx context.Context, u models.User, quote, key string) (models.Order, bool, error) {
	if u.Role != "customer" {
		return models.Order{}, false, Fail(403, "forbidden", "Customer account required")
	}
	if len(key) < 8 || len(key) > 128 || len(quote) != 43 {
		return models.Order{}, false, Fail(422, "validation", "quote_id and Idempotency-Key (8–128 bytes) required")
	}
	tx, e := s.Repo.Begin(ctx)
	if e != nil {
		return models.Order{}, false, e
	}
	defer tx.Rollback(ctx)
	if e = s.Repo.LockKey(ctx, tx, u.ID+":"+key); e != nil {
		return models.Order{}, false, e
	}
	hash := Hash(quote)
	id, prior, e := s.Repo.ExistingOrder(ctx, tx, u.ID, key)
	if e == nil {
		if hash != prior {
			return models.Order{}, false, Fail(409, "idempotency_conflict", "This key belongs to another request")
		}
		o, e := s.Repo.Order(ctx, tx, id, false)
		return o, false, e
	}
	if !errors.Is(e, pgx.ErrNoRows) {
		return models.Order{}, false, e
	}
	// Same quote cannot be purchased twice, even using distinct idempotency keys.
	if e = s.Repo.LockKey(ctx, tx, "quote:"+quote); e != nil {
		return models.Order{}, false, e
	}
	if e = s.Repo.LockKey(ctx, tx, "catalog"); e != nil {
		return models.Order{}, false, e
	}
	// Keep the reviewed tier stable until insertion, including concurrent awards.
	if e = s.Repo.LockUser(ctx, tx, u.ID); e != nil {
		return models.Order{}, false, e
	}
	in, q, e := s.Repo.Quote(ctx, tx, quote, u.ID)
	if e != nil {
		return models.Order{}, false, Missing(e)
	}
	if !s.now().Before(q.ExpiresAt) {
		return models.Order{}, false, Fail(409, "quote_expired", "Please review a fresh quote")
	}
	c, e := s.Repo.Catalog(ctx, tx, in.CafeID, false)
	if e != nil {
		return models.Order{}, false, e
	}
	n, e := s.Repo.Receipts(ctx, tx, u.ID)
	if e != nil {
		return models.Order{}, false, e
	}
	fresh, e := Calculate(in, c, n)
	if e != nil {
		return models.Order{}, false, e
	}
	if fresh.Total != q.Total || fresh.DiscountPercent != q.DiscountPercent || !reflect.DeepEqual(fresh.Items, q.Items) {
		return models.Order{}, false, Fail(409, "price_changed", "Catalog or loyalty changed; review a fresh quote before ordering")
	}
	ttl := s.QRExpiry
	if ttl == 0 {
		ttl = 30 * time.Minute
	}
	id = Token()
	e = s.Repo.InsertOrder(ctx, tx, u.ID, key, hash, quote, Token(), s.now().Add(ttl), in, q, id)
	if repository.Unique(e) {
		return models.Order{}, false, Fail(409, "quote_already_used", "This quote already created an order")
	}
	if e != nil {
		return models.Order{}, false, e
	}
	o, e := s.Repo.Order(ctx, tx, id, false)
	if e != nil {
		return o, false, e
	}
	e = tx.Commit(ctx)
	return o, true, e
}
func canStaff(u models.User, cafe int) bool {
	return u.Role == "admin" || (u.Role == "staff" && u.CafeID != nil && *u.CafeID == cafe)
}
func (s *Service) Order(ctx context.Context, u models.User, id string, staff bool) (models.Order, error) {
	o, e := s.Repo.Order(ctx, s.Repo.Pool, id, false)
	if e != nil {
		return o, Missing(e)
	}
	if staff {
		if !canStaff(u, o.CafeID) {
			return models.Order{}, Fail(403, "forbidden", "Cafe permission required")
		}
		o.QR = nil
	} else if o.UserID != u.ID {
		return models.Order{}, Fail(404, "not_found", "Order not found")
	}
	return o, nil
}
func (s *Service) Orders(ctx context.Context, u models.User, staff bool, cafe int, search string, before int64, limit int) ([]models.Order, error) {
	user := u.ID
	if staff {
		user = ""
		if u.Role == "staff" && u.CafeID != nil {
			cafe = *u.CafeID
		} else if u.Role != "admin" {
			return nil, Fail(403, "forbidden", "Staff role required")
		}
	}
	if limit < 1 || limit > 100 {
		limit = 20
	}
	search = strings.TrimPrefix(strings.TrimSpace(search), "#")
	search = strings.TrimPrefix(search, "mastercoffee:order:")
	ids, e := s.Repo.OrderIDs(ctx, s.Repo.Pool, user, cafe, search, before, limit, staff && search == "")
	if e != nil {
		return nil, e
	}
	orders := []models.Order{}
	for _, id := range ids {
		o, e := s.Repo.Order(ctx, s.Repo.Pool, id, false)
		if e != nil {
			return nil, e
		}
		if staff {
			o.QR = nil
		}
		orders = append(orders, o)
	}
	return orders, nil
}
func validateQR(o models.Order, token string, now time.Time) error {
	if o.QR == nil || o.QR.Token != token {
		return Fail(404, "qr_not_found", "QR not found")
	}
	if o.QR.Used {
		return Fail(409, "qr_used", "QR has already been used")
	}
	if !now.Before(o.QR.ExpiresAt) {
		return Fail(410, "qr_expired", "QR expired; customer must refresh it")
	}
	if o.Status == "cancelled" {
		return Fail(409, "order_cancelled", "Order cancelled")
	}
	return nil
}
func (s *Service) Lookup(ctx context.Context, u models.User, code string) (models.Order, error) {
	code = strings.TrimSpace(code)
	isQR := strings.HasPrefix(code, "mastercoffee:order:") || len(code) == 43
	code = strings.TrimPrefix(code, "mastercoffee:order:")
	if isQR {
		id, e := s.Repo.QRID(ctx, s.Repo.Pool, code)
		if e != nil {
			return models.Order{}, Missing(e)
		}
		o, e := s.Repo.Order(ctx, s.Repo.Pool, id, false)
		if e != nil {
			return o, e
		}
		if !canStaff(u, o.CafeID) {
			return models.Order{}, Fail(403, "forbidden", "QR belongs to another cafe")
		}
		if e = validateQR(o, code, s.now()); e != nil {
			return models.Order{}, e
		}
		o.QR = nil
		return o, nil
	}
	orders, e := s.Orders(ctx, u, true, 0, code, 0, 1)
	if e != nil {
		return models.Order{}, e
	}
	if len(orders) == 0 {
		return models.Order{}, Fail(404, "not_found", "Order number not found")
	}
	return orders[0], nil
}

// Mutations lock the order row; state, QR consumption, audit and ledger commit together.
func (s *Service) Mutate(ctx context.Context, u models.User, id, action, value string) (models.Order, error) {
	tx, e := s.Repo.Begin(ctx)
	if e != nil {
		return models.Order{}, e
	}
	defer tx.Rollback(ctx)
	o, e := s.Repo.Order(ctx, tx, id, true)
	if e != nil {
		return o, Missing(e)
	}
	staff := canStaff(u, o.CafeID)
	switch action {
	case "payment":
		if !staff {
			return models.Order{}, Fail(403, "forbidden", "Cafe staff required")
		}
		if value != "" {
			value = strings.TrimPrefix(value, "mastercoffee:order:")
			if e = validateQR(o, value, s.now()); e != nil {
				return models.Order{}, e
			}
		}
		if o.Status == "cancelled" {
			return models.Order{}, Fail(409, "invalid_transition", "Cancelled orders cannot be paid")
		}
		if o.PaymentStatus == "paid" {
			o.QR = nil
			return o, nil
		}
		if e = s.Repo.SetPayment(ctx, tx, id, s.now()); e != nil {
			return o, e
		}
	case "status":
		if !staff {
			return models.Order{}, Fail(403, "forbidden", "Cafe staff required")
		}
		if value == o.Status {
			o.QR = nil
			return o, nil
		}
		allowed := map[string]string{"created": "accepted", "accepted": "preparing", "preparing": "ready", "ready": "completed"}
		if value == "cancelled" {
			if o.Status != "created" || o.PaymentStatus != "unpaid" {
				return models.Order{}, Fail(409, "invalid_transition", "Only unpaid created orders may be cancelled")
			}
		} else if allowed[o.Status] == "" || allowed[o.Status] != value || o.PaymentStatus != "paid" {
			return models.Order{}, Fail(409, "invalid_transition", "Pay at the register and follow each preparation step")
		}
		if e = s.Repo.SetStatus(ctx, tx, id, value, s.now()); e != nil {
			return o, e
		}
		if value == "completed" {
			for _, i := range o.Items {
				if i.LoyaltyEligible {
					if e = s.Repo.AwardReceipt(ctx, tx, o); e != nil {
						return o, e
					}
					break
				}
			}
		}
	case "cancel":
		if o.UserID != u.ID {
			return models.Order{}, Fail(404, "not_found", "Order not found")
		}
		if o.Status == "cancelled" {
			return o, nil
		}
		if o.Status != "created" || o.PaymentStatus != "unpaid" {
			return models.Order{}, Fail(409, "invalid_transition", "Only unpaid created orders may be cancelled")
		}
		if e = s.Repo.SetStatus(ctx, tx, id, "cancelled", s.now()); e != nil {
			return o, e
		}
	case "qr":
		if o.UserID != u.ID {
			return models.Order{}, Fail(404, "not_found", "Order not found")
		}
		if o.PaymentStatus != "unpaid" || o.Status == "cancelled" {
			return models.Order{}, Fail(409, "invalid_transition", "QR only available for unpaid active orders")
		}
		ttl := s.QRExpiry
		if ttl == 0 {
			ttl = 30 * time.Minute
		}
		if e = s.Repo.SetQR(ctx, tx, id, Token(), s.now().Add(ttl), s.now()); e != nil {
			return o, e
		}
	default:
		return models.Order{}, Fail(422, "validation", "Unknown action")
	}
	event := action
	if action == "status" {
		event += ":" + value
	}
	if e = s.Repo.Event(ctx, tx, id, u.ID, event); e != nil {
		return o, e
	}
	o, e = s.Repo.Order(ctx, tx, id, false)
	if e != nil {
		return o, e
	}
	if e = tx.Commit(ctx); e != nil {
		return o, e
	}
	if action == "payment" || action == "status" {
		o.QR = nil
	}
	return o, nil
}
func (s *Service) SaveProduct(ctx context.Context, id int, p models.Product, createOnly bool) error {
	if p.ID != 0 && p.ID != id {
		return Fail(422, "validation", "Product ID does not match URL")
	}
	p.ID = id
	if p.LoyaltyEligible && !p.IsDrink {
		return Fail(422, "validation", "Only drinks can qualify for beverage loyalty")
	}
	if id < 1 || utf8.RuneCountInString(p.Name) < 1 || utf8.RuneCountInString(p.Name) > 200 || utf8.RuneCountInString(p.Description) > 3000 || utf8.RuneCountInString(p.Category) < 1 || utf8.RuneCountInString(p.Category) > 80 || len(p.ImageKey) > 100 || len(p.Sizes) < 1 || len(p.Sizes) > 10 {
		return Fail(422, "validation", "Invalid product fields")
	}
	seen := map[string]bool{}
	for _, v := range p.Sizes {
		if len(v.ID) < 1 || len(v.ID) > 80 || utf8.RuneCountInString(v.Label) < 1 || utf8.RuneCountInString(v.Label) > 80 || v.Price < 0 || v.Price > 1000000 || seen[v.ID] {
			return Fail(422, "validation", "Sizes require unique IDs and integer KZT prices from 0 to 1000000")
		}
		seen[v.ID] = true
	}
	err := s.Repo.SaveProduct(ctx, p, createOnly)
	if errors.Is(err, repository.ErrProductExists) {
		return Fail(409, "product_exists", "Product already exists")
	}
	return err
}
func (s *Service) SaveStory(ctx context.Context, id string, v models.Story) error {
	u, parseErr := url.Parse(v.ImageURL)
	validURL := parseErr == nil && ((u.Scheme == "https" && u.Host != "" && u.User == nil) || (u.Scheme == "" && u.Host == "" && strings.HasPrefix(u.Path, "/uploads/") && !strings.Contains(strings.TrimPrefix(u.Path, "/uploads/"), "/")))
	if len(id) < 1 || len(id) > 100 || utf8.RuneCountInString(v.Title) < 1 || utf8.RuneCountInString(v.Title) > 200 || len(v.ImageURL) > 2000 || !validURL {
		return Fail(422, "validation", "Story requires title and /uploads/ or HTTPS image URL")
	}
	v.ID = id
	return s.Repo.SaveStory(ctx, v)
}
func (s *Service) SetAvailability(ctx context.Context, cafe, product int, available bool) error {
	e := s.Repo.SetAvailability(ctx, cafe, product, available)
	if repository.ForeignKey(e) {
		return Fail(422, "invalid_reference", "Cafe or product not found")
	}
	return e
}
func (s *Service) DeleteStory(ctx context.Context, id string) error {
	return s.Repo.DeleteStory(ctx, id)
}
