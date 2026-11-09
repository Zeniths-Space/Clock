# Security

## Reporting a vulnerability

Please report security issues privately through GitHub Security Advisories
("Report a vulnerability" on the repository's Security tab). Do not open a public issue.

## Design

- No permissions.
- The only exported component is the launcher activity, which reads no data from its intent.
- Release signing keys and passwords are never committed. They live in protected CI secrets.
