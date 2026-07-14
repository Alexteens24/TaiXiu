# Welcome to TaiXiu

TaiXiu is a Paper and Folia minigame where players bet economy money or PlayerPoints on the total of three dice. Version 3 focuses on preserving session and balance state across failures instead of assuming every economy call succeeds cleanly.

::: warning Pre-release validation
TaiXiu 3.0 builds and its unit tests pass, but the real-provider [Paper/Folia checklist](/runtime-test-checklist) must be completed with your exact economy stack before production use.
:::

<CardGrid>
  <DocCard title="Install" icon="🚀" link="/TaiXiu/guide/installation" desc="Requirements, first startup, verification, and Folia provider guidance." />
  <DocCard title="Configure" icon="⚙️" link="/TaiXiu/guide/configuration" desc="Database, currency, betting, UI, Discord, and retention settings." />
  <DocCard title="Commands" icon="⌨️" link="/TaiXiu/guide/commands" desc="Player and administrator commands with Vietnamese aliases." />
  <DocCard title="Storage & recovery" icon="🛡️" link="/TaiXiu/guide/storage-recovery" desc="Session numbering, transaction states, backups, and reconciliation." />
  <DocCard title="Placeholders" icon="🧩" link="/TaiXiu/guide/placeholders" desc="Current and historical PlaceholderAPI values." />
  <DocCard title="API 3.0" icon="🔌" link="/TaiXiu/guide/api" desc="Events, immutable snapshots, and asynchronous integration methods." />
</CardGrid>

## Choose a path

- Server owners should start with [Installation](./installation) and [Configuration](./configuration).
- Staff should learn [Commands](./commands), [Permissions](./permissions), and [Storage & recovery](./storage-recovery).
- Plugin developers should read [API 3.0](./api) and the [migration guide](/api-v3-migration).
- Release testers should work through the [runtime checklist](/runtime-test-checklist).

::: danger Responsible operation
This plugin includes wagering with in-game money or points. Server operators remain responsible for applicable platform rules, terms, and local laws.
:::
