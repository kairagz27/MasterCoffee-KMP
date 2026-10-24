package migrations

import (
	"context"
	"crypto/sha256"
	"embed"
	"errors"
	"fmt"
	"github.com/jackc/pgx/v5"
	"sort"

	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed *.sql
var files embed.FS

// Apply serializes migrations across replicas and refuses edited applied migrations.
func Apply(ctx context.Context, pool *pgxpool.Pool) error {
	tx, err := pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	if _, err = tx.Exec(ctx, "SELECT pg_advisory_xact_lock(86429310)"); err != nil {
		return err
	}
	if _, err = tx.Exec(ctx, "CREATE TABLE IF NOT EXISTS schema_migrations(version text PRIMARY KEY, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT now())"); err != nil {
		return err
	}
	entries, err := files.ReadDir(".")
	if err != nil {
		return err
	}
	names := []string{}
	for _, e := range entries {
		if !e.IsDir() {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names)
	for _, name := range names {
		b, e := files.ReadFile(name)
		if e != nil {
			return e
		}
		sum := fmt.Sprintf("%x", sha256.Sum256(b))
		var prior string
		e = tx.QueryRow(ctx, "SELECT checksum FROM schema_migrations WHERE version=$1", name).Scan(&prior)
		if e == nil {
			if prior != sum {
				return fmt.Errorf("applied migration checksum mismatch: %s", name)
			}
			continue
		}
		if !errors.Is(e, pgx.ErrNoRows) {
			return e
		}
		if _, e = tx.Exec(ctx, string(b)); e != nil {
			return fmt.Errorf("migration %s: %w", name, e)
		}
		if _, e = tx.Exec(ctx, "INSERT INTO schema_migrations(version,checksum) VALUES($1,$2)", name, sum); e != nil {
			return e
		}
	}
	return tx.Commit(ctx)
}
