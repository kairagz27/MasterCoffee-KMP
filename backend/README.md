# Master Coffee API

Go1.27.1 + chi + PostgreSQL17. This backend belongs to the unified repository; use the root README and root Docker Compose configuration.

- API contract: `../docs/openapi.yaml` (OpenAPI3.0.3, JSON syntax is valid YAML).
- Architecture, business rules, configuration, security boundaries: `../docs/backend.md`.
- Run all local services from repository root: `docker compose up --build -d` after copying/configuring `.env.example`.
- Run explicitly against an existing DB: set `DATABASE_URL`, run `go run ./cmd/migrate`, then `go run ./cmd/api`.
- `go test ./...` and `go vet ./...`.
- Set `TEST_DATABASE_URL` to a disposable PostgreSQL test database to execute real integration suites. Tests create and remove only isolated schemas. Without the variable those tests explicitly skip.

The original menu-only backend source/history is retained in Git; its insecure unversioned menu POST and separate Compose setup have been replaced.
