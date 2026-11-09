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
