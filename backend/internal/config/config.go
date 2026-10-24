package config

import (
	"errors"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Environment, DatabaseURL, Address, UploadDir, DemoPassword string
	Origins                                                    []string
	AutoMigrate, SeedDemo                                      bool
	SessionTTL, QRExpiry                                       time.Duration
}

func env(key, fallback string) string {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	return v
}
func duration(key string, defaultValue int, unit time.Duration) (time.Duration, error) {
	n, e := strconv.Atoi(env(key, strconv.Itoa(defaultValue)))
	if e != nil || n < 1 || n > 8760 {
		return 0, errors.New(key + " must be an integer from 1 to 8760")
	}
	return time.Duration(n) * unit, nil
}
func Load() (Config, error) {
	c := Config{Environment: env("APP_ENV", "development"), DatabaseURL: os.Getenv("DATABASE_URL"), Address: env("HTTP_ADDR", ":8080"), UploadDir: env("UPLOAD_DIR", "./uploads"), DemoPassword: os.Getenv("DEMO_PASSWORD"), Origins: strings.Split(env("CORS_ORIGINS", "http://localhost:8081"), ",")}
	var e error
	if c.Environment != "development" && c.Environment != "test" && c.Environment != "production" {
		return c, errors.New("APP_ENV must be development, test or production")
	}
	if c.DatabaseURL == "" {
		return c, errors.New("DATABASE_URL is required")
	}
	u, e := url.Parse(c.DatabaseURL)
	if e != nil || u.Host == "" || (u.Scheme != "postgres" && u.Scheme != "postgresql") {
		return c, errors.New("DATABASE_URL must be a PostgreSQL URL")
	}
	c.SeedDemo = env("SEED_DEMO", "false") == "true"
	c.AutoMigrate = env("AUTO_MIGRATE", "false") == "true"
	if c.SeedDemo && (c.Environment != "development" || len(c.DemoPassword) < 12 || len(c.DemoPassword) > 72) {
		return c, errors.New("SEED_DEMO requires APP_ENV=development and DEMO_PASSWORD of 12–72 bytes")
	}
	if c.Environment == "production" {
		if c.AutoMigrate {
			return c, errors.New("production requires explicit cmd/migrate before startup")
		}
		if u.Query().Get("sslmode") != "verify-full" {
			return c, errors.New("production DATABASE_URL requires sslmode=verify-full")
		}
		for _, o := range c.Origins {
			v, e := url.Parse(o)
			if e != nil || v.Scheme != "https" || v.Host == "" || strings.Contains(o, "*") {
				return c, errors.New("production CORS_ORIGINS must contain explicit HTTPS origins")
			}
		}
	}
	if c.SessionTTL, e = duration("SESSION_TTL_HOURS", 168, time.Hour); e != nil {
		return c, e
	}
	if c.QRExpiry, e = duration("QR_TTL_MINUTES", 30, time.Minute); e != nil {
		return c, e
	}
	return c, nil
}
