# Accessibility

Krelzox OS aims to be usable by as many people as possible from the sofa, with
just a TV remote. This document describes what is in place today, what has not
been verified yet, and how to report problems.

## Commitment

- Every screen must be usable with the D-pad alone (arrows, OK and Back), with
  a clearly visible focus that is never lost.
- Text must meet WCAG 2.1 AA contrast (at least 4.5:1) against its background.
- Interactive elements that carry meaning must expose a content description to
  assistive technologies; purely decorative icons are hidden from them.
- Accessibility problems are treated as bugs.

## Current status

**Verified on the reference device** (Raspberry Pi 5, LineageOS 24.0 /
Android TV 17, 1920×1080):

- Full D-pad navigation with hierarchical Back. Automated UI tests
  (`KrelzoxShellFocusTest`) drive the interface with real key events and check
  focus on key screens — see [docs/rendimiento.md](docs/rendimiento.md).
- The user interface is available in English and Spanish.

**Implemented, not yet tested with assistive technology:**

- App cards, the performance panel and top-bar tabs expose content
  descriptions; decorative icons use `contentDescription = null`.
- Theme contrast was measured in
  [ADR-005](docs/decisiones/ADR-005-apariencia-salvapantallas-ajustes.md).

**Known limitations:**

- White text on the primary color of the original **Ion** theme measures
  4.1:1, below the AA target. The Aurora, Amber and Jade themes meet it.
- Krelzox has not been tested with TalkBack or other screen readers.
- Behavior with large font sizes, display zoom and the system's "remove
  animations" setting has not been verified.
- Krelzox has only been tested on the reference device. It is expected to run
  on other Android TV devices with SDK 31+, but this has not been verified.

## Reporting an accessibility problem

Open an issue with the
[Bug report](https://github.com/Nach0t/Krelzox-OS/issues/new?template=bug_report.yml)
template and mention accessibility in the title. Please include:

- The screen and the element involved.
- What you expected and what happened.
- The assistive technology or system setting in use (for example, TalkBack or a
  larger font size), if any.
- The device and Android version.

Contributions that improve accessibility are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md).
