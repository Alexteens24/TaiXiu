---
layout: home

hero:
  name: TaiXiu
  text: Safe sessions. Predictable recovery.
  tagline: A modern Tai Xiu plugin for Paper and Folia with SQLite persistence and journaled economy operations.
  image:
    src: /logo.svg
    alt: TaiXiu
  actions:
    - theme: brand
      text: Get started
      link: /guide/installation
    - theme: alt
      text: Read the docs
      link: /guide/
    - theme: alt
      text: View on GitHub
      link: https://github.com/Alexteens24/TaiXiu

features:
  - icon: 🎲
    title: Familiar Tai Xiu gameplay
    details: Three-dice High and Low sessions with configurable limits, tax, timing, sounds, boss bars, and result behavior.
  - icon: 🧾
    title: Durable transaction journal
    details: Debit, payout, and refund intents are recorded with conservative recovery when an external provider result is uncertain.
  - icon: 🗄️
    title: SQLite source of truth
    details: Sessions, bets, payouts, and transaction state live in one WAL-enabled database with retention controls.
  - icon: 🧵
    title: Paper and Folia scheduling
    details: Online economy work follows the player entity scheduler; offline work follows the global scheduler through one execution path.
  - icon: 🌐
    title: Java and Bedrock friendly
    details: Inventory UI for Java players, optional Floodgate forms for Bedrock, and English or Vietnamese messages.
  - icon: 🔌
    title: Integration ready
    details: Vault, PlayerPoints, PlaceholderAPI, Discord webhooks, immutable snapshots, events, and asynchronous API methods.
---
