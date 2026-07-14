# TaiXiu 3.0

[**English**](README.md) | [Tiếng Việt](README_VI.md)

[![Build](https://github.com/Alexteens24/TaiXiu/actions/workflows/build.yml/badge.svg)](https://github.com/Alexteens24/TaiXiu/actions/workflows/build.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper 1.21.4+](https://img.shields.io/badge/Paper-1.21.4%2B-blue.svg)](https://papermc.io/)
[![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-green.svg)](LICENSE)

A modernized Tai Xiu plugin for Paper and Folia, focused on safe economy transactions, SQLite persistence, and crash recovery.

This fork is maintained at [Alexteens24/TaiXiu](https://github.com/Alexteens24/TaiXiu) and is based on the original [CortezRomeo/TaiXiu](https://github.com/CortezRomeo/TaiXiu) project.

> [!IMPORTANT]
> Version 3.0 is currently undergoing pre-release validation. Builds and unit tests pass, but the real-provider Paper/Folia checklist must be completed before production use.

> [!WARNING]
> This plugin includes wagering with in-game money or points. Server operators are responsible for all applicable platform rules, terms, and local laws.

## Game rules

The system rolls three six-sided dice:

- A total from `4–10` is Xiu (Low).
- A total from `11–17` is Tai (High).
- A total of `3` or `18` is special and both sides lose.
- When `bet-settings.disable-special: true`, `3/18` is adjusted to avoid a special result.

A winner receives the original stake plus profit after tax. With `0%` tax, the payout is `2 × stake`.

## Fork highlights

- Java 21 and Paper API 1.21.4+ without version-specific NMS code.
- Native Paper global, async, and player-entity schedulers with one economy execution path for Paper/Folia.
- SQLite WAL storage instead of one YAML file per session.
- Durable debit, payout, and refund transaction journal.
- Health lock that pauses the game when database or provider state is unsafe.
- Shutdown tracking for in-flight economy work, with conservative `UNKNOWN` recovery.
- Bounded LRU history cache while SQLite remains the source of truth.
- Immutable API snapshots, cancellable pre-bet event, and async settlement/history APIs.
- Bounded Discord webhook queue, template caching, and rate-limit handling.
- Gradle Kotlin DSL, dependency locking, JaCoCo, GitHub Actions, and Dependabot.
- English and Vietnamese message packs.

## Requirements

| Component | Requirement |
|---|---|
| Java | 21 |
| Server | Paper 1.21.4+ or a compatible Folia build |
| Economy bridge | Vault; VaultUnlocked on Folia |
| Economy provider | A provider compatible with the selected server and bridge |

Optional integrations:

- PlaceholderAPI.
- PlayerPoints.
- Floodgate/Geyser for Bedrock forms.
- Discord Webhook.

> [!CAUTION]
> `folia-supported: true` allows the plugin to load on Folia, but the Vault bridge and economy provider must also support Folia. Complete the [runtime checklist](docs/runtime-test-checklist.md) with your exact provider before production deployment.

## Build

The repository includes Gradle Wrapper 9.6.1, so a system Gradle installation is not required:

```bash
git clone https://github.com/Alexteens24/TaiXiu.git
cd TaiXiu
./gradlew clean build
```

The distributable artifact is written to:

```text
taixiu-plugin/build/libs/TaiXiu-3.0.0.jar
```

Run checks separately with:

```bash
./gradlew test
./gradlew check
```

## Installation

1. Install Java 21 and a compatible Paper/Folia server.
2. Install Vault and an economy provider; on Folia, use a Folia-compatible bridge and provider.
3. Copy `TaiXiu-3.0.0.jar` into `plugins/`.
4. Start the server once to create the configuration and `plugins/TaiXiu/taixiu.db`.
5. Edit `plugins/TaiXiu/config.yml`, then run `/taixiuadmin reload` or restart.
6. Test debit, payout, restart, and recovery on a temporary server before opening the game to players.

Do not replace the JAR or delete `taixiu.db` while the server is running.

## Sessions and storage

Sessions are stored in `plugins/TaiXiu/taixiu.db`:

- A new database starts at session `0`.
- The next session is created only after settlement, payout, and journal work for the current session complete safely.
- On restart, the plugin loads the greatest ID from `sessions`; changing the `en/vi` locale does not reset session numbering.
- If shutdown occurs during a payout, the current session may remain health-locked until the transaction is inspected.

The main tables are `sessions`, `bets`, `payouts`, and `transaction_journal`. SQLite uses WAL, foreign keys, and schema migrations.

### Upgrading from 2.x

On the first 3.0 startup, if the database is empty, the plugin imports only the latest unfinished YAML session. The old `session/` directory is renamed to `session-legacy-<timestamp>`. Completed YAML history is not imported automatically.

Before upgrading:

1. Stop the server cleanly.
2. Back up all of `plugins/TaiXiu/` and the economy provider data.
3. Replace the JAR and start the server.
4. Inspect migration logs, the active session, and `/taixiuadmin health`.

API 2.x consumers should read the [API 3.0 migration guide](docs/api-v3-migration.md).

### Retention and backups

`database.retention.mode` supports:

- `ALL`: keep every completed session.
- `DAYS`: retain sessions for a time window.
- `COUNT`: retain the newest configured number of sessions.

Sessions with unresolved payouts are not deleted by retention. Use the SQLite backup API/command for live backups; for manual file copies, stop the server and wait for the WAL checkpoint first.

## Transaction safety

TaiXiu writes an intent before calling an economy provider. Important states are:

- `PREPARED`: the intent exists, but the provider outcome is not confirmed.
- `APPLIED`: the provider reported a successful balance change.
- `COMPLETED`: the transaction and plugin data completed.
- `COMPENSATED`: a debit was refunded.
- `FAILED`: the provider definitively rejected the operation.
- `UNKNOWN`: the plugin cannot prove whether money changed.

For `UNKNOWN` or unresolved payouts, the plugin health-locks and pauses instead of crediting again. Providers without idempotency keys prevent absolute exactly-once guarantees across every crash boundary.

Inspect state before reconciliation:

```text
/taixiuadmin health
/taixiuadmin transaction list
```

Use `refund`, `retry`, `complete`, or `fail` only after checking the provider ledger or actual balance.

## Commands

### Players — `taixiu.use`

| Command | Description |
|---|---|
| `/taixiu` | Open the main menu |
| `/taixiu bet <tai|xiu> <amount>` | Place a bet |
| `/taixiu cuoc <tai|xiu> <amount>` | Vietnamese alias for bet |
| `/taixiu info [session]` | View the current or a historical session |
| `/taixiu thongtin [session]` | Vietnamese alias for info |
| `/taixiu rules` or `/taixiu luatchoi` | View the rules |
| `/taixiu toggle` | Toggle the boss bar/notifications |

### Administrators — `taixiu.admin`

| Command | Description |
|---|---|
| `/taixiuadmin reload` | Reload configuration and reloadable integrations |
| `/taixiuadmin changestate` | Pause/resume when the health lock allows it |
| `/taixiuadmin settime <seconds>` | Change the remaining time |
| `/taixiuadmin setcurrency <VAULT|PLAYERPOINTS>` | Change currency before any bet exists |
| `/taixiuadmin setresult <d1> <d2> <d3>` | Force a session result |
| `/taixiuadmin health` or `suckhoe` | Inspect health state |
| `/taixiuadmin health acknowledge` | Explicitly clear the health lock |
| `/taixiuadmin suckhoe xacnhan` | Vietnamese alias for acknowledge |
| `/taixiuadmin transaction list [page] [status]` | List transactions requiring attention |
| `/taixiuadmin giaodich danhsach [page] [status]` | Vietnamese alias for list |
| `/taixiuadmin transaction <id> <action> confirm [reason]` | Reconcile with confirmation and an audit reason |
| `/taixiuadmin giaodich <id> <action> xacnhan [reason]` | Vietnamese alias syntax |

Action aliases:

| English | Vietnamese ASCII alias |
|---|---|
| `complete` | `hoantat` |
| `fail` | `thatbai` |
| `refund` | `hoantien` |
| `retry` | `thulai` |
| `confirm` / `acknowledge` | `xacnhan` |

Additional permission: `taixiu.tax.bypass` bypasses payout tax.

## Placeholders

| Placeholder | Value |
|---|---|
| `%taixiu_phien%` | Current session ID |
| `%taixiu_timeleft%` | Remaining time |
| `%taixiu_result_phien_<session>%` | Session result |
| `%taixiu_resultformat_phien_<session>%` | Locale-formatted result |
| `%taixiu_taiplayers_phien_<session>%` | Tai bettors |
| `%taixiu_xiuplayers_phien_<session>%` | Xiu bettors |
| `%taixiu_taiplayers_bet_phien_<session>%` | Total Tai stake |
| `%taixiu_xiuplayers_bet_phien_<session>%` | Total Xiu stake |
| `%taixiu_totalbet_phien_<session>%` | Total stake on both sides |

Use `current` instead of `<session>` for the active session. Historical placeholders load asynchronously; the first cold request may return the configured `placeholder-history-loading-value`.

## Important configuration

```yaml
locale: en

database:
  file: taixiu.db
  history-cache-size: 256
  journal-retention-days: 90
  shutdown-transaction-timeout-seconds: 10
  retention:
    mode: ALL
    days: 90
    max-sessions: 10000

currency-settings:
  default: VAULT

discord-webhook-settings:
  queue-capacity: 256
  webhookURL: ""
```

Changing `database.file` requires a restart. Retention, Discord, and reloadable integration settings can be refreshed with the admin reload command.

## Testing and support status

CI runs the build, unit tests, and JaCoCo quality gates. Current tests cover dice rules, payout calculation, and SQLite migration/journal/retention behavior.

Economy provider thread errors can only be proven on a real server. Before release, complete the [Paper/Folia runtime test checklist](docs/runtime-test-checklist.md), especially:

- A player disconnecting during debit/payout.
- Shutdown while an entity operation is queued.
- A provider changing money and then throwing.
- SQLite write failures.
- One successful payout followed by one failed payout.
- Restart with `PREPARED`, `APPLIED`, and `UNKNOWN` journal rows.

## Contributing

1. Fork the repository and create a focused branch.
2. Keep each change small and scoped.
3. Run `./gradlew clean build`.
4. Attach runtime-checklist evidence for economy/scheduler changes.
5. Open a draft pull request against this fork.

Dependabot checks Gradle and GitHub Actions dependencies weekly.

## Credits

- Original author and project: [Thuong Nguyen / Cortez Romeo](https://github.com/CortezRomeo).
- 3.0 fork maintenance: [Alexteens24](https://github.com/Alexteens24).
- [ConfigUpdater](https://github.com/tchristofferson/Config-Updater).
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc).
- [JSON-java](https://github.com/stleary/JSON-java).
- [JetBrains Annotations](https://github.com/JetBrains/java-annotations).

## License

Distributed under the [GNU General Public License v3.0](LICENSE). This fork preserves attribution and copyright history from the upstream project.
