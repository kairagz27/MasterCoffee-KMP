package main

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os" // <-- Добавили пакет os для переменных окружения

	"github.com/go-chi/chi/v5"
	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/kairagz27/master-coffee-go/internal/handlers"
)

func main() {
	// 1. Подключаемся к базе
	// Пытаемся прочитать DSN из переменной окружения
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		// Если переменной нет (запускаем локально из IDE), используем локалхост
		dsn = "postgres://postgres:password@localhost:5432/coffee_db?sslmode=disable"
	}

	db, err := sql.Open("pgx", dsn)
	if err != nil {
		log.Fatal("Ошибка подключения к БД:", err)
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		log.Fatal("БД не отвечает:", err)
	}
	fmt.Println("Успешное подключение к PostgreSQL!")

	// Создаем экземпляр нашего хэндлера, передавая ему БД
	menuHandler := &handlers.MenuHandler{DB: db}

	// 2. Настраиваем роутер
	r := chi.NewRouter()

	// Теперь маршруты выглядят очень чисто и красиво
	r.Get("/api/menu", menuHandler.GetAll)
	r.Post("/api/menu", menuHandler.Create)

	// 3. Запускаем сервер
	port := ":8080"
	fmt.Println("Сервер запущен на порту", port)
	if err := http.ListenAndServe(port, r); err != nil {
		log.Fatal(err)
	}
}
