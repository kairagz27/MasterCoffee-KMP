package models

import "time"

type User struct {
	ID            string `json:"id"`
	Email         string `json:"email"`
	Name          string `json:"name"`
	Role          string `json:"role"`
	CafeID        *int   `json:"cafe_id"`
	EmailVerified bool   `json:"email_verified"`
}
type Session struct {
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expires_at"`
	User      User      `json:"user"`
}
type Cafe struct {
	ID           int    `json:"id"`
	Name         string `json:"name"`
	Address      string `json:"address"`
	WorkingHours string `json:"working_hours"`
	Phone        string `json:"phone"`
	ImageKey     string `json:"image_key"`
	Active       bool   `json:"active"`
}
type Size struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Price int    `json:"price"`
}
type Product struct {
	ID              int    `json:"id"`
	Name            string `json:"name"`
	Description     string `json:"description"`
	Category        string `json:"category"`
	ImageKey        string `json:"image_key"`
	IsDrink         bool   `json:"is_drink"`
	LoyaltyEligible bool   `json:"loyalty_eligible"`
	Available       bool   `json:"available"`
	Sizes           []Size `json:"sizes"`
}
type Addon struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Group string `json:"group"`
	Price int    `json:"price"`
}
type Story struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	ImageURL string `json:"image_url"`
	Active   bool   `json:"active"`
}
type Catalog struct {
	Cafes    []Cafe    `json:"cafes"`
	Products []Product `json:"products"`
	Addons   []Addon   `json:"addons"`
	Stories  []Story   `json:"stories"`
}
type ItemInput struct {
	ProductID   int      `json:"product_id"`
	SizeID      string   `json:"size_id"`
	Quantity    int      `json:"quantity"`
	AddonIDs    []string `json:"addon_ids"`
	Sugar       int      `json:"sugar"`
	OrderType   string   `json:"order_type"`
	Temperature string   `json:"temperature"`
}
type QuoteInput struct {
	CafeID  int         `json:"cafe_id"`
	Items   []ItemInput `json:"items"`
	Comment string      `json:"comment"`
}
type Item struct {
	ProductID       int      `json:"product_id"`
	Name            string   `json:"name"`
	SizeID          string   `json:"size_id"`
	SizeLabel       string   `json:"size_label"`
	Quantity        int      `json:"quantity"`
	AddonIDs        []string `json:"addon_ids"`
	AddonNames      []string `json:"addon_names"`
	Sugar           int      `json:"sugar"`
	OrderType       string   `json:"order_type"`
	Temperature     string   `json:"temperature"`
	UnitPrice       int      `json:"unit_price"`
	LineTotal       int      `json:"line_total"`
	IsDrink         bool     `json:"is_drink"`
	LoyaltyEligible bool     `json:"loyalty_eligible"`
}
type Quote struct {
	ID              string    `json:"id"`
	ExpiresAt       time.Time `json:"expires_at"`
	CafeID          int       `json:"cafe_id"`
	Items           []Item    `json:"items"`
	Subtotal        int       `json:"subtotal"`
	DiscountPercent int       `json:"discount_percent"`
	Discount        int       `json:"discount"`
	Total           int       `json:"total"`
	Currency        string    `json:"currency"`
	MoneyUnit       string    `json:"money_unit"`
}
type QR struct {
	Token     string    `json:"token"`
	Payload   string    `json:"payload"`
	ExpiresAt time.Time `json:"expires_at"`
	Used      bool      `json:"used"`
}
type Order struct {
	ID              string    `json:"id"`
	Number          string    `json:"number"`
	Sequence        int64     `json:"sequence"`
	CafeID          int       `json:"cafe_id"`
	UserID          string    `json:"user_id"`
	Status          string    `json:"status"`
	PaymentStatus   string    `json:"payment_status"`
	Subtotal        int       `json:"subtotal"`
	DiscountPercent int       `json:"discount_percent"`
	Discount        int       `json:"discount"`
	Total           int       `json:"total"`
	Currency        string    `json:"currency"`
	MoneyUnit       string    `json:"money_unit"`
	Items           []Item    `json:"items"`
	Comment         string    `json:"comment"`
	CreatedAt       time.Time `json:"created_at"`
	UpdatedAt       time.Time `json:"updated_at"`
	QR              *QR       `json:"qr,omitempty"`
}
type Loyalty struct {
	QualifyingOrders    int  `json:"qualifying_orders"`
	DiscountPercent     int  `json:"discount_percent"`
	NextThreshold       *int `json:"next_threshold"`
	NextDiscountPercent *int `json:"next_discount_percent"`
}
