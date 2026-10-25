package seed

import (
	"context"
	_ "embed"
	"encoding/json"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/internal/models"
	"golang.org/x/crypto/bcrypt"
)

//go:embed catalog.json
var catalog []byte

// Apply is reproducible and never overwrites operator changes or resets passwords.
func Apply(ctx context.Context, pool *pgxpool.Pool, demo bool, password string) error {
	var c models.Catalog
	if err := json.Unmarshal(catalog, &c); err != nil {
		return err
	}
	tx, err := pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	if _, err = tx.Exec(ctx, "SELECT pg_advisory_xact_lock(86429311)"); err != nil {
		return err
	}
	for _, v := range c.Cafes {
		if _, err = tx.Exec(ctx, "INSERT INTO cafes(id,name,address,working_hours,phone,image_key,active) VALUES($1,$2,$3,$4,$5,$6,$7) ON CONFLICT DO NOTHING", v.ID, v.Name, v.Address, v.WorkingHours, v.Phone, v.ImageKey, v.Active); err != nil {
			return err
		}
	}
	for _, v := range c.Products {
		b, e := json.Marshal(v)
		if e != nil {
			return e
		}
		if _, e = tx.Exec(ctx, "INSERT INTO products(id,document) VALUES($1,$2) ON CONFLICT DO NOTHING", v.ID, b); e != nil {
			return e
		}
	}
	if _, err = tx.Exec(ctx, "INSERT INTO product_availability(cafe_id,product_id) SELECT c.id,p.id FROM cafes c CROSS JOIN products p ON CONFLICT DO NOTHING"); err != nil {
		return err
	}
	addons := []models.Addon{{ID: "syrup-caramel", Name: "Сироп Карамель", Group: "syrup", Price: 0}, {ID: "syrup-salted-caramel", Name: "Сироп Соленая Карамель", Group: "syrup", Price: 0}, {ID: "syrup-vanilla", Name: "Сироп Ваниль", Group: "syrup", Price: 0}, {ID: "essence-amaretto", Name: "Амаретто эссенция", Group: "essence", Price: 60}, {ID: "essence-cherry", Name: "Вишня эссенция", Group: "essence", Price: 60}, {ID: "essence-pear", Name: "Груша эссенция", Group: "essence", Price: 60}, {ID: "milk-almond", Name: "Миндальное молоко", Group: "milk", Price: 0}, {ID: "milk-coconut", Name: "Кокосовое молоко", Group: "milk", Price: 0}, {ID: "milk-hazelnut", Name: "Фундучное молоко", Group: "milk", Price: 0}}
	for _, v := range addons {
		b, e := json.Marshal(v)
		if e != nil {
			return e
		}
		if _, e = tx.Exec(ctx, "INSERT INTO addons(id,document) VALUES($1,$2) ON CONFLICT DO NOTHING", v.ID, b); e != nil {
			return e
		}
	}
	if demo {
		if len(password) < 12 || len(password) > 72 {
			return fmt.Errorf("demo password must contain 12–72 bytes")
		}
		hash, e := bcrypt.GenerateFromPassword([]byte(password), 12)
		if e != nil {
			return e
		}
		for _, role := range []string{"customer", "staff", "admin"} {
			var cafe any
			if role == "staff" {
				cafe = 1
			}
			if _, e = tx.Exec(ctx, "INSERT INTO users(id,email,name,password_hash,role,cafe_id) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(email) DO NOTHING", "demo-"+role, role+"@mastercoffee.local", "Demo "+role, string(hash), role, cafe); e != nil {
				return e
			}
		}
	}
	return tx.Commit(ctx)
}
