# Configuration

TaiXiu creates `plugins/TaiXiu/config.yml` on first startup. Most gameplay and integration settings can be refreshed with `/taixiuadmin reload`; changing `database.file` requires a full restart.

## General

| Key | Default | Description |
|---|---:|---|
| `config-version` | `5` | Internal configuration schema version. Do not edit manually. |
| `debug` | `false` | Enables verbose diagnostic logging. Use only while investigating a problem. |
| `text-format` | `LEGACY` | Global syntax for configurable UI text: `LEGACY` or `MINIMESSAGE`. |
| `locale` | `en` | Message pack: `en` or `vi`. This does not affect session IDs or stored balances. |
| `placeholder-history-loading-value` | `...` | First value returned while an uncached historical placeholder loads asynchronously. |
| `format-money` | `#,###` | Java `DecimalFormat` pattern used for displayed amounts. |

## Database

```yaml
database:
  file: taixiu.db
  history-cache-size: 256
  journal-retention-days: 90
  shutdown-transaction-timeout-seconds: 10
  retention:
    mode: ALL
    days: 90
    max-sessions: 10000
```

| Key | Description |
|---|---|
| `file` | SQLite filename inside the TaiXiu data folder. Restart after changing it. |
| `history-cache-size` | Maximum historical sessions retained in the in-memory LRU cache. |
| `journal-retention-days` | Age after which terminal transaction audit rows may be pruned. |
| `shutdown-transaction-timeout-seconds` | How long shutdown waits for tracked economy operations before unresolved work is forced to `UNKNOWN`. |
| `retention.mode` | `ALL`, `DAYS`, or `COUNT`. |
| `retention.days` | Completed-session window used by `DAYS`. |
| `retention.max-sessions` | Number of newest completed sessions retained by `COUNT`. |

Sessions with unresolved payouts are protected from normal retention cleanup.

## Session and betting

```yaml
task:
  taiXiuTask:
    time-per-session: 125

bet-settings:
  max-bet: 1000000
  min-bet: 500
  tax: 0
  disable-while-remaining: 15
  disable-special: false

rollover:
  enabled: false
  max-consecutive: 3
  max-payout-multiplier: 10

insurance:
  enabled: false
  losses-required: 3
  refund-percent: 20
  max-refund-per-24-hours: 1000000
```

- `time-per-session` is the countdown duration in seconds.
- `max-bet` and `min-bet` define the accepted stake range.
- `tax` is deducted from winning profit; `0` disables it.
- `disable-while-remaining` closes betting near the end of a session.
- `disable-special` prevents sums `3` and `18` from producing a both-sides-lose result.
- Rollover holds an eligible winner's complete after-tax payout in SQLite until the next betting cutoff. It is disabled by default, has no streak multiplier, and automatically cashes out when a limit is reached.
- Insurance counts consecutive eligible wallet-funded losses, including special results. The default third loss refunds 20% of that bet, bounded by a rolling 24-hour cap; escrow-funded bets never qualify.

## Currency

```yaml
currency-settings:
  default: VAULT
  display-settings:
    VAULT:
      name: '&6Money'
      symbol: '&6$'
    PLAYERPOINTS:
      name: '&bPoints'
      symbol: '&b۞'
```

Available types are `VAULT` and `PLAYERPOINTS`. PlayerPoints must be installed before selecting it. An administrator may change currency only before the session has accepted a bet.

## Optional integrations

```yaml
floodgate-settings:
  enabled: true

discord-webhook-settings:
  queue-capacity: 256
  webhookURL: ""

bStats:
  enabled: true
```

An empty `webhookURL` disables Discord delivery. Webhook work uses a bounded queue so an unavailable endpoint cannot grow memory usage without limit.

## Presentation

The `sound` section controls the win/lose Bukkit sound name, volume, and pitch. The `boss-bar` section controls titles, colors, styles, the settlement display, and its duration. Message text lives in `messages_en.yml` and `messages_vi.yml`; inventory layout lives in `sessioninfoinventory.yml`.

::: tip Formatting
`text-format` applies globally to messages, boss bars, currency display names/symbols, inventory text, and Geyser forms. The bundled files remain Legacy for upgrade compatibility:

```yaml
text-format: LEGACY
# Examples: '&cRed', '&#03c6fcSky'
```

To use MiniMessage, convert all configurable UI strings before changing the mode:

```yaml
text-format: MINIMESSAGE
# Examples: '<red>Red</red>', '<gradient:#03c6fc:#1fc433>Gradient</gradient>'
```

MiniMessage chat supports the standard tags, including click and hover events. Boss bars, inventories, and Geyser forms receive colors and decorations through a legacy-string bridge, so interactive events do not apply there. Keep placeholder names such as `%prefix%` and `%money%` intact when translating text. `/taixiuadmin reload` applies a mode change.
:::

## Reload behavior

Use `/taixiuadmin reload` after editing reloadable values. Prefer a clean restart after database path or dependency changes. Always verify `/taixiuadmin health` before reopening betting.
