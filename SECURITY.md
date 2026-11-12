# Security Policy

## Supported versions

Krelzox OS is in early development and has no formal releases yet. Security
fixes are applied only to the latest code on the `main` branch.

| Version | Supported |
|---|---|
| `main` (latest) | ✅ |
| Older commits | ❌ |

## Reporting a vulnerability

**Do not report vulnerabilities in public issues, pull requests or
discussions.**

Report them privately through GitHub's private vulnerability reporting:

1. Go to the repository's **Security** tab.
2. Select **Report a vulnerability**, or open
   <https://github.com/Nach0t/Krelzox-OS/security/advisories/new> directly.
3. Fill in the form and submit it. Only the maintainer can see the report.

If that option is not available, open a public issue that asks for a private
contact channel **without** describing the vulnerability.

### What to include

- A description of the vulnerability and its potential impact.
- The affected component (for example, the launcher, the crash-loop safe mode,
  system metrics or the CI workflow) and the commit or version.
- The device and Android version where you reproduced it.
- Steps to reproduce it, or a minimal proof of concept.
- Any known mitigation or suggested fix.

Remove personal data — IP addresses, account names, device serial numbers —
from logs and screenshots.

## What to expect

The maintainer will acknowledge the report, investigate it and keep you
informed through the private advisory. This is a personal project maintained in
spare time, so response times are best-effort. Once a fix is available, the
advisory may be published, crediting you if you wish.

Please give the maintainer reasonable time to fix the issue before disclosing
it publicly.

## Scope

In scope: the code in this repository — the Krelzox Launcher app, its modules,
and the build and CI configuration.

Out of scope: vulnerabilities in LineageOS, the KonstaKANG builds, Android
itself or third-party apps launched from Krelzox. Report those to their
respective maintainers.
