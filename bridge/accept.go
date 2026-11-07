package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// config is everything one enrollment needs.
type config struct {
	BaseURL  string
	ClientID string
	Content  acceptContent
	Timeout  time.Duration
	Log      io.Writer
	HTTP     *http.Client
}

// outcome is the result of an accept, whether the server accepted it or not.
type outcome struct {
	Result          int
	Command         string
	Account         string
	Password        string
	AccountID       string
	DeviceName      string
	DataEncodeKey   string
	DynamicPassword string
	PasswordExpire  string
	Powers          string
	Model           string
	Raw             string
}

func (o outcome) accepted() bool { return o.Result == 0 }

// enrol runs the whole exchange: session cookie, accept on the up channel and the
// response on the down channel. A non-zero result is a server verdict, not an error:
// it comes back in the outcome so the caller can record it.
func enrol(ctx context.Context, cfg config) (outcome, error) {
	log := cfg.Log
	if log == nil {
		log = io.Discard
	}
	client := cfg.HTTP
	if client == nil {
		client = &http.Client{Timeout: cfg.Timeout}
	}
	wire := newDuplexClient(cfg.BaseURL, client, log)
	if err := wire.open(ctx); err != nil {
		return outcome{}, err
	}
	envelope := buildAcceptEnvelope(cfg.ClientID, cfg.Content)
	fmt.Fprintf(log, "envelope: %d bytes\n", len(envelope))
	if err := wire.send(ctx, envelope); err != nil {
		return outcome{}, err
	}
	body, err := wire.receive(ctx)
	if err != nil {
		return outcome{}, err
	}
	parsed, err := parseResponse(body)
	if err != nil {
		return outcome{}, fmt.Errorf("unreadable response: %w", err)
	}
	if command := strings.TrimSpace(parsed.Header.Command); command != "" && command != acceptCommand {
		fmt.Fprintf(log, "down: unexpected command %q\n", command)
	}
	return outcome{
		Result:          parsed.result(),
		Command:         parsed.Header.Command,
		Account:         parsed.Content.Account,
		Password:        cfg.Content.Password,
		AccountID:       parsed.Content.ID,
		DeviceName:      parsed.Content.Name,
		DataEncodeKey:   parsed.Content.DataEncodeKey,
		DynamicPassword: parsed.Content.DynamicPassword,
		PasswordExpire:  parsed.Content.PasswordExpired,
		Powers:          parsed.Content.Powers,
		Model:           parsed.Content.Model,
		Raw:             strings.TrimSpace(body),
	}, nil
}
