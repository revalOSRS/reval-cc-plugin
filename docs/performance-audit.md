# Plugin performance and resource audit

Reviewed the production plugin at `0985205` (v2.21.2), across all 174 Java source files, on 2026-10-18. The review covered subscriptions and game ticks, account collection, notification delivery, persistence, overlays, caches, Swing panels and native windows. Development launchers were checked through the standalone build. Uncommitted prototype work in the separate main checkout is outside this PR.

## Findings addressed

| Area | Previous behavior and impact | Change |
| --- | --- | --- |
| Session persistence | Game ticks, world hops, login recovery and delivery acknowledgements performed filesystem work on the client or Swing thread. Slow disk access could pause gameplay or the UI. | Queue file serialization, atomic writes, deletes and recovery on a single ordered worker using RuneLite's executor. Recovery returns to the client thread before accessing tracker state. |
| Webhook preparation | `sendDataAsync` serialized and gzipped its payload on its caller, usually the client thread. HTTP requests could accumulate while the backend was unavailable. | Move preparation to RuneLite's executor; limit outstanding preparation and delivery to 64 events; give requests a 30-second call timeout; cancel owned work on plugin stop. |
| Failed session delivery | Network failures, error statuses and invalid responses never notified the recovery callback. Session IDs remained in the tracker's `inFlight` set and could not be retried on subsequent logins. | Complete callbacks once with either a response or `null`, including cancellation, preparation failure and overload. Keep unacknowledged files for recovery. |
| Team colors | Every clan-list redraw or eligible chat message rebuilt the complete team lookup from the cached API response. While a request was pending or failing, each redraw could launch another request. | Reuse the derived map for five minutes, share one pending request, and retry failures after one minute. Reset invalidates old responses. |
| Screenshot capture | Simultaneous events requested and encoded the same frame independently. Missing frames left futures and notification payloads pending and chat widgets hidden. | Share one capture/encoder, finish after five seconds, restore chat on completion or stop, and retain at most one outstanding frame listener and encoder even after a timeout. |
| Logout and shutdown | Buffered loot, equipment provenance, pending pet/KC/diary work, death actor references and delayed sync state were incompletely reset. Startup's cache-ready callback could keep retrying after the plugin stopped. | Reset session buffers on logout and on the client thread during shutdown; invalidate delayed startup work. |
| Panel and request lifetime | Retry timers could revive replaced panels. Late profile and Bingo responses could restore account/event data after reset. Browser and API caches remained populated after stop. | Stop timers, invalidate stale callbacks, release account data and board component trees, close detached windows, cancel owned API calls and clear caches. Shared RuneLite services remain available to other plugins. |
| Dynamic varbit watches | Removed watches remained in the batching maps and could still emit values while other watches were active. | Prune both maps when new filter baselines are applied. |

## Evidence and validation

Ten initial regression checks failed against the previous behavior: synchronous session writes, synchronous webhook preparation, missing failure completion, unbounded webhook bursts, repeated team refreshes with pending/cached responses, duplicate screenshot captures, loot surviving reset, removed varbit watches and stale Bingo responses. The completed suite also covers write/delete/recovery ordering, retries after failed delivery, cancellation, restart, wire compatibility, late account responses, executor rejection and screenshot timeouts while an encoder is blocked.

The 35-test suite includes burst checks with 1,000 redraws, 1,000 screenshot requests and 1,000 webhook submissions. Assertions check work counts and queue bounds, avoiding hardware-dependent timing thresholds. Screenshot tests use RuneLite's real `DrawManager`, including its null-image behavior. RuneLite's [DrawManager implementation](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/DrawManager.java) exposes no next-frame removal method and discards queued listeners when its image supplier fails; the temporary frame watcher handles that case.

Validation environment: Java 21.0.11, RuneLite 1.13.2 resolved by the existing `latest.release` dependency, Java 11 compilation target.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew clean build shadowJar
git diff --check
```

The distributable plugin jar continues to contain production classes and resources only. JUnit and Mockito are test dependencies; the standalone development jar uses the existing separate dev source set.

## Behavior under failure

- When all 64 webhook delivery slots are occupied, additional events are rejected with a logged warning instead of extending the queue. Session summaries remain on disk for recovery; ordinary events have no durable replay. Callback failure is explicit, so rejection cannot pin session IDs in memory.
- File operations remain ordered: an acknowledgement cannot delete before its preceding write and leave the file resurrected afterward. A completed atomic write survives a process crash. A snapshot still queued at the instant the process is killed has not reached disk; the existing approximately 30-second snapshot cadence remains.
- A screenshot timeout sends the notification without an image. While drawing or encoding is stalled, later events reuse that completed result rather than enqueueing more images. Capturing resumes after the outstanding frame/encoder is released.

## Other paths reviewed and useful follow-up measurements

The notification regexes are already compiled and reused; collection-log cache parsing occurs at startup; full quest/diary/CA/collection-log collection runs at explicit sync or login/logout boundaries. Kill attribution already removes entries on NPC death and despawn. The browser view cache has a 64-entry limit, collection grids paint visible cells, and optional sidebar tabs already load on demand. Those mechanisms do not warrant speculative rewrites from this audit.

This is a source audit with deterministic regression and burst checks, not a live heap or frame-time profile. The useful next measurement is a logged-in recording with the normal renderer: idle with the sidebar closed, busy combat, a full collection-log sync, screenshot events, world hops, account changes and repeated theme/plugin toggles. Compare frame-time spikes and retained heap after garbage collection. If full-sync collection is a measurable spike, profile the quest/CA enum traversal before introducing more caches. A long-session soak with GPU/117 HD would establish whether any renderer-specific cost remains.
