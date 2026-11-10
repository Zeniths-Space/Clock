# Security

## Reporting a vulnerability

Please report security issues privately through GitHub Security Advisories
("Report a vulnerability" on the repository's Security tab). Do not open a public issue.

## Design

- Permissions only for alarms (notifications, full-screen alarms, exact alarms, boot).
- Exported: the launcher activity (it reads only a boolean "open Alarms" from its intent) and the
  receiver that sets alarms again, which acts only on the system's boot, update and time-change
  broadcasts and reads nothing from them. The ringing screen and the alarm receiver are not exported;
  only the app's own immutable PendingIntents reach them.
- Release signing keys and passwords are never committed. They live in protected CI secrets.

## Release signing certificate

Official Elara Clock releases are signed with this certificate (created 2026-11-10):

- Subject: `CN=Elara Clock, O=Zenith's Space` (RSA 4096, SHA384withRSA, valid until 2054)
- SHA-256: `01:05:36:f5:48:03:cf:6b:94:50:0d:4f:49:65:35:0d:98:98:fe:90:3d:5b:8a:02:7b:eb:21:1b:ac:40:de:f9`

Check a downloaded APK with `apksigner verify --print-certs elara-clock-<version>.apk`. An APK signed
with any other key is not from this project. Android also refuses to update an installed copy with an
APK signed by a different key.
