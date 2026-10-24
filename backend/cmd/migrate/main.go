package main

import (
	"context"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/migrations"
	"log"
	"os"
	"time"
)

func main() {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		log.Fatal("DATABASE_URL is required")
	}
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
