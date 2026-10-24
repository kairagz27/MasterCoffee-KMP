package handlers

import (
	"database/sql"
	"encoding/json"
	"net/http"

	"github.com/kairagz27/master-coffee-go/internal/models"
)

// MenuHandler хранит подключение к базе данных,
// чтобы методы могли к ней обращаться
type MenuHandler struct {
	DB *sql.DB
}

// GetAll обрабатывает GET-запросы
func (h *MenuHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	rows, err := h.DB.Query("SELECT id, name, price FROM menu")
	if err != nil {
		http.Error(w, "Ошибка запроса к БД", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var menu []models.Drink
	for rows.Next() {
		var d models.Drink
		if err := rows.Scan(&d.ID, &d.Name, &d.Price); err != nil {
			http.Error(w, "Ошибка чтения данных", http.StatusInternalServerError)
			return
		}
		menu = append(menu, d)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(menu)
}

// Create обрабатывает POST-запросы
func (h *MenuHandler) Create(w http.ResponseWriter, r *http.Request) {
	var d models.Drink
	if err := json.NewDecoder(r.Body).Decode(&d); err != nil {
		http.Error(w, "Неверный формат данных", http.StatusBadRequest)
		return
	}

	query := "INSERT INTO menu (name, price) VALUES ($1, $2) RETURNING id"
	err := h.DB.QueryRow(query, d.Name, d.Price).Scan(&d.ID)
	if err != nil {
		http.Error(w, "Ошибка при сохранении в БД", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(d)
}