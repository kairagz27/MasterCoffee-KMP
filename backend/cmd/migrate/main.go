package main

import (
	"context"
	"log"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/internal/config"
	"github.com/kairagz27/master-coffee-go/migrations"
)

func main() {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	c, err := config.Load()
	if err != nil {
		log.Fatal(err)
	}
	dsn := c.DatabaseURL
	p, e := pgxpool.New(ctx, dsn)
	if e != nil {
		log.Fatal("invalid database configuration")
	}
	defer p.Close()
	if e = migrations.Apply(ctx, p); e != nil {
		log.Fatal(e)
	}
	log.Print("migrations applied")
}
