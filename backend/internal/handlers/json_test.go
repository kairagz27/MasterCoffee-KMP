package handlers

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestStrictBoundedJSON(t *testing.T) {
	for _, body := range []string{`null`, `[]`, `{"known":"yes","total":1}`, `{"known":"yes"}{}`, `{"known":"` + strings.Repeat("x", 65*1024) + `"}`} {
		t.Run(body[:min(len(body), 32)], func(t *testing.T) {
			req := httptest.NewRequest("POST", "/", strings.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			var out struct {
				Known string `json:"known"`
			}
			if decode(httptest.NewRecorder(), req, &out) == nil {
				t.Fatal("invalid or unbounded JSON accepted")
			}
		})
	}
	req := httptest.NewRequest("POST", "/", strings.NewReader(`{"known":"Қазақша"}`))
	req.Header.Set("Content-Type", "application/json")
	var out struct {
		Known string `json:"known"`
	}
	if e := decode(httptest.NewRecorder(), req, &out); e != nil || out.Known != "Қазақша" {
		t.Fatal("valid Unicode JSON rejected", e)
	}
}
