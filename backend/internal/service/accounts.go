package service

import (
	"context"
	"net/mail"
	"strings"
	"unicode/utf8"

	"github.com/kairagz27/master-coffee-go/internal/models"
	"github.com/kairagz27/master-coffee-go/internal/repository"
	"golang.org/x/crypto/bcrypt"
)

type OperatorInput struct {
	Email  string
	Name   string
	Role   string
	CafeID int
}

// ProvisionOperator is deliberately absent from the public HTTP router. Only
// an operator with the deployment's DB credentials can invoke cmd/accounts.
// Existing customers are never implicitly promoted or overwritten.
func (s *Service) ProvisionOperator(ctx context.Context, in OperatorInput, password string) (models.User, error) {
	in.Email = strings.ToLower(strings.TrimSpace(in.Email))
	in.Name = strings.TrimSpace(in.Name)
	address, err := mail.ParseAddress(in.Email)
	if err != nil || address.Address != in.Email || len(in.Email) > 254 || utf8.RuneCountInString(in.Name) < 1 || utf8.RuneCountInString(in.Name) > 100 || len(password) < 12 || len(password) > 72 {
		return models.User{}, Fail(422, "validation", "Valid email, name (1–100 characters), and password (12–72 bytes) required")
	}
	if (in.Role != "staff" && in.Role != "admin") || (in.Role == "staff" && in.CafeID < 1) || (in.Role == "admin" && in.CafeID != 0) {
		return models.User{}, Fail(422, "validation", "Choose staff with a cafe ID, or admin without a cafe assignment")
	}
	user := models.User{ID: Token(), Email: in.Email, Name: in.Name, Role: in.Role}
	if in.Role == "staff" {
		active, e := s.Repo.CafeActive(ctx, in.CafeID)
		if e != nil {
			return models.User{}, e
		}
		if !active {
			return models.User{}, Fail(422, "cafe_unavailable", "Staff requires an existing active cafe")
		}
		user.CafeID = &in.CafeID
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), 12)
	if err != nil {
		return models.User{}, err
	}
	if err = s.Repo.InsertUser(ctx, user, string(hash)); repository.Unique(err) {
		return models.User{}, Fail(409, "account_exists", "Account exists; provisioning never changes an existing account or role")
	}
	return user, err
}
