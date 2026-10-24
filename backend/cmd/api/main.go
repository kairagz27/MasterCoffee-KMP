package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/internal/config"
	"github.com/kairagz27/master-coffee-go/internal/handlers"
	"github.com/kairagz27/master-coffee-go/internal/repository"
	"github.com/kairagz27/master-coffee-go/internal/seed"
	"github.com/kairagz27/master-coffee-go/internal/service"
	"github.com/kairagz27/master-coffee-go/migrations"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(log)
	if err := run(log); err != nil {
		log.Error("API stopped", "error", err.Error())
		os.Exit(1)
	}
}
func run(log *slog.Logger) error {
	c, err := config.Load()
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	startup, cancel := context.WithTimeout(ctx, 45*time.Second)
	defer cancel()
	pc, err := pgxpool.ParseConfig(c.DatabaseURL)
	if err != nil {
		return errors.New("invalid database configuration")
	}
	pc.MaxConns = 15
	pc.MinConns = 1
	pc.MaxConnIdleTime = 5 * time.Minute
	pc.ConnConfig.ConnectTimeout = 5 * time.Second
	pc.ConnConfig.RuntimeParams["timezone"] = "UTC"
	pc.ConnConfig.RuntimeParams["statement_timeout"] = "15000"
	pool, err := pgxpool.NewWithConfig(startup, pc)
	if err != nil {
		return err
	}
	defer pool.Close()
	if err = pool.Ping(startup); err != nil {
		return errors.New("database is not ready")
	}
	if c.AutoMigrate {
		if err = migrations.Apply(startup, pool); err != nil {
			return err
		}
	}
	if err = seed.Apply(startup, pool, c.SeedDemo, c.DemoPassword); err != nil {
		return err
	}
	svc := &service.Service{Repo: &repository.Repository{Pool: pool}, SessionTTL: c.SessionTTL, QRExpiry: c.QRExpiry}
	h := &handlers.Handler{Service: svc, Origins: c.Origins, UploadDir: c.UploadDir, Logger: log}
	server := &http.Server{Addr: c.Address, Handler: h.Router(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 15 * time.Second, WriteTimeout: 25 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 * 1024}
	result := make(chan error, 1)
	go func() { result <- server.ListenAndServe() }()
	log.Info("API listening", "address", c.Address, "environment", c.Environment)
	select {
	case err = <-result:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	case <-ctx.Done():
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		return server.Shutdown(shutdown)
	}
}
