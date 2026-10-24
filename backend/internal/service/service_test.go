package service

import (
	"testing"
	"time"

	"github.com/kairagz27/master-coffee-go/internal/models"
)

func fixture() (models.QuoteInput, models.Catalog) {
	return models.QuoteInput{CafeID: 1, Items: []models.ItemInput{{ProductID: 1, SizeID: "large", Quantity: 2, AddonIDs: []string{"essence"}, OrderType: "takeaway", Temperature: "standard"}}}, models.Catalog{Cafes: []models.Cafe{{ID: 1, Active: true}}, Products: []models.Product{{ID: 1, Name: "Капучино", Available: true, IsDrink: true, LoyaltyEligible: true, Sizes: []models.Size{{ID: "large", Label: "450 мл", Price: 1440}}}, {ID: 2, Name: "Food", Available: true, Sizes: []models.Size{{ID: "one", Label: "1", Price: 101}}}}, Addons: []models.Addon{{ID: "essence", Name: "Вишня", Group: "essence", Price: 60}, {ID: "e2", Group: "essence", Price: 60}, {ID: "e3", Group: "essence", Price: 60}}}
}
func TestDiscountBoundaries(t *testing.T) {
	for _, v := range [][2]int{{0, 3}, {49, 3}, {50, 5}, {69, 5}, {70, 7}, {99, 7}, {100, 10}, {101, 10}} {
		if actual := Discount(v[0]); actual != v[1] {
			t.Errorf("Discount(%d)=%d want%d", v[0], actual, v[1])
		}
	}
	if Loyalty(100).NextThreshold != nil {
		t.Fatal("max level must not suggest next level")
	}
}
func TestCalculateAdditionsFoodAndRounding(t *testing.T) {
	in, c := fixture()
	in.Items = append(in.Items, models.ItemInput{ProductID: 2, SizeID: "one", Quantity: 1, OrderType: "dine_in", Temperature: "standard"})
	q, e := Calculate(in, c, 0)
	if e != nil {
		t.Fatal(e)
	}
	if q.Subtotal != 3101 || q.Total != 3011 || q.Discount != 90 {
		t.Fatalf("unexpected quote %+v", q)
	}
	c.Products[0].Sizes[0].Price = 50
	in.Items = in.Items[:1]
	in.Items[0].Quantity = 1
	in.Items[0].AddonIDs = nil
	q, e = Calculate(in, c, 0)
	if e != nil || q.Total != 49 || q.Discount != 1 {
		t.Fatalf("50*.97 rounds48.5 to49: %+v %v", q, e)
	}
}
func TestRejectInvalidCombinations(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*models.QuoteInput, *models.Catalog)
	}{{"unknown size", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].SizeID = "wrong" }}, {"unavailable", func(i *models.QuoteInput, c *models.Catalog) { c.Products[0].Available = false }}, {"inactive cafe", func(i *models.QuoteInput, c *models.Catalog) { c.Cafes[0].Active = false }}, {"zero quantity", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].Quantity = 0 }}, {"large quantity", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].Quantity = 21 }}, {"sugar", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].Sugar = 6 }}, {"temperature", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].Temperature = "boiling" }}, {"order type", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].OrderType = "delivery" }}, {"unknown addon", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].AddonIDs = []string{"fake"} }}, {"duplicate addon", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].AddonIDs = []string{"essence", "essence"} }}, {"third addon in same group", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].AddonIDs = []string{"essence", "e2", "e3"} }}, {"food addons", func(i *models.QuoteInput, c *models.Catalog) { i.Items[0].ProductID = 2; i.Items[0].SizeID = "one" }}}
	for _, v := range tests {
		t.Run(v.name, func(t *testing.T) {
			in, c := fixture()
			v.mutate(&in, &c)
			if _, e := Calculate(in, c, 0); e == nil {
				t.Fatal("invalid cart accepted")
			}
		})
	}
}
func TestQRExpiryUsedAndWrongToken(t *testing.T) {
	now := time.Now()
	o := models.Order{Status: "created", QR: &models.QR{Token: "secret", ExpiresAt: now.Add(time.Minute)}}
	if e := validateQR(o, "secret", now); e != nil {
		t.Fatal(e)
	}
	if validateQR(o, "other", now) == nil {
		t.Fatal("wrong token accepted")
	}
	if validateQR(o, "secret", now.Add(2*time.Minute)) == nil {
		t.Fatal("expired token accepted")
	}
	o.QR.Used = true
	if validateQR(o, "secret", now) == nil {
		t.Fatal("used token accepted")
	}
}
