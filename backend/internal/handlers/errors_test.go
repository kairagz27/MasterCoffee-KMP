package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestPanicAndDeadlineErrorsAreJSONWithoutSecrets(t *testing.T) {
	var logs bytes.Buffer
	h := Handler{Logger: slog.New(slog.NewTextHandler(&logs, nil))}
	panicHandler := h.recoverJSON(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { panic("secret-token-and-password") }))
	w := httptest.NewRecorder()
	panicHandler.ServeHTTP(w, httptest.NewRequest("GET", "/", nil))
	if w.Code != 500 || !strings.HasPrefix(w.Header().Get("Content-Type"), "application/json") {
		t.Fatal("panic response", w.Code, w.Body.String())
	}
	if strings.Contains(logs.String()+w.Body.String(), "secret-token-and-password") {
		t.Fatal("panic value leaked")
	}
	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
	defer cancel()
	deadline := requestTimeout(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { h.fail(w, r, r.Context().Err()) }))
	w = httptest.NewRecorder()
	deadline.ServeHTTP(w, httptest.NewRequest("GET", "/", nil).WithContext(ctx))
	var body struct {
		Error struct {
			Code string `json:"code"`
		} `json:"error"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil || w.Code != 504 || body.Error.Code != "request_timeout" {
		t.Fatal("deadline response", w.Code, w.Body.String(), err)
	}
}
