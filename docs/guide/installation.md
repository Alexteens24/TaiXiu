# Installation

## Requirements

| Component | Requirement |
|---|---|
| Java | 21 for 1.21.x; 25 for 26.x servers |
| Server | Paper 1.21.4+ or a compatible Folia build |
| Economy bridge | Vault; VaultUnlocked on Folia |
| Economy provider | Compatible with the selected server and bridge |

Optional plugins include PlaceholderAPI, PlayerPoints, Floodgate/Geyser, and a Discord webhook endpoint.

The TaiXiu artifact remains Java 21 bytecode; the higher runtime is imposed by the 26.x server.

::: warning Folia stack
`folia-supported: true` only permits TaiXiu to load. The Vault bridge and economy provider must independently support Folia and should be validated on a temporary server.
:::

## Install

1. Stop the server.
2. Install Vault and a compatible economy provider. Use VaultUnlocked and a Folia-aware provider on Folia.
3. Place `TaiXiu-3.0.0.jar` in the server's `plugins/` directory.
4. Start once to generate `plugins/TaiXiu/config.yml` and `plugins/TaiXiu/taixiu.db`.
5. Stop, adjust the configuration, then start again.
6. Run `/taixiuadmin health` and test a complete debit and payout cycle.

## Verify

The startup log should show TaiXiu enabled without a missing Vault/provider error. Then verify:

- `/taixiu` opens the player menu.
- `/taixiuadmin health` reports a usable state.
- A small bet changes the balance once.
- Settlement pays a winner once.
- A clean restart preserves the session ID and balance.

Do not replace the JAR, delete `taixiu.db`, or manually copy live database files while the server is running.

## Upgrade from 2.x

Back up the entire `plugins/TaiXiu/` directory and economy provider data before replacing the plugin. On the first 3.0 startup with an empty database, TaiXiu imports only the latest unfinished YAML session and renames the old directory to `session-legacy-<timestamp>`.

Completed YAML history is not imported automatically. API consumers should also read the [API migration guide](/api-v3-migration).
