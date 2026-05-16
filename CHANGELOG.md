# Changelog

All notable changes to Blocker are documented here.

This project follows [Conventional Commits](https://www.conventionalcommits.org/) and [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- App blocking after daily usage quota via AccessibilityService
- Website blocking across all browsers via DNS-level VPN tunnel
- Schedule-based app blocking (time window + day of week)
- Site daily quota tracking (block after N minutes of DNS activity)
- Quick Block focus mode — timed all-app blocking with per-app exceptions
- Block overlay with full-screen card and home redirect on tap
- Friction dialogs (5-second countdown) on rule disable
- Emergency Stop for Quick Block (10-second countdown)
- Site-blocked system notification with per-domain rate limiting
- iOS glass-style UI (light and dark mode)
- Onboarding flow for all required permissions
- Room DB with schema migrations
- Unit tests: DNS blocking, schedule logic, repository CRUD
- Espresso UI tests: navigation, rule management, Quick Block tab
