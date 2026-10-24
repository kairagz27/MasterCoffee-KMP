package config

import "testing"

func TestProductionGuards(t *testing.T) {
	t.Setenv("DATABASE_URL", "postgres://user:password@localhost/db?sslmode=disable")
	t.Setenv("APP_ENV", "production")
	t.Setenv("AUTO_MIGRATE", "false")
	t.Setenv("SEED_DEMO", "false")
	t.Setenv("CORS_ORIGINS", "https://coffee.example")
	if _, e := Load(); e == nil {
		t.Fatal("production insecure DB accepted")
	}
	t.Setenv("DATABASE_URL", "postgres://user:password@db.example/db?sslmode=verify-full")
	if _, e := Load(); e != nil {
		t.Fatal(e)
	}
	t.Setenv("SEED_DEMO", "true")
	t.Setenv("DEMO_PASSWORD", "valid-demo-password")
	if _, e := Load(); e == nil {
		t.Fatal("production demo seed accepted")
	}
	t.Setenv("SEED_DEMO", "false")
	t.Setenv("AUTO_MIGRATE", "true")
	if _, e := Load(); e == nil {
		t.Fatal("production auto-migrate accepted")
	}
}
