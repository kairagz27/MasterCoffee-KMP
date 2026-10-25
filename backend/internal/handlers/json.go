package handlers

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"mime"
	"net/http"
	"reflect"
	"strings"
	"unicode/utf8"

	"github.com/kairagz27/master-coffee-go/internal/service"
)

func decode(w http.ResponseWriter, r *http.Request, value any) error {
	mediaType, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || strings.ToLower(mediaType) != "application/json" {
		return service.Fail(415, "unsupported_media_type", "Use application/json")
	}
	r.Body = http.MaxBytesReader(w, r.Body, 64*1024)
	d := json.NewDecoder(r.Body)
	var raw json.RawMessage
	if err = d.Decode(&raw); err != nil {
		return service.Fail(400, "invalid_json", "Invalid JSON or body larger than 64 KiB")
	}
	var trailing any
	if err = d.Decode(&trailing); err != io.EOF {
		return service.Fail(400, "invalid_json", "Exactly one JSON object required")
	}
	raw = bytes.TrimSpace(raw)
	if len(raw) == 0 || raw[0] != '{' || !utf8.Valid(raw) {
		return service.Fail(400, "invalid_json", "UTF-8 JSON object required")
	}
	keys := json.NewDecoder(bytes.NewReader(raw))
	keys.UseNumber()
	if err = exactJSON(keys, reflect.TypeOf(value)); err != nil {
		return service.Fail(400, "invalid_json", "Fields must match the contract exactly; duplicates and null values are not allowed")
	}
	strict := json.NewDecoder(bytes.NewReader(raw))
	strict.DisallowUnknownFields()
	if err = strict.Decode(value); err != nil {
		return service.Fail(400, "invalid_json", "Invalid field types or unknown fields")
	}
	return nil
}

// encoding/json deliberately accepts case-insensitive keys, repeated properties
// and null scalar values. API inputs use the exact, non-null OpenAPI field names.
func exactJSON(d *json.Decoder, t reflect.Type) error {
	for t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	token, err := d.Token()
	if err != nil {
		return err
	}
	if token == nil {
		return errors.New("null input")
	}
	switch token {
	case json.Delim('{'):
		if t.Kind() != reflect.Struct {
			return errors.New("unexpected object")
		}
		fields := make(map[string]reflect.Type, t.NumField())
		for i := 0; i < t.NumField(); i++ {
			field := t.Field(i)
			name := strings.Split(field.Tag.Get("json"), ",")[0]
			if name != "" && name != "-" && field.IsExported() {
				fields[name] = field.Type
			}
		}
		seen := map[string]bool{}
		for d.More() {
			key, err := d.Token()
			if err != nil {
				return err
			}
			name, ok := key.(string)
			child, exists := fields[name]
			if !ok || !exists || seen[name] {
				return errors.New("unknown or duplicate field")
			}
			seen[name] = true
			if err = exactJSON(d, child); err != nil {
				return err
			}
		}
		_, err = d.Token()
	case json.Delim('['):
		if t.Kind() != reflect.Slice {
			return errors.New("unexpected array")
		}
		for d.More() {
			if err = exactJSON(d, t.Elem()); err != nil {
				return err
			}
		}
		_, err = d.Token()
	}
	return err
}
