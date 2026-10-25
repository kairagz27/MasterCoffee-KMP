package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/kairagz27/master-coffee-go/internal/config"
	"github.com/kairagz27/master-coffee-go/internal/repository"
	"github.com/kairagz27/master-coffee-go/internal/service"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
func run(args []string) error {
	flags := flag.NewFlagSet("accounts", flag.ContinueOnError)
	var in service.OperatorInput
	var passwordFile string
	flags.StringVar(&in.Email, "email", "", "New operator email (must not already exist)")
	flags.StringVar(&in.Name, "name", "", "Operator display name")
	flags.StringVar(&in.Role, "role", "", "staff or admin; no default privilege")
	flags.IntVar(&in.CafeID, "cafe", 0, "Required cafe ID for staff; omit for admin")
	flags.StringVar(&passwordFile, "password-file", "", "Path to a private password file; password never appears in command arguments")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if flags.NArg() != 0 || passwordFile == "" {
		return fmt.Errorf("use explicit --email, --name, --role, optional --cafe, and --password-file")
	}
	file, err := os.Open(passwordFile)
	if err != nil {
		return fmt.Errorf("cannot open password file")
	}
	defer file.Close()
	raw, err := io.ReadAll(io.LimitReader(file, 75))
	if err != nil {
		return fmt.Errorf("cannot read password file")
	}
	password := strings.TrimSuffix(strings.TrimSuffix(string(raw), "\n"), "\r")
	if len(raw) > 74 || len(password) < 12 || len(password) > 72 {
		return fmt.Errorf("password file must contain 12–72 bytes, with optional final newline")
	}
	c, err := config.Load()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, c.DatabaseURL)
	if err != nil {
		return fmt.Errorf("invalid database configuration")
	}
	defer pool.Close()
	svc := service.Service{Repo: &repository.Repository{Pool: pool}}
	user, err := svc.ProvisionOperator(ctx, in, password)
	if err != nil {
		var public *service.Error
		if errors.As(err, &public) {
			return public
		}
		return fmt.Errorf("operator creation failed; verify database availability and migrations")
	}
	return json.NewEncoder(os.Stdout).Encode(user)
}
