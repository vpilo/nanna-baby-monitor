# QR-Pairing Security Model — Design

**Date:** 2026-07-06
**Status:** Design approved, pending implementation plan
**Scope:** New security model for Baby Monitor (Android + Desktop/JVM, future web/iOS/native).

> This document designs the *new* model only. Removal of the existing security
> model (shared relay secret / `RelayHandshake`, current cert handling) is handled
> separately and is out of scope here.

## Goals

- All communications encrypted; suitable for a publicly released project.
- Clients pair to a server by scanning a QR shown by the server (or typing a PIN on desktop).
- The **relay must not be able to read media** — it becomes a blind pipe.
- Portability: use only crypto primitives available natively on JVM, Android, Apple, Web, and native.
- Keep dependency/size footprint small.

## Key decisions

| Area | Decision | Rationale |
|------|----------|-----------|
| Pairing authentication | Ephemeral **ECDH (P-256) + PIN key-confirmation** | Portable primitives; no PAKE library (BouncyCastle J-PAKE would block web/iOS/native). |
| Media encryption | **Uniform app-layer AES-256-GCM** on LAN and relay | Only AEAD native on all targets (WebCrypto lacks ChaCha20); one code path; makes relay blind. |
| Trust | **Mutual, persistent, revocable** | Pair once, reconnect freely; multiple monitors per camera; server can revoke. |
| Pairing path | **LAN only**, initiated from a discovered server | Same-Wi-Fi constraint enforced by requiring mDNS discovery + selection first. |
| Forward secrecy | **Not in MVP** (PSK-only) | Cheap to add later (per-session ephemeral ECDH); noted as future work. |
| Crypto library | `cryptography-kotlin` (whyoleg) | `commonMain` API delegating to each platform's native provider; minimal footprint; targets the full roadmap. |
| QR generation | `qrose` (alexzhirkevich) | Compose Multiplatform; portable; drops into `presentation`. |

## Terminology

- **AEAD** — Authenticated Encryption with Associated Data: encryption that also
  attaches a 16-byte integrity tag, so any tampering is detected (decryption fails).
- **S** — the long-term shared secret established by pairing (a pre-shared key).
- **HKDF** — key-derivation function (HMAC-SHA256 based) that turns `S`/ECDH output
  into clean, purpose-separated keys.
- **Nonce** — number-used-once; unique per message under a given key.

## Identities & long-term state

- **Server certificate:** persistent self-signed keypair + cert generated on first
  launch, stored in a keystore, used for TLS on `SERVICE_PORT`. Its SHA-256
  **fingerprint** is the server's stable pinning anchor.
- **Device ID:** unchanged; keeps driving mDNS discovery and relay rendezvous.
  Pairing binds `deviceId → { certFingerprint, S, name }`.
- **Trust stores (persisted, new `PairingRepository` in `settings`):**
  - **Client** keeps paired **servers**: `deviceId, certFingerprint, name, S`.
  - **Server** keeps a revocable list of paired **clients**: `clientId, name, S, pairedAt`.

## Pairing (one-time, LAN-only)

Initiated only after the client selects a server from the mDNS-discovered list —
selection is the same-Wi-Fi gate.

**Server** — user taps "Pair":
- Generate a random **6-char PIN** (uppercase, ambiguous chars `O/0/I/1` excluded).
- Open a `/pair` WSS endpoint on `SERVICE_PORT` (normal TLS).
- Start a bounded **pairing window** (~3 min) with **attempt lockout** (5 wrong PINs
  → abort, PIN invalidated).
- Display a QR encoding `bm|<pairingProtocolVersion>|<deviceId>|<PIN>|<hostHint>` plus the 
  PIN as text (for desktop clients that read it off the screen).
- In this iteration, the protocol version is `1`.

**Client:**
- From the discovered-server list, select the target server (fixes `deviceId` + address).
- Scan QR (Android: CameraX + barcode) or type the PIN (desktop).
- If a QR was scanned, verify its `deviceId` matches the selected server.
- Connect to `/pair` over TLS (server cert not yet trusted — acceptable).

**Exchange (ECDH + PIN confirmation):**
1. Client and server exchange ephemeral P-256 public keys `A`, `B` →
   `Z = ECDH`, `S = HKDF(Z)`.
2. Transcript `T = A ‖ B ‖ serverCertFingerprint`, where `serverCertFingerprint`
   is the fingerprint of the TLS cert the client's connection actually terminated on.
3. Client → `Mc = HMAC(KDF(PIN), "c" ‖ T)`; server verifies (wrong → attempt counter).
4. Server → `Ms = HMAC(KDF(PIN), "s" ‖ T)`; client verifies.
5. On success both persist the pairing; client pins `serverCertFingerprint`.

Binding `serverCertFingerprint` into `T` defeats a MITM: a MITM terminating TLS with
its own cert yields a fingerprint mismatch and cannot forge `Ms` without the PIN.

**Accepted residual risk:** a MITM *actively present during the pairing window* can
capture `Mc` and offline-crack the 6-char PIN. Mitigated by the short window +
attempt lockout. Deemed acceptable for this app's threat model in exchange for
primitive portability.

## Reconnecting & media encryption (every session, LAN or relay)

Each connection (`/control`, `/audio`, `/video`) independently:

1. **Transport.**
   - *LAN:* discover via mDNS, connect `wss`, **pin-check** the server cert against
     the stored fingerprint.
   - *Relay:* connect through the relay (relay's own TLS); no server-cert pin on this path.
2. **Session handshake (authenticated by `S`).** Exchange fresh random salts + nonces;
   each side sends `HMAC(S, …)` to prove it still holds `S`. Derive per-direction keys
   `K_c2s`, `K_s2c = HKDF(S, salts)`. On the relay path this runs **end-to-end through**
   the relay as opaque frames — the relay cannot forge it (no `S`).
3. **Media frames** sealed with **AES-256-GCM**: wire = `[counter][ciphertext + 16-byte tag]`;
   nonce = per-connection counter (fresh per-session keys make counter-from-0 safe).
   `deviceId` / stream-type bound as associated data to stop a malicious relay
   cross-wiring streams.

**Result:** the relay sees only frame sizes/timing, never content, and cannot tamper
undetected. **No relay code changes required.** The relay's own access authorization
is orthogonal and left to the separate cleanup.

## Crypto module

New package in `network:common` (`commonMain`), one thin dependency on `cryptography-kotlin`:

- ECDH-P256, HKDF, AES-256-GCM, HMAC wrappers
- Pairing-protocol and session-handshake message types
- PIN generation, QR payload encode/decode, cert-fingerprint utility

## UI

- **Server:** "Pair" action → QR (via `qrose`) + PIN + waiting/expiry state; a
  **paired-devices list with revoke**.
- **Client (Android):** QR scanner + pairing progress.
- **Client (Desktop):** discovered-server picker + manual PIN entry + pairing progress.

## Error handling

- Wrong PIN → attempt counter; lockout aborts and invalidates the PIN.
- Pairing window timeout → PIN expires.
- Selected device not on mDNS → "not on this network".
- QR `deviceId` ≠ selected server → reject.
- **Cert fingerprint mismatch on reconnect** → refuse, prompt re-pair.
- Revoked / unknown `S` at session handshake → "no longer paired, re-pair".
- AES-GCM decryption failure (tampered frame) → drop the connection.

## Testing

No tests exist today. The crypto/protocol logic is pure `commonMain` and is a good
place to introduce the first test source set:

- ECDH + PIN-confirmation happy path and wrong-PIN rejection
- MITM cert-fingerprint mismatch rejection
- HKDF / AES-GCM round-trip and tamper-detection
- PIN generation charset, QR payload encode/decode round-trip
- Session handshake key agreement
- Revocation (unknown `S`) rejection

## Future work

- **Forward secrecy:** per-media-session ephemeral ECDH authenticated by `S`, so a
  leaked stored `S` cannot decrypt recorded past sessions.
- Extend QR scanning / crypto actuals to web, iOS, and native targets.
