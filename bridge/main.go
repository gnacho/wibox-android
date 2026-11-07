package main

import (
	"context"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"flag"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	defaultBaseURL = "https://r1-8.qvcloud.net"
	defaultTimeout = 120 * time.Second
	defaultMemo    = "bridge-lab"
)

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, stdout, stderr *os.File) int {
	flags := flag.NewFlagSet("wibox-enrol", flag.ContinueOnError)
	flags.SetOutput(stderr)

	code := flags.String("code", "", "invitation code from the stock app (InviteCode)")
	device := flags.String("device", "", "device id from the invitation link (DeviceId)")
	memo := flags.String("memo", defaultMemo, "alias the shared device is registered under")
	baseURL := flags.String("base", defaultBaseURL, "gateway base url")
	clientID := flags.String("client", "", "installation client id (default: a new 003-2021-<hex16>)")
	password := flags.String("password", "", "account password (default: a new UUID)")
	timeout := flags.Duration("timeout", defaultTimeout, "overall exchange timeout")
	caFile := flags.String("ca", "", "PEM file with the gateway CA (the stock one is self-signed)")
	insecure := flags.Bool("insecure", false, "skip TLS verification (diagnostic only)")
	verbose := flags.Bool("v", false, "print the request envelope and the raw response")
	from := flags.String("from", "QrInvite", "way-from value of the invitation link")

	if err := flags.Parse(args); err != nil {
		return 2
	}
	if *code == "" || *device == "" {
		fmt.Fprintln(stderr, "both -code and -device are required; both come from the invitation link the stock app prints")
		return 2
	}

	log := stdout
	if *verbose {
		fmt.Fprintln(stderr, "verbose: the envelope and the raw response are printed")
	}

	client := &http.Client{Timeout: *timeout}
	tlsConfig, err := buildTLSConfig(*caFile, *insecure)
	if err != nil {
		fmt.Fprintln(stderr, "tls:", err)
		return 2
	}
	if tlsConfig != nil {
		transport := http.DefaultTransport.(*http.Transport).Clone()
		transport.TLSClientConfig = tlsConfig
		client.Transport = transport
	}

	if *clientID == "" {
		generated, err := newClientID()
		if err != nil {
			fmt.Fprintln(stderr, "client id:", err)
			return 2
		}
		*clientID = generated
	}
	if *password == "" {
		generated, err := newUUID()
		if err != nil {
			fmt.Fprintln(stderr, "password:", err)
			return 2
		}
		*password = generated
	}

	fmt.Fprintf(log, "gateway: %s\n", strings.TrimRight(*baseURL, "/"))
	fmt.Fprintf(log, "client: %s\n", *clientID)
	fmt.Fprintf(log, "device: %s\n", *device)
	if *verbose {
		content := acceptContent{
			ShareCode:  *code,
			DeviceID:   *device,
			Memo:       *memo,
			NeedCreate: true,
			Password:   *password,
			WayFrom:    *from,
		}
		fmt.Fprintf(stderr, "--- request ---\n%s\n--- end ---\n", buildAcceptEnvelope(*clientID, content))
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()
	got, err := enrol(ctx, config{
		BaseURL:  *baseURL,
		ClientID: *clientID,
		Content: acceptContent{
			ShareCode:  *code,
			DeviceID:   *device,
			Memo:       *memo,
			NeedCreate: true,
			Password:   *password,
			WayFrom:    *from,
		},
		Timeout: *timeout,
		Log:     log,
		HTTP:    client,
	})
	if err != nil {
		fmt.Fprintln(stderr, "exchange failed:", err)
		return 2
	}
	if *verbose {
		fmt.Fprintf(stderr, "--- response ---\n%s\n--- end ---\n", got.Raw)
	}

	fmt.Fprintf(log, "result: %d\n", got.Result)
	if !got.accepted() {
		fmt.Fprintf(log, "verdict: the server rejected the accept with result %d\n", got.Result)
		return 1
	}
	fmt.Fprintf(log, "account: %s\n", got.Account)
	fmt.Fprintf(log, "account-password: %s\n", got.Password)
	fmt.Fprintf(log, "device-id: %s\n", got.AccountID)
	fmt.Fprintf(log, "device-name: %s\n", got.DeviceName)
	fmt.Fprintf(log, "model: %s\n", got.Model)
	fmt.Fprintf(log, "powers: %s\n", got.Powers)
	fmt.Fprintf(log, "data-encode-key: %s\n", got.DataEncodeKey)
	fmt.Fprintf(log, "password-expired: %s\n", got.PasswordExpire)
	return 0
}

// buildTLSConfig returns nil for the default verification. The gateway the stock app
// uses presents a self-signed certificate without a common name, so a live run needs
// either its CA in PEM (chain verified, host name not checked) or -insecure.
func buildTLSConfig(caFile string, insecure bool) (*tls.Config, error) {
	if caFile == "" && !insecure {
		return nil, nil
	}
	cfg := &tls.Config{MinVersion: tls.VersionTLS12}
	if caFile == "" {
		cfg.InsecureSkipVerify = true
		return cfg, nil
	}
	pem, err := os.ReadFile(caFile)
	if err != nil {
		return nil, err
	}
	pool := x509.NewCertPool()
	if !pool.AppendCertsFromPEM(pem) {
		return nil, fmt.Errorf("%s does not contain a PEM certificate", caFile)
	}
	cfg.RootCAs = pool
	// The vendor certificate has no common name: the chain is verified here, the host
	// name is not.
	cfg.InsecureSkipVerify = true
	cfg.VerifyPeerCertificate = func(rawCerts [][]byte, _ [][]*x509.Certificate) error {
		if len(rawCerts) == 0 {
			return fmt.Errorf("empty certificate chain")
		}
		cert, err := x509.ParseCertificate(rawCerts[0])
		if err != nil {
			return err
		}
		intermediates := x509.NewCertPool()
		for _, raw := range rawCerts[1:] {
			if parsed, err := x509.ParseCertificate(raw); err == nil {
				intermediates.AddCert(parsed)
			}
		}
		_, err = cert.Verify(x509.VerifyOptions{Roots: pool, Intermediates: intermediates})
		return err
	}
	return cfg, nil
}

func newClientID() (string, error) {
	buf := make([]byte, 8)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return "003-2021-" + hex.EncodeToString(buf), nil
}

func newUUID() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	buf[6] = (buf[6] & 0x0f) | 0x40
	buf[8] = (buf[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", buf[0:4], buf[4:6], buf[6:8], buf[8:10], buf[10:16]), nil
}
