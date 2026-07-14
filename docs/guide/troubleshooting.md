# Troubleshooting

## The session ID returned to 0 or 1

Check which database file the server actually opened. A new or missing `plugins/TaiXiu/taixiu.db` starts at session `0`; changing `locale` alone does not reset it.

- Confirm `database.file` did not change.
- Confirm the server uses the expected working directory and plugin data folder.
- Check startup logs for SQLite or migration errors.
- Do not restore only the main `.db` file from a live WAL database.

## A balance appears rolled back after restart

First restart once cleanly and recheck the actual economy provider balance. Then inspect:

```text
/taixiuadmin health
/taixiuadmin transaction list
```

TaiXiu does not own Vault balances; it journals operations sent to the configured provider. Verify that the same provider and its data loaded on both starts, and compare any unresolved journal with the provider ledger before taking action.

## The plugin is health-locked

The lock means TaiXiu cannot prove that continuing is safe. Look for `PREPARED`, `APPLIED`, or `UNKNOWN` rows and the associated startup error. Do not use `health acknowledge` simply to silence the warning; resolve or explicitly reconcile the underlying operation first.

## Folia reports a thread or synchronous-event error

Record the complete stack trace, server build, Vault bridge, provider name, and provider version. TaiXiu schedules online operations through the player entity scheduler, but the bridge and provider must also obey Folia threading rules. Reproduce the case using the [runtime checklist](/runtime-test-checklist).

## Historical placeholders show `...`

This is the configured loading value for a cold asynchronous SQLite read. Allow the display to refresh. If it never resolves, check logs for database errors and confirm the requested session exists.

## Discord messages are missing

- Confirm `discord-webhook-settings.webhookURL` is non-empty and valid.
- Check the log for HTTP errors or rate limiting.
- Confirm the bounded queue is not saturated during an outage.
- Discord failure does not block the game thread or decide settlement success.

## Information to include in a bug report

- Exact Paper/Folia and Java versions.
- TaiXiu commit/build and clean startup log.
- Vault/VaultUnlocked and economy provider versions.
- Relevant `config.yml` section with secrets removed.
- Steps, expected result, actual result, and journal state.
- Whether the issue survives a clean restart on a temporary server.
