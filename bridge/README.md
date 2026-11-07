# Enrolment probe

A small Go probe that proves or disproves the per-installation enrolment of the
QVCloud userauth channel, before the bridge daemon depends on it.

Every installation authenticates as its own user, and that user is created when the
installation accepts an invitation. The Android app already does this from Kotlin. The
bridge daemon is Go and lives in another repository, so this probe repeats the exchange
with the standard library only and records what the server answers.

The contract it implements is the one measured against the real gateway, not the one
read from the smali:

- `docs/protocol.md` section 14: the duplex channel (the POST goes to
  `/auth/nologin;jus_duplex=up`, the answer arrives on the down channel of the same
  `jsessionid`, and a request without the cookie is answered with a plain 404).
- `docs/protocol.md` section 16: the `accept-nologin-share-invitation` request. The
  content fields are the kebab case aliases of the XStream wire (`share-code`,
  `device-id`, `share-device-memo`, `need-create`, `password`, `way-from`), the content
  element goes before the header with its bean class, and the header version is empty.

## What it does

1. Takes a session cookie from the down channel (headers only, the poll is closed right
   away: the gateway refuses to generate a response while a down poll is in flight).
2. Posts the accept envelope with the cookie on the up channel.
3. Reads the queued envelope from the down channel and prints the result code. On
   success it prints the account, the password the request carried, the device, the
   model, the powers and the data encode key.

`need-create` is always `1`: the probe is an installation without a user of its own, so
the server creates one and protects it with the generated password. Keep that password:
it is the login credential of the new account.

## Usage

```bash
cd bridge
go build -o wibox-enrol .            # or: go run . -code ... -device ...

./wibox-enrol -code ABCD1234 -device testdevice01 -v
```

Flags:

| Flag | Meaning |
|---|---|
| `-code` | invitation code (`InviteCode`) of a fresh invitation |
| `-device` | device id (`DeviceId`) of the invitation link |
| `-memo` | alias the shared device is registered under |
| `-client` | installation client id, `003-2021-<hex16>`; a new one is generated |
| `-password` | password of the new account; a new UUID is generated |
| `-base` | gateway base URL, `https://r1-8.qvcloud.net` |
| `-timeout` | overall exchange budget, 120 s |
| `-ca` | PEM file with the gateway CA |
| `-insecure` | skip TLS verification, diagnostic only |
| `-v` | print the request envelope and the raw response |

Exit codes: `0` accepted, `1` the server rejected the accept (the code is printed),
`2` the exchange failed (transport, timeout, gateway without cookie).

## TLS

The gateway the stock app uses presents a self-signed certificate without a common
name. Its CA is not in this repository on purpose (it belongs to the vendor): pass it
with `-ca /path/to/ca.pem`, which verifies the chain and skips the host name check, or
use `-insecure` for a throwaway run. With no flag the probe uses the system roots and
the live gateway will fail verification.

## What is verified and what is not

Verified offline, in the tests: the request envelope is byte for byte the captured
shape, the content fields are the kebab case aliases, the session cookie is reused
across the up post and the down that carries the answer, the answer can also arrive in
the POST body, a server verdict is reported with its code, and a gateway that never
answers times out instead of hanging.

Not verified: a live enrol from this probe. It needs a fresh invitation code (a used
one is answered with `100151007`) and the device id from the link the stock app prints
on the "New user" screen. Issue #18 keeps the exact result of that run.

## Tests

```bash
cd bridge
go build ./...
go test -race ./...
```

The fake gateway in `accept_test.go` implements the same order as the real one, so the
duplex handling is checked without touching the network.
