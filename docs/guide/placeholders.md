# PlaceholderAPI

Install PlaceholderAPI before using the TaiXiu expansion. Placeholder names retain their original Vietnamese `phien` segment for compatibility.

| Placeholder | Value |
|---|---|
| `%taixiu_phien%` | Current session ID |
| `%taixiu_timeleft%` | Remaining time |
| `%taixiu_result_phien_<session>%` | Raw session result |
| `%taixiu_resultformat_phien_<session>%` | Locale-formatted result |
| `%taixiu_taiplayers_phien_<session>%` | Number of Tai bettors |
| `%taixiu_xiuplayers_phien_<session>%` | Number of Xiu bettors |
| `%taixiu_taiplayers_bet_phien_<session>%` | Total Tai stake |
| `%taixiu_xiuplayers_bet_phien_<session>%` | Total Xiu stake |
| `%taixiu_totalbet_phien_<session>%` | Total stake on both sides |

Replace `<session>` with a numeric session ID or `current` for the active session.

```text
%taixiu_resultformat_phien_current%
%taixiu_totalbet_phien_42%
```

## Historical loading

Active data and recently cached history can be returned immediately. An uncached historical lookup loads from SQLite asynchronously, so its first request may return `placeholder-history-loading-value` (default: `...`). A later PlaceholderAPI refresh receives the loaded value.

Increase `database.history-cache-size` only when your display workload repeatedly accesses many different historical sessions; SQLite remains the authoritative store.
