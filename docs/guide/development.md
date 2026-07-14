# Development

## Prerequisites

- JDK 21.
- Git.
- Node.js 22 only when editing this documentation website.

## Build the plugin

```bash
git clone https://github.com/Alexteens24/TaiXiu.git
cd TaiXiu
./gradlew clean build
```

Useful tasks:

```bash
./gradlew test
./gradlew check
./gradlew dependencies
```

The multi-project build separates public API, server-independent implementation, and plugin runtime code into `taixiu-api`, `taixiu-implement`, and `taixiu-plugin`.

## Run the documentation

```bash
cd docs
npm install
npm run docs:dev
```

Build the static site with `npm run docs:build`. GitHub Actions publishes `docs/.vitepress/dist` to GitHub Pages after documentation changes reach `main`.

## Before opening a change

1. Keep the change focused and preserve unrelated work.
2. Run `./gradlew clean build`.
3. Build the docs when Markdown, VitePress, or navigation changes.
4. Attach [runtime checklist](/runtime-test-checklist) evidence for scheduler, economy, shutdown, or recovery changes.
5. Explain compatibility changes and update migration notes where necessary.

## Architecture rules

- Route all balance, debit, credit, refund, retry, and recovery operations through the unified currency scheduler.
- Never block Paper/Folia scheduler threads on a future or database operation.
- Persist transaction intent before invoking an external economy provider.
- Treat uncertain external outcomes as `UNKNOWN`, not as permission to retry.
- Keep SQLite as the source of truth; caches may accelerate reads but must not invent state.
- Track in-flight economy work through shutdown and close storage only after callbacks are safe.
