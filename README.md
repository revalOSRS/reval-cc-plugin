# Reval Clan Plugin

A comprehensive RuneLite plugin for the Reval clan in Old School RuneScape. Features a full-featured sidepanel UI for clan members, tracks player progress, sends real-time event notifications, and provides detailed data collection for Combat Achievements, Collection Log, Achievement Diaries, and more.

## Features

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
- **Rank-up announcements**: Announce earned Reval ranks and their in-game application in clan chat with the new rank's title and icon
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
- **Show clan notifications**: Reval announcements, notifications, and in-game rank promotions in chat
- **Send player deaths to Discord**: post your deaths (and who killed you) to the clan Discord
- **Leagues Events**: Leagues task, relic, area and combat mastery tracking

Rank-up announcements use the same setting and appear as clan-system messages, for example
`[Reval] Shafli's clan rank is now [rank icon] Sapphire.` They announce actual in-game
rank increases observed while connected to Reval clan chat, including changes to offline
members in the clan roster. Every plugin client displays its own local announcement;
the plugin does not send player chat or require a backend broadcast. Logging in, hopping,
rejoining chat, and enabling the plugin establish a baseline without replaying old rank
changes. New members and demotions are not announced. The backend also delivers new point-earned
ranks as `[Reval] Shafli has earned [rank icon] Sapphire.`, before staff applies the rank.
These earned-rank notifications expire after 15 minutes and use the existing notification
fetch/acknowledgement path. The earned trigger requires the companion backend change;
old clients display its plain-text broadcast. If an icon is unavailable, the title still appears.

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
