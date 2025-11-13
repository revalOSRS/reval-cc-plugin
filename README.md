# Reval Clan Plugin

A comprehensive RuneLite plugin for the Reval clan in Old School RuneScape. Tracks player progress, sends real-time event notifications, and provides detailed data collection for Combat Achievements, Collection Log, Achievement Diaries, and more.

## Features

### 📊 Player Data Sync
- **Combat Achievements**: Full tracking of all 625 tasks with completion status, points, and tier progress
- **Collection Log**: Complete item tracking with obtained items, kill counts, and category organization
- **Achievement Diaries**: Progress tracking for all regions and difficulty tiers
- **Quest Completion**: Full quest state tracking with completion counts
- **Player Metadata**: Account type, combat level, total level, and experience tracking

### 🔔 Real-Time Event Notifications
The plugin sends webhook notifications for the following events:

- **Loot Drops**: Valuable loot (1M+ GP, whitelisted items, or untradeables) with GE/HA values
- **Pet Drops**: All pet acquisitions
- **Level Ups**: Skill level achievements
- **Quest Completions**: Quest finished notifications
- **Kill Counts**: Boss kill count milestones
- **Clue Scrolls**: Clue completion with rewards
- **Achievement Diaries**: Diary task completions with precise tracking
- **Combat Achievements**: CA task completions
- **Collection Log**: New collection log item acquisitions
- **Player Deaths**: Death tracking with killer information (NPC/Player)
- **Detailed Kill Tracking**: Damage dealt, weapons used, special attacks
- **Area Entry**: Region/area entry notifications
- **Emote Usage**: Emote performance tracking

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
- **Webhook URL**: Your webhook endpoint URL for receiving data
- **Save Local JSON**: Save collected data to local JSON files on logout

### Event Notification Toggles
Each event type can be individually enabled/disabled:
- Loot Drops
- Pet Drops
- Level Ups
- Quest Completions
- Kill Counts
- Clue Scrolls
- Achievement Diaries
- Combat Achievements
- Collection Log
- Player Deaths
- Detailed Kills
- Area Entry
- Emote Usage

## Usage

### Collection Log Sync
1. Open your Collection Log in-game
2. Click the "Sync Reval" button in the top-right corner
3. The plugin will capture all obtained items and their quantities

### Manual Test
Type `::testreval` in the game chat to send a test webhook and verify your configuration.

### Automatic Sync on Logout
When you log out, the plugin automatically collects and sends all player data to your configured webhook endpoint.

## Support

For bug reports and feature requests, please open an issue on the [GitHub repository](https://github.com/Meduza/reval-cc-plugin).

## License

This project is licensed under the BSD 2-Clause License - see the LICENSE file for details.

## Credits

Created by **Lightroom** for the Reval clan community.

## Version

Current Version: 1.0.0

## Acknowledgments

Portions of this plugin were inspired by or derived from:
- [Dink](https://github.com/pajlads/DinkPlugin) - Licensed under BSD 2-Clause License

## License

This project uses code from third-party projects. See the LICENSES directory for details.
