package config

import (
	"os"
	"path/filepath"
	"testing"
)

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

func TestFileSecretAndOriginValidation(t *testing.T) {
	t.Setenv("APP_ENV", "test")
	t.Setenv("AUTO_MIGRATE", "false")
	t.Setenv("SEED_DEMO", "false")
	t.Setenv("DEMO_PASSWORD_FILE", "")
	t.Setenv("DATABASE_URL", "")
	t.Setenv("CORS_ORIGINS", " http://localhost:8081 ")
	file := filepath.Join(t.TempDir(), "database-url")
	if err := os.WriteFile(file, []byte("postgres://coffee@localhost/db?sslmode=disable\n"), 0600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("DATABASE_URL_FILE", file)
	c, err := Load()
	if err != nil || c.DatabaseURL != "postgres://coffee@localhost/db?sslmode=disable" || c.Origins[0] != "http://localhost:8081" {
		t.Fatal("file secret or trimmed origin rejected", err)
	}
	t.Setenv("DATABASE_URL", "postgres://different@localhost/db")
	if _, err = Load(); err == nil {
		t.Fatal("ambiguous secret sources accepted")
	}
	t.Setenv("DATABASE_URL", "")
	for _, origin := range []string{"http://localhost:8081/path", "https://user:password@host.test", "https://host.test?query=1", "https://*.test"} {
		t.Setenv("CORS_ORIGINS", origin)
		if _, err = Load(); err == nil {
			t.Fatal("invalid origin accepted", origin)
		}
	}
}
