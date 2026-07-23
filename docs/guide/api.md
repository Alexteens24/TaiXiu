# API 3.0

TaiXiu exposes its API classes from the `taixiu-api` module. Version 3 favors immutable observations and asynchronous operations where storage or settlement may cross thread boundaries.

## Add the API project

For local development in this repository:

```kotlin
dependencies {
    compileOnly(project(":taixiu-api"))
}
```

External consumers should use a published artifact when one becomes available or build the API module from the same TaiXiu revision they target. Do not shade a second live copy of plugin implementation classes into an addon.

## Session snapshots

`SessionSnapshot` and `BetSnapshot` are immutable values intended for events and integrations. Treat `ISession` mutation methods as legacy API.

```java
TaiXiuManager.getSessionDataAsync(sessionId).thenAccept(session -> {
    if (session == null) {
        return;
    }

    SessionSnapshot snapshot = session.snapshot();
    // Read snapshot data without mutating the live session.
});
```

The synchronous `getSessionData(long)` checks only active state and the bounded history cache. A `null` value does not prove the session is absent from SQLite.

## Settlement

Observe the future returned by `resultSeasonAsync(...)`:

```java
TaiXiuManager.resultSeasonAsync(session, dice1, dice2, dice3)
        .whenComplete((settled, error) -> {
            if (error != null || !Boolean.TRUE.equals(settled)) {
                // Settlement did not reach a safe completed state.
                return;
            }

            // Journal and payout work completed safely.
        });
```

Never call `join()` or `get()` from a Paper tick thread or Folia region/entity thread.

## Events

- `PlayerBetPreEvent` can cancel a bet before it is accepted.
- `PlayerBetEvent` observes an accepted bet.
- `SessionResultEvent` observes an immutable completed result snapshot.
- `SessionSwapEvent` observes immutable session transition data.

Do not perform blocking I/O from an event handler. Provider or database uncertainty can cause settlement futures to fail and health-lock the game; addons must not automatically retry `UNKNOWN` transactions.

Escrow-funded rollover bets pass through `PlayerBetPreEvent` with the complete escrow stake and emit
`PlayerBetEvent` after the atomic escrow-to-bet transition, just like wallet-funded accepted bets.

## Migrating from 2.x

Deprecated methods remain temporarily to support source migration, not to preserve old synchronous behavior. Read the full [API 3.0 migration guide](/api-v3-migration) before updating an addon.
