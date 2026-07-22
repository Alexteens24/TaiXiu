# Paper and Folia runtime test checklist

Complete this checklist on temporary servers before marking the 3.0 release ready. Unit tests and MockBukkit-style tests cannot prove the thread rules of a real economy provider.

Record the exact server build, Java version, Vault bridge, economy provider, provider version, database mode, result, and relevant log excerpt for each run.

## Test matrix

- [ ] Paper 1.21.4, Java 21, Vault, and EssentialsX Economy or XConomy.
- [ ] Folia 1.21.4, Java 21, VaultUnlocked, and an economy provider that explicitly supports Folia.
- [ ] Paper 26.1.2, Java 25, Vault, and EssentialsX Economy or XConomy.
- [ ] Folia 26.2, Java 25, VaultUnlocked, and a provider that explicitly supports that Folia build.
- [ ] Repeat the happy-path tests with PlayerPoints when that integration is enabled.

The `folia-supported: true` manifest flag allows the plugin to load for this validation; it must not be treated as release sign-off until the Folia row and failure scenarios below pass.

## Happy paths

- [ ] Start with a new data folder and verify SQLite schema creation.
- [ ] Place bets from two online players and verify debit, journal, session, and message state.
- [ ] Settle with two winners and verify payouts run sequentially and both journals complete.
- [ ] Query active and historical placeholders and open historical session information.
- [ ] Reload configuration and verify Vault, PlayerPoints, Discord, and Floodgate state refresh correctly.
- [ ] Stop normally and verify a clean WAL checkpoint and successful restart.
- [ ] Enable rollover, win, then exercise Tai, Xiu, manual cashout, cutoff cashout, max-depth cashout, and max-payout cashout.
- [ ] Restart with offers in `PENDING_TARGET`, `AVAILABLE`, `CONSUMED`, and `CASHOUT_PENDING` and verify no duplicate credit.
- [ ] Enable insurance and verify the third eligible wallet loss (including a `3/18` result) refunds 20%, respects the 24-hour cap, and excludes escrow bets.
- [ ] Verify max-bet and decimal tax-discount permissions use the highest valid enabled node.

## Thread and lifecycle failures

- [ ] Disconnect a player after the debit intent is written but before the provider operation starts.
- [ ] Disconnect a winner immediately before an entity-scheduled payout.
- [ ] Stop the server while an online-player debit is waiting in the entity scheduler.
- [ ] Stop the server while a payout is waiting or executing.
- [ ] Verify shutdown waits up to `database.shutdown-transaction-timeout-seconds`.
- [ ] Force the shutdown timeout and verify every still-running journal-backed operation is persisted as `UNKNOWN` before SQLite closes.
- [ ] Verify no late callback throws `RejectedExecutionException` or accesses a closed SQLite connection.
- [ ] On Folia, confirm there are no synchronous-event or wrong-region errors such as `BalanceChangeEvent may only be triggered synchronously`.

## Provider and persistence failures

- [ ] Use a test provider that throws before changing money.
- [ ] Use a test provider that changes money and then throws.
- [ ] Make SQLite temporarily unwritable or locked during intent, applied-state, bet-save, and payout-state writes.
- [ ] Settle two winners where the first payout succeeds and the second is rejected.
- [ ] Remove or disable the configured provider and verify the health lock prevents betting/resume.
- [ ] Confirm Discord rate limiting or an unavailable webhook cannot block a tick/region thread.

## Restart recovery states

- [ ] Restart with a debit journal in `PREPARED` and verify it becomes `UNKNOWN` without automatic credit.
- [ ] Restart with a debit journal in `APPLIED` and a persisted bet; verify it becomes `COMPLETED`.
- [ ] Restart with a debit journal in `APPLIED` and no persisted bet; verify compensation uses the unified currency scheduler and is journaled.
- [ ] Restart with payout journals in `PREPARED`, `APPLIED`, `FAILED`, and `UNKNOWN`.
- [ ] Verify unresolved rows keep the game health-locked until explicit reconciliation or operator acknowledgement.
- [ ] Exercise `complete`, `fail`, `refund`, and `retry` reconciliation after checking the provider ledger.

## Evidence required for release sign-off

- [ ] Attach server logs for both matrix rows.
- [ ] Attach the relevant `transaction_journal` rows before and after failure/recovery cases.
- [ ] Record final balances and compare them with expected debits, payouts, and refunds.
- [ ] Confirm there are no thread, region ownership, closed-database, or unhandled future errors.
