# TaiXiu documentation

This directory contains the VitePress website published at `https://alexteens24.github.io/TaiXiu/`.

## Local development

Install dependencies and start the development server:

```bash
cd docs
npm install
npm run docs:dev
```

Build the production site with:

```bash
npm run docs:build
```

The generated site is written to `docs/.vitepress/dist/` and is intentionally ignored by Git.

## Structure

- `index.md` — landing page.
- `guide/` — user, operator, and developer documentation.
- `api-v3-migration.md` — migration notes for API consumers.
- `runtime-test-checklist.md` — Paper/Folia release validation.
- `.vitepress/config.mts` — navigation, sidebar, metadata, and GitHub links.
- `.vitepress/components/` — reusable command, permission, and navigation cards.
- `public/` — static assets.

GitHub Actions builds and publishes the website after documentation changes reach `main`.
