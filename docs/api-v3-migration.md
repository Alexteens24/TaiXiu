# TaiXiu API 3.0 migration

TaiXiu 3.0 intentionally changes several timing and mutability guarantees. The deprecated methods remain temporarily for source migration, but they are not behavioral compatibility shims.

## Session settlement

`TaiXiuManager.resultSeason(...)` now starts settlement and returns immediately. It does not guarantee that payout provider calls, journal updates, result events, or the session swap have finished.

Replace it with `TaiXiuManager.resultSeasonAsync(...)` and observe the returned `CompletableFuture<Boolean>`:

```java
TaiXiuManager.resultSeasonAsync(session, dice1, dice2, dice3)
        .whenComplete((settled, error) -> {
            if (error != null || !Boolean.TRUE.equals(settled)) {
                // Settlement did not reach a safe completed state.
                return;
            }
            // Payout journal updates completed successfully.
        });
```

Do not block a Paper or Folia tick/region thread with `join()` or `get()`.

## Historical sessions

`TaiXiuManager.getSessionData(long)` only checks the active-session map and bounded history cache. A `null` result does not prove that the session is absent from SQLite.

Use `TaiXiuManager.getSessionDataAsync(long)` for historical data:

```java
TaiXiuManager.getSessionDataAsync(sessionId).thenAccept(session -> {
    if (session == null) {
        // The session does not exist.
        return;
    }
    SessionSnapshot snapshot = session.snapshot();
});
```

## Events and mutability

`SessionResultEvent` and `SessionSwapEvent` expose immutable `SessionSnapshot` values. Event listeners can inspect a result but can no longer mutate the live game session.

Mutable methods on `ISession`, `TaiXiuManager.setSessionData(...)`, the synchronous settlement wrapper, and the cache-only historical lookup are deprecated. New integrations should treat session objects as read-only and prefer snapshots.

## Economy failure semantics

Settlement may complete with a failed future when a provider operation or journal write cannot be proven safe. `UNKNOWN` means TaiXiu cannot determine whether the external provider changed a balance. Integrations must not automatically retry an `UNKNOWN` operation; an administrator must compare the provider ledger and reconcile it explicitly.
