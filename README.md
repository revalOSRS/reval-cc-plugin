# Reval Clan Plugin

A comprehensive RuneLite plugin for the Reval clan in Old School RuneScape. Features a full-featured sidepanel UI for clan members, tracks player progress, sends real-time event notifications, and provides detailed data collection for Combat Achievements, Collection Log, Achievement Diaries, and more.

## Features

### Raid party deaths

Raid loot and completion events include known per-member death counts in
`party.members[].deaths`. ToB and ToA use the game's named death messages, including
logout penalties, without assigning the team-wide total to an individual. CoX/CM
records the local player's deaths; teammates outside the loaded scene remain unknown.
Tracking runs independently of the **Send player deaths to Discord** setting.

An explicit raid start establishes coverage. Enabling the plugin mid-raid, missing
death messages, reconnecting, hopping, or restarting leaves affected counts unknown,
so absent values must not be interpreted as zero. A completed party snapshot stays
with later outside-chest claims during the same uninterrupted login, without the
ten-minute limit used by the names-only final-boss fallback. Counts reset for each
new raid and do not include deaths after completion.

### In-game clan browser

Send `!reval` or `!reval help` in public, clan or private chat, then click its link and choose **Open Reval board** to open the command guide.
Commands open a resizable native window after choosing that menu option;
the game remains usable while it is open. Collection Log uses boxed subcategories under the main
category tabs. Luck and Deserved use the same tabs and sidebar with standings in the content area;
Dryboard has category autocomplete. Leaderboard statistics are selected with buttons below member
search. The `?` button opens a guide with syntax, examples, and chat-sharing instructions.

| Command | View |
| --- | --- |
| `!reval clog forestry` | Your synced collection log, using your logged-in account |
| `!reval clog forestry Valgusruum` | Another clan account’s collection log |
| `!reval clog clues Phosani55555` | All clue pages, including rare and shared rewards |
| `!reval luck vorkath` | Lowest saved greenlog completion counters first |
| `!reval deserved vorkath` | Highest saved greenlog completion counters first |
| `!reval dryboard vorkath` | Kills since the last recorded unique, including duplicates |
| `!reval leaderboard xp` | Full clan standings for a statistic |
| `!reval leaderboard clues hard` | A specific clue tier; omit the tier for all seven tabs |
| `!reval leaderboard bosses toa` | A specific boss or raid; omit the boss for all bosses |

Leaderboard statistics: `clog`, `pets`, `points`, `greenlogs`, `monthly-clog`, `monthly-points`,
`clues`, `combat`, `bosses`, and `xp`. Boss aliases include `cox`, `tob`, `toa`, and `bandos`.
Player names containing spaces can be quoted, or supplied with `--player "Love Yourz"`.

Standings include every available account and retain their original ranks when searched. Global
statistics and Dryboard have separate Mains, Ironman + GIM + UIM, and Hardcore Ironman columns.
Luck and Deserved use one account per linked member. Categories with no counter tie completed logs
at #1; otherwise completed logs without a comparable counter remain visible without a numeric rank.
When fewer than ten members have completed a category, the remaining places show the closest
unfinished logs. Double-click a category-board row to open that account’s collection log.

Collection logs use RuneLite’s cached item sprites, adaptive cell sizes, and hover names.
Enter an account in **Find a clan member** and press Enter to load its log; **Find me** returns to your own. Completion counters remain frozen at their saved greenlog value; `~` marks an
estimated value. Dryboard uses recorded drop history and omits accounts without comparable current
and last-drop counters. These views read clan data and do not trigger a sync or modify points.

The client keeps its 64 most recently viewed selections in memory for the session. Reopening a
cached view makes no HTTP or database request; leaderboard searches are local too. Refresh
reloads the selected view from clan data, bypassing both the client and backend view caches.
Restarting the client or evicting an older selection requires another read. Refresh does not sync
the game account. The category catalogue and RuneLite item sprites are also cached.
Cached pages switch immediately without a loading frame. While a new collection page loads,
the account header, main tabs, and category buttons stay visible; only its item area shows loading
or a retryable error.
Completed collection categories have green text, borders, and backgrounds in the sidebar, including
pages not yet opened. The selected greenlog has a brighter highlight. Completion status is scoped
to the selected account and uses the same cached log response without extra database reads.

Share a board through regular public, clan or private chat with `!reval luck araxxor`, `!reval leaderboard clues hard`,
or `!reval clog forestry`. Updated Reval clients show a teal link such as
**Click to view Araxxor luck board** or **Click to view Valgusruum's collection log: Forestry**.
Left-clicking opens the chat menu; choose **Open Reval board** to open the window. The same option is available on right-click.
Private messages use the same labels in both the chatbox and split private chat, including your own sent messages.
The original command remains attached to the link. A clog without an explicit player opens the
sender's account; add a player name to share another account. Sharing selects the same page and
account, using the recipient's normal cache; it is not a frozen snapshot. Messages remain ordinary
chat for players without the update. Shares, including your own, do not fetch data or open a window until
**Open Reval board** is selected. History redraws never open a window, and the plugin never sends an extra chat message
automatically. Links are formatted before the
game lays out each message, so they appear immediately and survive notifications, scrolling and
chat-filter changes. Original history text stays unchanged.

In **Reval Clan → Clan browser**, **Enable !reval chat commands** is on by default.
Switch it off to leave shared commands as ordinary text without board-click actions.
Changes apply immediately to existing chat history. Sidebar browser access remains available
when chat commands are disabled.

For local development, start the backend’s read-only `scripts/dev-clan-browser.ts` server with a
read-only database URL, then run:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runBrowserClient \
  -PrevalBrowserApi=http://127.0.0.1:4786/plugin \
  '-PrevalBrowserCommand=clog forestry Valgusruum'
```

`runBrowserClient` starts the real RuneLite client with Reval loaded and opens the selected browser
page directly without sending chat. `runBrowserPreview` opens just the native window without a game login;
its optional `revalBrowserAtlas` points to a local sprite atlas for visual QA. Without an API
override, the plugin uses the normal Reval backend. The backend clan-browser endpoints must be
deployed before an owner-managed plugin release.

Local launchers and their logging config live in `src/dev`. `./gradlew build` creates the plugin
jar from `src/main`; `./gradlew shadowJar` creates the standalone development client with its
RuneLite runtime. Use `./gradlew runClient` to launch the client normally. Neither jar includes
automated test classes or test-framework dependencies.

### 🖥️ Sidepanel UI (Planned)
A dedicated panel for Reval clan members with:
- **Profile**: View your clan rank, points progress, milestones, and achievements
- **Ranks & Points**: Browse all clan ranks, requirements, and point sources
- **Competitions**: Track active and upcoming clan competitions with leaderboards
- **Events**: View and register for clan events
- **Clan Tasks**: Weekly and monthly task challenges
- **Clan Diaries**: Custom achievement diaries with tiered rewards
- **Achievements**: Combat achievements and collection log progress
- **Leaderboard**: Clan-wide rankings and statistics

### 📊 Player Data Sync
- **Combat Achievements**: Full tracking of all 625 tasks with completion status, points, and tier progress
- **Collection Log**: Complete item tracking with obtained items, kill counts, and category organization
- **Personal Bests**: Capture game-recorded times from visited Collection Log and Combat Achievements boss pages, including separate raid modes
- **Achievement Diaries**: Progress tracking for all regions and difficulty tiers
- **Quest Completion**: Full quest state tracking with completion counts
- **Player Metadata**: Account type, combat level, total level, and experience tracking

### 🔔 Real-Time Event Notifications
The plugin sends webhook notifications for the following events:

- **Loot Drops**: Valuable loot (configurable min value, whitelisted items, or untradeables) with GE values
- **Pet Drops**: All pet acquisitions with milestone tracking
- **Level Ups**: Skill level achievements
- **Quest Completions**: Quest finished notifications
- **Kill Counts**: Boss kill count milestones
- **Clue Scrolls**: Clue completion with tier and rewards
- **Achievement Diaries**: Diary task completions with precise tracking
- **Combat Achievements**: CA task completions
- **Collection Log**: New collection log item acquisitions
- **Player Deaths**: Death tracking with killer information (NPC/Player)
- **Detailed Kill Tracking**: Damage dealt, weapons used, special attacks
- **Emote Usage**: Emote performance tracking
- **Music Played**: Track when players play specific music tracks
- **System Chat Messages**: Configurable pattern matching for game messages

### 🔐 Clan Validation
All webhooks are protected by clan membership validation. Only members of the "Reval" clan with sufficient rank can send notifications.

## Installation

### RuneLite Plugin Hub
1. Open the RuneLite client
2. Click on the wrench icon (Configuration)
3. Click on the Plugin Hub button (puzzle piece icon)
4. Search for "Reval Clan"
5. Click Install

## Configuration

Configure the plugin in the RuneLite settings panel:

### Main Settings
- **Enable Webhook**: Master toggle for all webhook notifications
- **Save Local JSON**: Save collected data to local JSON files on logout

### Notifications
Which game events are tracked is decided by the Reval backend, not per player, so the plugin stays in sync with what the clan systems expect. The per-player switches are:
- **Show clan notifications**: Reval announcements and notifications in chat
- **Send player deaths to Discord**: post your deaths (and who killed you) to the clan Discord
- **Leagues Events**: Leagues task, relic, area and combat mastery tracking

### Leagues Bingo boards
Active Leagues Bingo events on the Events tab open in the side panel: pick a team from the standings, pick one of its region boards (completion, points and x2 status per board), then browse the tile grid. Clicking a tile shows the task, its requirements, progress and who contributed.

### Profile cards
Choose where the "View Reval Profile" right-click option appears: on players in the world, in chat, or in the clan member list.

## Usage

### Collection Log Sync
1. Open your Collection Log in-game
2. Click the "Sync Reval" button in the top-right corner
3. The plugin will capture all obtained items and their quantities

### Automatic Sync on Logout
When you log out, the plugin automatically collects and sends all player data to your configured webhook endpoint.

### Personal Best Times
Visit boss pages in Collection Log or **Combat Achievements → Bosses** to capture their current personal bests. Combat Achievements includes times such as Jad, Zuk, and raid modes that Collection Log does not display. These pages provide overall times for each mode; team-size variants are captured only when a source identifies the team size.

Captured times are included in the next player-data sync or logout. Bosses with no recorded time or no completions are skipped, and unvisited pages are not scanned automatically.

## Support

For bug reports and feature requests, please open an issue on the [GitHub repository](https://github.com/Meduza/reval-cc-plugin).

## License

This project is licensed under the BSD 2-Clause License - see the LICENSE file for details.

## Credits

Created by **Lightroom** for the Reval clan community.

## Version

Current Version: 1.5.1

## Acknowledgments

Portions of this plugin were inspired by or derived from:
- [Dink](https://github.com/pajlads/DinkPlugin) - Licensed under BSD 2-Clause License
- [TempleOSRS](https://github.com/SMaloney2017/Temple-OSRS-Plugin) - Licensed under BSD 2-Clause License

See the LICENSES directory for third-party license details.
