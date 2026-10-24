package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/internal/models"
)

type DB interface {
	Exec(context.Context, string, ...any) (pgconn.CommandTag, error)
	Query(context.Context, string, ...any) (pgx.Rows, error)
	QueryRow(context.Context, string, ...any) pgx.Row
}
type Repository struct{ Pool *pgxpool.Pool }

func (r *Repository) Begin(ctx context.Context) (pgx.Tx, error) {
	return r.Pool.BeginTx(ctx, pgx.TxOptions{})
}
func (r *Repository) User(ctx context.Context, db DB, id string) (models.User, error) {
	var u models.User
	err := db.QueryRow(ctx, "SELECT id,email,name,role,cafe_id FROM users WHERE id=$1", id).Scan(&u.ID, &u.Email, &u.Name, &u.Role, &u.CafeID)
	return u, err
}
func (r *Repository) Credentials(ctx context.Context, email string) (models.User, string, error) {
	var u models.User
	var hash string
	err := r.Pool.QueryRow(ctx, "SELECT id,email,name,role,cafe_id,password_hash FROM users WHERE email=$1", email).Scan(&u.ID, &u.Email, &u.Name, &u.Role, &u.CafeID, &hash)
	return u, hash, err
}
func (r *Repository) InsertUser(ctx context.Context, u models.User, hash string) error {
	_, err := r.Pool.Exec(ctx, "INSERT INTO users(id,email,name,role,cafe_id,password_hash) VALUES($1,$2,$3,$4,$5,$6)", u.ID, u.Email, u.Name, u.Role, u.CafeID, hash)
	return err
}
func (r *Repository) InsertSession(ctx context.Context, hash, user string, expiry time.Time) error {
	_, err := r.Pool.Exec(ctx, "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES($1,$2,$3)", hash, user, expiry)
	return err
}
func (r *Repository) SessionUser(ctx context.Context, hash string, now time.Time) (models.User, error) {
	var id string
	err := r.Pool.QueryRow(ctx, "SELECT user_id FROM sessions WHERE token_hash=$1 AND expires_at>$2", hash, now).Scan(&id)
	if err != nil {
		return models.User{}, err
	}
	return r.User(ctx, r.Pool, id)
}
func (r *Repository) DeleteSession(ctx context.Context, hash string) error {
	_, err := r.Pool.Exec(ctx, "DELETE FROM sessions WHERE token_hash=$1", hash)
	return err
}
func (r *Repository) Receipts(ctx context.Context, db DB, user string) (int, error) {
	var n int
	err := db.QueryRow(ctx, "SELECT qualifying_orders FROM users WHERE id=$1", user).Scan(&n)
	return n, err
}
func (r *Repository) Catalog(ctx context.Context, db DB, cafe int, all bool) (models.Catalog, error) {
	c := models.Catalog{Cafes: []models.Cafe{}, Products: []models.Product{}, Addons: []models.Addon{}, Stories: []models.Story{}}
	rows, err := db.Query(ctx, "SELECT id,name,address,working_hours,phone,image_key,active FROM cafes ORDER BY id")
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var v models.Cafe
		if err = rows.Scan(&v.ID, &v.Name, &v.Address, &v.WorkingHours, &v.Phone, &v.ImageKey, &v.Active); err != nil {
			rows.Close()
			return c, err
		}
		c.Cafes = append(c.Cafes, v)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return c, err
	}
	rows, err = db.Query(ctx, "SELECT p.document,COALESCE(a.available,false) FROM products p LEFT JOIN product_availability a ON a.product_id=p.id AND a.cafe_id=$1 ORDER BY p.id", cafe)
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var v models.Product
		var b []byte
		var available bool
		if err = rows.Scan(&b, &available); err != nil {
			rows.Close()
			return c, err
		}
		if err = json.Unmarshal(b, &v); err != nil {
			rows.Close()
			return c, err
		}
		v.Available = available
		c.Products = append(c.Products, v)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return c, err
	}
	rows, err = db.Query(ctx, "SELECT document FROM addons ORDER BY id")
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var v models.Addon
		var b []byte
		if err = rows.Scan(&b); err != nil {
			rows.Close()
			return c, err
		}
		if err = json.Unmarshal(b, &v); err != nil {
			rows.Close()
			return c, err
		}
		c.Addons = append(c.Addons, v)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return c, err
	}
	rows, err = db.Query(ctx, "SELECT document FROM stories ORDER BY id")
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var v models.Story
		var b []byte
		if err = rows.Scan(&b); err != nil {
			rows.Close()
			return c, err
		}
		if err = json.Unmarshal(b, &v); err != nil {
			rows.Close()
			return c, err
		}
		if all || v.Active {
			c.Stories = append(c.Stories, v)
		}
	}
	err = rows.Err()
	rows.Close()
	return c, err
}
func (r *Repository) SaveQuote(ctx context.Context, user string, in models.QuoteInput, q models.Quote) error {
	a, e := json.Marshal(in)
	if e != nil {
		return e
	}
	b, e := json.Marshal(q)
	if e != nil {
		return e
	}
	_, e = r.Pool.Exec(ctx, "INSERT INTO quotes(id,user_id,cafe_id,request,snapshot,expires_at) VALUES($1,$2,$3,$4,$5,$6)", q.ID, user, in.CafeID, a, b, q.ExpiresAt)
	return e
}
func (r *Repository) Quote(ctx context.Context, db DB, id, user string) (models.QuoteInput, models.Quote, error) {
	var in models.QuoteInput
	var q models.Quote
	var a, b []byte
	var expires time.Time
	err := db.QueryRow(ctx, "SELECT request,snapshot,expires_at FROM quotes WHERE id=$1 AND user_id=$2", id, user).Scan(&a, &b, &expires)
	if err != nil {
		return in, q, err
	}
	if err = json.Unmarshal(a, &in); err != nil {
		return in, q, err
	}
	err = json.Unmarshal(b, &q)
	q.ExpiresAt = expires.UTC()
	return in, q, err
}
func (r *Repository) LockKey(ctx context.Context, db DB, key string) error {
	_, err := db.Exec(ctx, "SELECT pg_advisory_xact_lock(hashtextextended($1,0))", key)
	return err
}
func (r *Repository) LockUser(ctx context.Context, db DB, id string) error {
	var locked string
	return db.QueryRow(ctx, "SELECT id FROM users WHERE id=$1 FOR UPDATE", id).Scan(&locked)
}
func (r *Repository) ExistingOrder(ctx context.Context, db DB, user, key string) (string, string, error) {
	var id, hash string
	err := db.QueryRow(ctx, "SELECT id,request_hash FROM orders WHERE user_id=$1 AND idempotency_key=$2", user, key).Scan(&id, &hash)
	return id, hash, err
}
func (r *Repository) InsertOrder(ctx context.Context, db DB, user, key, hash, quote, qr string, expiry time.Time, in models.QuoteInput, q models.Quote, id string) error {
	_, err := db.Exec(ctx, "INSERT INTO orders(id,user_id,cafe_id,quote_id,idempotency_key,request_hash,subtotal,discount_percent,discount,total,comment,qr_token,qr_expires_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)", id, user, q.CafeID, quote, key, hash, q.Subtotal, q.DiscountPercent, q.Discount, q.Total, in.Comment, qr, expiry)
	if err != nil {
		return err
	}
	for i, item := range q.Items {
		b, e := json.Marshal(item)
		if e != nil {
			return e
		}
		if _, e = db.Exec(ctx, "INSERT INTO order_items(order_id,position,snapshot) VALUES($1,$2,$3)", id, i, b); e != nil {
			return e
		}
	}
	return r.Event(ctx, db, id, user, "created")
}
func (r *Repository) Order(ctx context.Context, db DB, id string, lock bool) (models.Order, error) {
	var o models.Order
	var qr models.QR
	var used *time.Time
	sql := "SELECT id,sequence,user_id,cafe_id,status,payment_status,subtotal,discount_percent,discount,total,comment,qr_token,qr_expires_at,qr_used_at,created_at,updated_at FROM orders WHERE id=$1"
	if lock {
		sql += " FOR UPDATE"
	}
	err := db.QueryRow(ctx, sql, id).Scan(&o.ID, &o.Sequence, &o.UserID, &o.CafeID, &o.Status, &o.PaymentStatus, &o.Subtotal, &o.DiscountPercent, &o.Discount, &o.Total, &o.Comment, &qr.Token, &qr.ExpiresAt, &used, &o.CreatedAt, &o.UpdatedAt)
	if err != nil {
		return o, err
	}
	o.Number = fmt.Sprintf("MC-%06d", o.Sequence)
	o.CreatedAt = o.CreatedAt.UTC()
	o.UpdatedAt = o.UpdatedAt.UTC()
	qr.ExpiresAt = qr.ExpiresAt.UTC()
	o.Currency = "KZT"
	o.MoneyUnit = "whole_kzt"
	qr.Used = used != nil
	qr.Payload = "mastercoffee:order:" + qr.Token
	o.QR = &qr
	o.Items = []models.Item{}
	rows, err := db.Query(ctx, "SELECT snapshot FROM order_items WHERE order_id=$1 ORDER BY position", id)
	if err != nil {
		return o, err
	}
	defer rows.Close()
	for rows.Next() {
		var b []byte
		var i models.Item
		if err = rows.Scan(&b); err != nil {
			return o, err
		}
		if err = json.Unmarshal(b, &i); err != nil {
			return o, err
		}
		o.Items = append(o.Items, i)
	}
	return o, rows.Err()
}
func (r *Repository) OrderIDs(ctx context.Context, db DB, user string, cafe int, search string, before int64, limit int, activeOnly bool) ([]string, error) {
	ids := []string{}
	rows, err := db.Query(ctx, `SELECT id FROM orders WHERE ($1='' OR user_id=$1) AND ($2=0 OR cafe_id=$2) AND ($3='' OR ('MC-'||CASE WHEN length(sequence::text)<6 THEN lpad(sequence::text,6,'0') ELSE sequence::text END)=$3 OR sequence::text=$3 OR qr_token=$3 OR id=$3) AND ($4::bigint=0 OR sequence<$4) AND (NOT $6::boolean OR status NOT IN('completed','cancelled')) ORDER BY sequence DESC LIMIT $5`, user, cafe, search, before, limit, activeOnly)
	if err != nil {
		return ids, err
	}
	defer rows.Close()
	for rows.Next() {
		var id string
		if err = rows.Scan(&id); err != nil {
			return ids, err
		}
		ids = append(ids, id)
	}
	return ids, rows.Err()
}
func (r *Repository) QRID(ctx context.Context, db DB, token string) (string, error) {
	var id string
	err := db.QueryRow(ctx, "SELECT id FROM orders WHERE qr_token=$1", token).Scan(&id)
	return id, err
}
func (r *Repository) SetPayment(ctx context.Context, db DB, id string, now time.Time) error {
	_, err := db.Exec(ctx, "UPDATE orders SET payment_status='paid',qr_used_at=$2,updated_at=$2 WHERE id=$1", id, now)
	return err
}
func (r *Repository) SetStatus(ctx context.Context, db DB, id, status string, now time.Time) error {
	_, err := db.Exec(ctx, "UPDATE orders SET status=$2,updated_at=$3 WHERE id=$1", id, status, now)
	return err
}
func (r *Repository) SetQR(ctx context.Context, db DB, id, token string, expiry, now time.Time) error {
	_, err := db.Exec(ctx, "UPDATE orders SET qr_token=$2,qr_expires_at=$3,updated_at=$4 WHERE id=$1", id, token, expiry, now)
	return err
}
func (r *Repository) Event(ctx context.Context, db DB, id, user, event string) error {
	_, err := db.Exec(ctx, "INSERT INTO order_events(order_id,actor_id,event) VALUES($1,$2,$3)", id, user, event)
	return err
}
func (r *Repository) AwardReceipt(ctx context.Context, db DB, o models.Order) error {
	tag, err := db.Exec(ctx, "INSERT INTO loyalty_ledger(user_id,order_id,receipt_delta) VALUES($1,$2,1) ON CONFLICT(order_id) DO NOTHING", o.UserID, o.ID)
	if err != nil || tag.RowsAffected() == 0 {
		return err
	}
	_, err = db.Exec(ctx, "UPDATE users SET qualifying_orders=qualifying_orders+1 WHERE id=$1", o.UserID)
	return err
}

var ErrProductExists = errors.New("product already exists")

func (r *Repository) SaveProduct(ctx context.Context, p models.Product, createOnly bool) error {
	tx, err := r.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	if err = r.LockKey(ctx, tx, "catalog"); err != nil {
		return err
	}
	b, err := json.Marshal(p)
	if err != nil {
		return err
	}
	if createOnly {
		var exists bool
		if err = tx.QueryRow(ctx, "SELECT EXISTS(SELECT 1 FROM products WHERE id=$1)", p.ID).Scan(&exists); err != nil {
			return err
		}
		if exists {
			return ErrProductExists
		}
	}
	_, err = tx.Exec(ctx, "INSERT INTO products(id,document) VALUES($1,$2) ON CONFLICT(id) DO UPDATE SET document=EXCLUDED.document", p.ID, b)
	if err != nil {
		return err
	}
	_, err = tx.Exec(ctx, "INSERT INTO product_availability(cafe_id,product_id) SELECT id,$1 FROM cafes ON CONFLICT DO NOTHING", p.ID)
	if err != nil {
		return err
	}
	return tx.Commit(ctx)
}
func (r *Repository) SetAvailability(ctx context.Context, cafe, product int, available bool) error {
	tx, err := r.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	if err = r.LockKey(ctx, tx, "catalog"); err != nil {
		return err
	}
	_, err = tx.Exec(ctx, "INSERT INTO product_availability(cafe_id,product_id,available) VALUES($1,$2,$3) ON CONFLICT(cafe_id,product_id) DO UPDATE SET available=EXCLUDED.available", cafe, product, available)
	if err != nil {
		return err
	}
	return tx.Commit(ctx)
}
func (r *Repository) SaveStory(ctx context.Context, s models.Story) error {
	b, e := json.Marshal(s)
	if e != nil {
		return e
	}
	_, e = r.Pool.Exec(ctx, "INSERT INTO stories(id,document) VALUES($1,$2) ON CONFLICT(id) DO UPDATE SET document=EXCLUDED.document", s.ID, b)
	return e
}
func (r *Repository) DeleteStory(ctx context.Context, id string) error {
	_, err := r.Pool.Exec(ctx, "DELETE FROM stories WHERE id=$1", id)
	return err
}
func Unique(err error) bool { var p *pgconn.PgError; return errors.As(err, &p) && p.Code == "23505" }
func ForeignKey(err error) bool {
	var p *pgconn.PgError
	return errors.As(err, &p) && p.Code == "23503"
}
