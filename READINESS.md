# Readiness report

What is verified on every push (GitHub Actions, real JDK 25, real containers) and what is not.

## Verified
- Java build and 3,500+ backend tests; frontend typecheck, 6,100+ tests, production build; desktop shell tests.
- A running server with login: anonymous access refused, login and lockout, user creation and forced
  password change, roles, API keys, a real PDF operation, audit log, instant lockout of disabled users.
- SSO against a mock OpenID provider: sign-in, admin mapping, domain allow-list, unverified email, forged callback.
- AI assistant against a mock model server: summarize, ask, long documents in parts, scanned PDF via OCR,
  bad input gives clean 4xx errors, quota.
- Docker: base and app images build from scratch, the container starts, passes the login smoke test.
- Dependencies: the shipped frontend has 0 known vulnerabilities (`npm audit --omit=dev`).

## Not verified (needs you)
- A real Claude / OpenAI / other model: add a key, then run `scripts/check-ai.sh`.
- A real SSO provider (Google, Entra, Keycloak, ...).
- Load and long-running behaviour; Windows/macOS desktop installers (unsigned, never built here).
- Java dependency vulnerabilities: enable Dependabot alerts in the repo settings (Security tab).
- Build-only npm tools (Storybook, Vitest, Puppeteer) still list advisories; they are not shipped.

## Known limits
- Users live in one file: one server, not a cluster. Sessions are in memory: a restart signs everyone out.
- No SAML (use an OIDC bridge). No billing.
- Contact addresses in the docs are placeholders for you to fill in.
