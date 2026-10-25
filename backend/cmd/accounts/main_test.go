package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestPasswordNeverAcceptedAsCommandArgument(t *testing.T) {
	if err := run([]string{"--email", "staff@example.test", "--name", "Staff", "--role", "staff", "--cafe", "1", "plain-password-value"}); err == nil {
		t.Fatal("plaintext positional password accepted")
	}
}
func TestPasswordFileBoundedBeforeDatabaseAccess(t *testing.T) {
	file := filepath.Join(t.TempDir(), "password")
	if err := os.WriteFile(file, make([]byte, 8192), 0600); err != nil {
		t.Fatal(err)
	}
	if err := run([]string{"--password-file", file}); err == nil {
		t.Fatal("oversized password file accepted")
	}
}
