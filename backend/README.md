# Master Coffee Backend ☕

This is a backend service for a coffee shop management system, originally conceptually built with Firebase but migrated to a robust relational architecture using Go and PostgreSQL.

The project demonstrates building a clean, containerized RESTful API.

## Tech Stack
* **Language:** Go (Golang) 1.27
* **Router:** [go-chi/chi](https://github.com/go-chi/chi) (lightweight and fast HTTP router)
* **Database:** PostgreSQL
* **DB Driver:** [jackc/pgx](https://github.com/jackc/pgx)
* **Infrastructure:** Docker & Docker Compose (Multi-stage build)

## Prerequisites
To run this project, you only need to have **Docker** and **Docker Compose** installed on your machine. You don't even need to install Go locally!

## How to Run 🚀

1. Clone the repository:
   ```bash
   git clone [https://github.com/kairagz27/master-coffee-go.git](https://github.com/kairagz27/master-coffee-go.git)
   cd master-coffee-go
   
2. Build and start the containers in the background:
    ```bash
   docker-compose up -d --build
   
3.The server will be available at http://localhost:8080

## API Endpoints
## Menu
* GET /api/menu - Retrieves the list of all available drinks.
* POST /api/menu - Adds a new drink to the menu.
* Payload example:
    ```bash
  {
  "name": "Latte",
  "price": 1300
    }

## Project Structure
* cmd/api/ — Entry point of the application.
* internal/handlers/ — HTTP handlers containing API logic.
* internal/models/ — Data structures used across the application.
* Dockerfile — Multi-stage instructions to build a lightweight Go image.
* docker-compose.yml — Orchestrates the Go application and PostgreSQL database.