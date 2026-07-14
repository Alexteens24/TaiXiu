![logo](https://i.imgur.com/CawZvhH.png)

Starting March 8th, 2024, TaiXiu by Thuong Nguyen (Cortez Romeo) transitions to an open-source model under the GNU GPL 3.0 license. If you're a developer, I kindly request that you contribute via pull requests rather than creating numerous forks. Let's ensure updates are accessible to all!

## Description
Tai Xiu is a game in the form of betting. In the game, there are three 6-sided dice numbered from 1-6. The dealer (system) will take the sum of the 03 dice that appear to give a final number to the player.

The player's task is to predict whether the result is Tai or Xiu. Xiu is the sum of 3 dice from 3-10, and Tai is 11-18. If the guess is correct, the player will win and receive money.

> [!WARNING]
> Disclaimer: This is a gambling plugin and it will have features to help players bet and win money. Because of the gambling nature, I will not be responsible for gambling terms and regulations related to your use of this plugin.

## Main features
- Automatically updating files if there is a new update.
- Configable messages, gui, etc..
- Supporting API.
- Supporting GUI
- Supporting Hex Color
- Supporting BossBar
- Supporting Floodgate (GeyserMC)
- Easily managing plugin database
- Crash-aware economy transaction journal and SQLite session storage

## System requirements
TaiXiu 3.0 requires **Java 21** and **Paper or Folia 1.21.4+**. The plugin now uses the public Paper API and has no version-specific NMS dependency.

Build from source with the bundled Gradle Wrapper: `./gradlew clean build`. The distributable plugin is written to `taixiu-plugin/build/libs/TaiXiu-3.0.0.jar`.

Dependencies and GitHub Actions are monitored weekly by Dependabot via `.github/dependabot.yml`. Use the wrapper instead of a system Gradle installation so builds use the pinned, verified Gradle version.

## Plugin requirements
- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- [VaultUnlocked](https://www.spigotmc.org/resources/vaultunlocked.117277/) when running Folia
- One economy plugin
-- Economy plugins: [iConomy](http://dev.bukkit.org/server-mods/iconomy) 4,5,6, [BOSEconomy](http://dev.bukkit.org/server-mods/boseconomy) 6 & 7, EssentialsEcon, 3Co, [MultiCurrency](http://dev.bukkit.org/server-mods/multicurrency), [MineConomy](http://dev.bukkit.org/server-mods/mineconomy), [eWallet](http://dev.bukkit.org/server-mods/ewallet), [EconXP](http://dev.bukkit.org/server-mods/econxp/), [CurrencyCore](http://dev.bukkit.org/server-mods/currency/), [CraftConomy](http://dev.bukkit.org/server-mods/craftconomy/), AEco, [Gringotts](http://dev.bukkit.org/server-mods/gringotts/), [BetterEconomy](https://www.spigotmc.org/resources/bettereconomy.96690/)
> [!CAUTION]
> On Folia, the selected Vault implementation and economy provider must also support Folia.

## Storage and migration

Sessions, bets, payouts and the economy transaction journal are stored in one SQLite file, `plugins/TaiXiu/taixiu.db`. SQLite WAL mode is enabled and important economy transitions are committed immediately.

On the first 3.0 startup, if the database is empty, TaiXiu imports only the latest unfinished session from the old `session/*.yml` storage. Completed YAML history is intentionally not imported. The old folder is moved to a timestamped `session-legacy-*` archive so it can be inspected or backed up manually.

History retention is configured at `database.retention`: `ALL` keeps every completed session, `DAYS` keeps a time window, and `COUNT` keeps the newest configured number of completed sessions. Sessions with pending payouts are never removed.

Changing `database.file` requires a server restart. Other validated settings, including retention, Discord webhook and Geyser forms, are refreshed by `/taixiuadmin reload`.

### Transaction recovery

TaiXiu records an economy intent before changing a balance. Known provider rejections are marked failed; known applied changes are completed or compensated. If the server or economy provider stops at the exact point where TaiXiu cannot know whether money changed, the entry is marked `UNKNOWN` and is never credited automatically. This avoids silently duplicating money on providers that do not expose idempotency keys.

Inspect and reconcile these entries explicitly:

- `/taixiuadmin health` and `/taixiuadmin health acknowledge`
- `/taixiuadmin transaction list [page] [status]`
- `/taixiuadmin transaction <id> complete confirm [reason]` — assert that the intended balance change already happened.
- `/taixiuadmin transaction <id> fail confirm [reason]` — record a known provider rejection.
- `/taixiuadmin transaction <id> refund confirm [reason]` — credit an ambiguous/applied debit after checking the provider ledger.
- `/taixiuadmin transaction <id> retry confirm [reason]` — retry a payout after checking that it was not already credited.

The game stays health-locked and paused while unresolved transactions exist. `health acknowledge` is an explicit operator override; verify the database and provider ledger before clearing it.

For a live backup, use SQLite's backup API/command instead of copying only `taixiu.db`; otherwise stop the server first and copy the database after shutdown.

## Soft-depend plugins
You might need these plugins to utilize my plugin resources totally.
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
	-   **%taixiu_phien%** - Get the current session number
    -   **%taixiu_timeleft%** - Get current session  time left 
	-   **%taixiu_result_phien_{session_number}%** - Get the result of the session {session_number}
	-   **%taixiu_resultformat_phien_{session_number}%** - Get the format result of the session {session_number} (based on message.yml)
	-   **%taixiu_taiplayers_phien_{session_number}%** - List the players who bet on Tai
	-   **%taixiu_xiuplayers_phien_{session_number}%** - List the players who bet on Xiu
	-   **%taixiu_taiplayers_bet_phien_{session_number}%** - Get the total money of Tai
	-   **%taixiu_xiuplayers_bet_phien_{session_number}%** - Get the total money of Xiu
	-   **%taixiu_totalbet_phien_{session_number}%** - Get the total money of the session

**Attention:** Change **{session_number}** to **current** if you want to get the last session.
- [Floodgate API (GeyserMC)](https://geysermc.org/download#floodgate)
	- This will let you be able to [use Bedrock forms](https://wiki.geysermc.org/geyser/forms/) which I already have set up for this plugin.
- Discord Web Hook
	- Send messages to discord of session's information.
- [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/)
    - Since version 2.3, TaiXiu supports multiple currencies for each playing session. PlayerPointsn now can be used as a second currency for players to bet. 

## Commands & subcommands & permissions
- /taixiu `taixiu.use`
  - The main command for the players to use.
    - toggle
    - luatchoi
    - cuoc
    - thongtin
- /taixiuadmin `taixiu.admin`
  - For the administrators to use to adjust stats or reload plugin.
    - reload
    - changestate
    - settime
    - setcurrency
    - setresult
    - health/suckhoe `[acknowledge|xacnhan]`
    - transaction/giaodich `list|danhsach [page] [status]`
    - transaction/giaodich `<id>` `<complete|hoantat|fail|thatbai|refund|hoantien|retry|thulai>` `confirm|xacnhan [reason]`
- `taixiu.tax.bypass`: Bypass taxes.

## Update history
<details>
<summary>3.0.0</summary>

	- Requires Java 21 and Paper/Folia 1.21.4+.
	- Replaced YAML session files with SQLite, WAL, schema migrations and configurable retention.
	- Added active-session YAML migration and crash recovery journal for debits and payouts.
	- Added conservative UNKNOWN transaction handling and explicit admin reconciliation commands.
	- Reworked economy providers to validate Vault and PlayerPoints transaction results.
	- Replaced FoliaLib with native Paper global, async and entity schedulers.
	- Added immutable API snapshots, a correctly spelled CurrencyType API and cancellable PlayerBetPreEvent.
	- Fixed shared tax mutation, offline PlayerPoints payouts, task startup races, settlement races and recursive result retries.
	- Replaced Maven with Gradle Kotlin DSL and added weekly Dependabot updates.
</details>
<details>
<summary>2.8</summary>

 	- Fixed: An error occurred when trying to setup files while starting the server.
</details>
<details>
<summary>2.7</summary>

 	- Fixed: Console spam caused by certain errors.
	- Fixed: Menu errors on the latest server versions.
	- Fixed: Server may crash or lag when using Discord webhooks.
	- Fixed: Incorrect display of the "invalid-currency" message.
	- Support Added: Folia support.
	- Support Added: Compatibility with the latest server versions.
	- Optimization: Refactored and optimized some parts of the code.
	- Localization: Added English language support.
	- Localization: Translated some parts of the interface to English.
	- Feature Added: PlaceholderAPI %taixiu_timeleft% to get the remaining time of the current session.
	- Permission Added: taixiu.tax.bypass to skip the tax.
</details>
<details>
<summary>2.6</summary>

 	- Supported the newest version of minecraft server
	- Fixed misspelling
	- Removed supporting DiscordSRV and now support Discord Web Hook instead! (https://www.youtube.com/watch?v=G_fRHkHQUNU)
</details>
<details>
<summary>2.5</summary>
	
	- Supported 1.21.3
	- Fixed Bossbar's still enabled even after turning off in config.yml
	- Fixed spam console after turning off Bossbar
	- Fixed player head in TaiXiu GUI turns into Bedrock (mostly happens because server-online is false)
	- Added new placeholders for DiscordSRV
</details>
<details>
<summary>2.4</summary>
	
	- Supported minecraft version 1.21.x
	- Fixed spam console "<taixiutask.java>java.lang.NullPointerException: Cannot invoke "org.bukkit.entity.Player.getName()" because "p" is null"
	- Optimized pom.xml
</details>
<details>
<summary>2.3</summary>
	
	- Taixiu now supports PlayerPoints!
	- New inventory, added "shortItem" to short specific values.
	- Added /taixiuadmin setcurrency
	- Optimized code.
</details>
<details>
<summary>2.2</summary>
	
	- Fixed boss-bar does not work correctly.
	- Fixed session sometime automatically reset when a player toggle notification.
	- Fixed an error occurred when floodgate players use command "/taixiu"
	- Optimized boss-bar
	- Supported DiscordSRV! (100% Configable messages)
</details>
<details>
<summary>2.1</summary>
	
	- Fixed BossSytle is not working as config
</details>
<details>
<summary>2.0</summary>
	
	- Added GPL-3.0 license which should be added early
	- Fixed misspelling
	- Optimized code
	- Fixed an error that makes console spam when opening a GUI if the server version is above 1.16.5
 	- Added sound when the player wins or loses betting
	- Added a message when the player loses betting
	- Updated BossBar
	- Supported Floodgate Forms
</details>
<details>
<summary>1.1.5</summary>
	
	- Fixed an error relating to FilenameUtil caused console to spam while loading plugin
</details>
<details>
<summary>1.1.4</summary>
	
	- Fixed an error that causes money is not given for floodgate player
	- Fixed an error while giving a result
	- Recaculated how tax works
	- Updated PlaceholderAPI
	- Optimized code
</details>

## Contact
[![Discord Server](https://discord.com/api/guilds/1187827789664096267/widget.png?style=banner3)](https://discord.gg/NWbTVddmBM)

## 3rd party libraries
- [JetBrains Java Annotations](https://mvnrepository.com/artifact/org.jetbrains/annotations)
- [ConfigUpdater](https://github.com/tchristofferson/Config-Updater)
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
- [JSON-java](https://github.com/stleary/JSON-java)

# Special Thanks To
[<img src="https://user-images.githubusercontent.com/21148213/121807008-8ffc6700-cc52-11eb-96a7-2f6f260f8fda.png" alt="" width="150">](https://www.jetbrains.com)

Jetbrains supports TaiXiu with their [Open Source Licenses](https://www.jetbrains.com/opensource/).
