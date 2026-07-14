# Features

TaiXiu combines a familiar three-dice game with operational safeguards designed for servers where balance changes and persistence cannot be treated as one atomic action.

## Gameplay

- Tai (High), Xiu (Low), and configurable special results.
- Per-session Vault or PlayerPoints currency.
- Configurable minimum/maximum bet, tax, cutoff time, and session duration.
- Inventory menus, boss bars, sounds, and optional Floodgate forms.
- English and Vietnamese message packs.
- Historical session inspection and PlaceholderAPI output.

## Reliability

- SQLite WAL database for sessions, bets, payouts, and journals.
- Transaction intents written before external economy changes.
- Health lock when the plugin cannot prove a safe balance outcome.
- In-flight economy tracking during shutdown.
- Bounded history cache with SQLite as the source of truth.
- Retention policies that preserve sessions with unresolved payouts.
- A single scheduler path for online and offline economy operations.

## Integrations

| Integration | Purpose | Required |
|---|---|---|
| Vault | Economy bridge | Yes |
| VaultUnlocked | Folia-compatible Vault bridge | On Folia |
| Economy provider | Stores actual balances | Yes |
| PlayerPoints | Optional per-session points currency | No |
| PlaceholderAPI | Current and historical placeholders | No |
| Floodgate/Geyser | Bedrock forms | No |
| Discord webhook | Bet and result announcements | No |

## Tooling

The fork targets Java 21 and Paper API 1.21.4+, uses Gradle Kotlin DSL with dependency locking, and runs unit tests plus JaCoCo checks in GitHub Actions. Dependabot monitors Gradle, npm, and GitHub Actions dependencies.
