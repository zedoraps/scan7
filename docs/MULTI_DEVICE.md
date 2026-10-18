# Scan7 – Multiple phones in one game (spec)

> Spec, 2026-10-18. Status: **flow agreed with the user; proposals below still to confirm; nothing in the UI built.**
> A device-free protocol spike lives on the branch `worktree-agent-a50dec2812cd0b47a` (package `multiplayer/`, see §9). It is **not merged into main**.
> Context: [APP_FLOW.md](APP_FLOW.md) (game flow), [../design/DESIGN_HANDOFF.md](../design/DESIGN_HANDOFF.md) (look), [../AGENTS.md](../AGENTS.md). App: `minSdk 29`, `targetSdk 37` (Android 17).

---

## 0. Decisions, proposals, open questions

### 0.1 Decided by the user
1. On the **host** (the phone that runs the game) the user taps **"Share this game"** (the user called it "connect others").
2. **1–10 more people scan the host's QR code** with Scan7 on their own phone. Every phone needs the app.
3. Each guest **picks who they are** (a player of this game).
4. Each guest **picks the players they will scan**. Those players are **highlighted on that phone** and get **a small device badge on the other phones**, in both the table and the list view. A guest can technically still record other players, but the main flow is to scan only your own.
5. Guests then scan their players.
6. Guests can **only edit their own recent entries** (the running round). They can't make general score changes.
7. Guests **can't change totals** (no Adjust total).
8. Guests **can't end a round** (no Close round, Next round or End game).
9. **Reuse the existing UI** as much as possible. The guest UI is the normal round screen with fewer buttons.
10. **"Same players, new game"** keeps the joined guests connected. They move to the new game automatically.
11. **Home** gets **"Join a game"**. The **round screen** gets **"Share this game"** (the QR).
12. **Guests keep a copy of the game in their history**, marked "joined" (read-only).
13. **Play services are OK.** The transport is **Google Nearby Connections (`P2P_STAR`)**. F-Droid isn't a target for multiplayer, so a Play flavor of the build is fine (§6.4).
14. **Players can be managed during a game** (planned on main): only the host adds or removes players. Guests' claims on a removed player are dropped (§4.7).

### 0.2 Proposals (please confirm or change)
- **P1. One screen for steps 3 + 4**, "Who are you? · Who do you scan?". "Me" is preselected as scanned. You can add extra players, e.g. a neighbour without a phone. A player another device already scans shows that device's badge. Claiming them anyway asks first ("Take over Cleo from Anna's phone?"), and then they move to you.
- **P2. "Share this game"** sits in the round menu (Actions group) and as a small icon in the round header (host only). It opens a **share sheet** with the QR, the connected devices (who they are, whom they scan, online or offline) and **Remove** per device (confirmation, destructive style). The sheet also has **Stop sharing**.
- **P3. Conflicts:** the host decides, and its DB is the single source of truth. If two devices score the same player, the later one gets **"Already scored by <device> (+30)"**. Because of rule 6, a guest can't replace another device's entry: the dialog only has OK (the host can fix it). The host can always replace any entry.
- **P4. Guest restrictions are hidden, not greyed out:** Close round, Next round, End game, Adjust total, Rearrange seats, target and game settings. When everyone is done, the guest's bottom bar shows **"Waiting for <host> to close the round"** (text, not a button). Edit, Rescan and Reset to open show only on the guest's own entries of the running round. Other entries show "Scored by <device>".
- **P5. Host leaves or its phone dies:** guests see a non-blocking banner, "Connection lost · reconnecting…". They can keep scanning, and their scans wait in an outbox and are sent on reconnect. When the host app comes back, the same session resumes on its own. **Host migration is out of scope for v1.**
- **P6. "Scan next"** on a guest walks only its own claimed players. On the host it walks the players nobody else claimed plus the host's own.
- **P7. Badges:** the own players get a cyan outline plus a small "YOU SCAN" tag on the card or row. Other devices' players get a small phone badge with that device's owner avatar (initial). This needs a design pass (handoff rule).
- **P8. Leaving:** "Leave game" (guest round menu) releases that phone's claims. A plain disconnect keeps them, so the badge goes to "offline" and the claims come back on rejoin.
- **P9. Guest scoreboard:** read-only, including closed rounds (no reopen or edit).

### 0.3 Open questions
1. **P3:** if a guest scanned a player who another phone had already scored, is "OK, the host can fix it" enough? Or may a guest replace an entry of a player **it has claimed**?
2. **P6:** should the host's Scan next also include guest-claimed players (e.g. so it can help when a guest is slow)?
3. **Host's name in the UI:** the host phone's device name ("Pixel 10 Pro") or the host's "me" player ("Philip's phone")? The proposal is the "me" player, with the device name as fallback.
4. **Guest player lists:** a joined game brings players the guest doesn't have stored. They are kept only for that game's history and never show in the guest's Players list. Should there be a "Save Philip to my players" option later?
5. **Up to 10 guests + host = 11 phones, but a game has at most 10 players.** Is a guest without a player of its own (a pure viewer or helper) wanted? The proposal is yes: "Who are you?" can be "Just watching / helping".
6. **Training scans on guests:** stay on the guest phone under its own debug setting (proposal). OK?
7. **Test hardware for M2/M4:** is a second (and third) Android phone with Play services available? Nearby doesn't work between emulators.

---

## 1. User stories

| # | As … | I want … | Acceptance |
|---|---|---|---|
| U1 | host | to tap **Share this game** and show a QR | QR + "0 phones connected" within 1 s. Advertising runs while the sheet is closed (foreground-service notification) |
| U2 | guest | to tap **Join a game** on Home and scan the QR | Connected in ≤ 10 s with one permission prompt. Then "Who are you · Who do you scan?" |
| U3 | guest | to say who I am and whom I scan | My players are highlighted on my phone. The others see my badge on them within 1 s |
| U4 | guest | to scan my own row on my phone | Review → OK → my result shows on every phone within 1 s. Pending shows a hollow ✓ until the host confirms |
| U5 | everyone | to see the live table | Every phone shows the same results, totals, leader and round. Remote results flash briefly ("by Anna's phone") |
| U6 | host | to take over a player who has no phone | Unclaimed players are the host's by default. The host can scan or edit any player, any time |
| U7 | guest | to fix my own mistake | Edit, Rescan, Reset and Undo bust on my own entries of the running round |
| U8 | host | to close the round, end the game, adjust totals | As today. Guests see the result. Guests never get these buttons |
| U9 | guest | to leave and come back | Leave → the history copy stays, the claims are released. Rejoin by QR → snapshot, the claims are offered again |
| U10 | guest | my phone dropping off (screen off, out of range) to cost nothing | Reconnects by itself and gets a snapshot. Scans made meanwhile are sent then |
| U11 | late joiner | to join in round 5 | Snapshot of the whole game, then step U3 |
| U12 | host | my phone dying or the app being killed not to lose the game | The game is in the host's DB. Reopening Scan7 resumes sharing with the same QR, and guests reconnect |
| U13 | host | "Same players, new game" with everyone still connected | Guests switch to the new game, and their claims stay |
| U14 | host | to add or remove a player mid-game (once main has it) | Every phone updates. Claims and entries of a removed player go away |
| U15 | host | to remove a phone | It is disconnected and can't rejoin this session. Its claims are released |
| U16 | guest | a copy of the game in my history | A history row marked "Joined · Philip's phone" opens a read-only scoreboard |

---

## 2. UX per screen (minimal, reuse first)

Everything new uses existing components: `Scan7ConfirmDialog`, the M3 bottom sheet, `PlayerAvatar`, the round cards and rows, the camera chrome and the snackbar or toast. **New screens and badges need a design pass with the user before M3 is built** (DESIGN_HANDOFF rule).

**Home**
- A new tertiary text button **"Join a game"** goes under the pinned **Players · New game** row. It opens the join flow.
- The Resume card of a shared game shows "Shared · 3 phones". On a guest it shows "Joined · Philip's phone" while the session lives.
- In Past games, joined games show the tag **"Joined"**. Tapping one opens the read-only scoreboard, and they can be deleted like any other game.

**Join flow (guest, new)**
1. **QR scanner:** the existing camera chrome (← back, title "Scan the QR on the host's phone", no shutter). Decoding uses ZXing core (Apache-2.0, pure Java) on CameraX frames. A `scan7://join?...` deep link also works from the system camera.
2. **Permission rationale** (one line + Continue), then the system **"Nearby devices"** prompt (§6.3). On denial: "Scan7 needs Nearby devices to join · Open settings".
3. "Connecting to Philip's phone…", then **Who are you? · Who do you scan?** (P1). This reuses the New game player list (avatar rows). The top section, "I am", is a single choice plus "Just watching". The second section, "I scan", is multi-select with me preselected. Rows claimed by another phone show its badge. **Done** → round screen.

**Round screen: host**
- Header: a small **share icon** next to ⋮ (filled while sharing, with the count of phones). Round menu → Actions → **Share this game**.
- **Share sheet:** big QR + short code, then the devices (owner avatar, name, "scans: avatars…", ● online / ○ offline, ✕ Remove → confirm "Remove Anna's phone?" destructive), then **Stop sharing** (confirm).
- Cards and rows of guest-claimed players carry the device badge. A remote result flashes "by Anna's phone" for 2 s.
- Close round: if a guest still has an open player, the existing confirmation adds "Anna's phone is still scanning Ben."

**Round screen: guest** (the same composables, fewer actions)
- Header: logo · Table|List · ⋮. The guest's ⋮ holds: Scoreboard, Scan direction, **Who do you scan?**, **Leave game** (confirm). Rearrange seats and End game are left out.
- Own players are highlighted (P7). Others' players have badges.
- Bottom: **Scan next · <my next open player>** (P6). Close round is left out. When all my players are done, **Scan next** gives way to "Waiting for Philip to close the round" (muted text), or to "All your players are done" while others are still open.
- Player sheet: an open player has Scan · Enter manually · Bust. My own done entry has Edit · Rescan · Reset to open. Someone else's entry shows the result + "Scored by Ben's phone", read-only. Adjust total is left out.
- Banner states: "Connection lost · reconnecting…" (non-blocking), "Removed by the host" (blocking, → Home).

**Scan flow (both)**
- Unchanged camera, review and manual screens. On a guest, OK sends the intent and returns as usual. The card shows a **hollow ✓ (pending)** until the host's event arrives.
- Refusals become a `Scan7ConfirmDialog`-style info dialog: "Already scored by Ben's phone (+30)" · OK, or "Round 3 was closed by Philip" · OK.
- Photos and training JPEGs never leave the phone.

**End of round / game over**
- The host is unchanged. On a guest, the end-of-round view replaces **Next round** with "Waiting for Philip…". Game over on a guest shows the result + **Leave**, with no New game buttons. When the host taps **Same players, new game**, every guest goes straight to round 1 of the new game with its claims kept (U13).

---

## 3. Roles and permissions

| Action | Host | Guest |
|---|---|---|
| Score an open player (scan / manual / bust), running round | ✅ | ✅ (anyone; the main flow is own players) |
| Edit / rescan / reset / undo bust a done result, running round | ✅ any | ✅ **only entries this phone made** |
| Edit results of closed rounds, reopen a round | ✅ | ❌ |
| Close round · Next round · End game · Abandon | ✅ | ❌ |
| Adjust total / remove an adjustment | ✅ | ❌ |
| Rearrange seats, view per game, target | ✅ | ❌ (Table/List view toggle is local only) |
| Claim players ("who do you scan") | ✅ (always wins) | ✅ (asks before taking players from another phone) |
| Add / remove players mid-game, rename players | ✅ | ❌ |
| Share / stop sharing / remove a phone | ✅ | ❌ (Leave only) |

One pure function enforces this on both sides: `GuestRules.refusal(action, state, authors, device)`. The host checks every intent with it, and the guest UI uses the same function to decide which buttons to show.

---

## 4. Architecture

### 4.1 Host-authoritative event log + snapshot
- The **host's Room DB is the source of truth.** Guests never write game state. They send **intents** (`Intent{intentId, baseSeq, action: GameAction}`).
- `GameAction` is a sealed, `@Serializable` wire form of the existing `GameState` mutations (`ScoreCards`, `ScorePoints`, `Bust`, `UndoBust`, `ResetToOpen`, host-only `AdjustTotal`, `RemoveAdjustment`, `CloseRoundAndStartNext`, `ReopenRound`, `EndGame`, `ReorderSeats`, `MovePosition`, …). Player actions carry an **explicit round**, so a late intent never lands in the next round.
- The host applies an accepted action with the same pure `GameState` code (in the app: `GameRepository.apply(gameId, action)` → `mutate`). It numbers the action with a gap-free **`seq`** and broadcasts `Event{seq, at, action, intentId, by}`.
- **Guests mirror** by applying each event with the **same `GameAction.apply(state, at, seq)`**: same input, same timestamp, same state. A seq gap or an apply failure → `ResyncRequest` → `Snapshot`. Every (re)join gets a `Welcome` with the full snapshot (10 players × 30 rounds ≈ 7 KB gzipped, measured), so no event replay is needed.
- **Host UI = local path:** the host's own taps go through the same `GameAction` path (`HostSession.perform`), so one code path serves single-phone and shared play. Without sharing, nothing changes.

### 4.2 Conflict handling (in order, per intent)
1. **Duplicate** `intentId` (a retry after a reconnect): answered with the original outcome (`IntentAck` or the same refusal). It is never applied twice.
2. **Host-only** action → `NOT_ALLOWED`.
3. **Stale:** the result the intent targets changed after the guest's `baseSeq` (another phone scored that player, or the round was closed) → `STALE` with `author` ("Already scored by Ben's phone"). `baseSeq` is the seq the guest saw when the user **started** the action (opened the camera), not when it was sent.
4. **Guest rules** (§3): older round, or another device's entry → `NOT_ALLOWED` with `author`.
5. **`GameState` refuses** it (game over, unknown player, nothing to undo) → `INVALID`.

There are no merges and no CRDT. Order of arrival at the host decides, and every refusal is shown to the user (P3).

### 4.3 Claims ("who do you scan")
- The host keeps a **roster**: `DeviceInfo{deviceId, name, me, scans, connected}`, with the host itself as `"host"`. It broadcasts `Roster` on every change (join, drop, claim, remove).
- `Claim{me, scans, force}`. Players another device scans → `ClaimRejected{conflicts}` (nothing changes). The UI asks, then resends with `force = true`, which moves the players. The host's own claims always win.
- Claims are **UI guidance only**, not permissions. Any guest may score an open player (rule 4). Badges, highlights and Scan next come from the roster.
- Claims survive a disconnect (shown offline) and a rejoin (the same `deviceId`). "Leave game" releases them (P8, to build in M1).

### 4.4 Identity
- **`deviceId`:** a random UUID per install (settings table). It recognises a returning phone on a new transport endpoint, and the old link is dropped.
- **Player ids** in the protocol are the **host's** Room ids, valid inside the session. The snapshot carries `PlayerDto{id, name, avatar}`.
- **`gameUid`** (new, UUID column on `games`): the guest's history copy is keyed by `(gameUid)`, so rejoining updates the copy instead of duplicating it. "Same players, new game" creates a new uid.
- **Adjustment ids** are the event `seq` that created them (spike) or a new `uid` column, so "remove adjustment" addresses the same row on every phone.

### 4.5 Offline-first and persistence
- **Host:** game state as today (Room). New table `share_session` (M1): `sessionId`, `token`, `gameId`, `seq`, the last ~1000 intent outcomes (dedup across restarts), and the roster. With it, a host restart **resumes the same session**: same QR, guests reconnect on their own, nothing is applied twice, and intents older than the restart are treated as stale (`floorSeq`).
- **Guest:** the session mirror lives in memory (plus `SavedStateHandle`). After each snapshot or event, the latest state is also written as the **joined history copy** (`games.origin = JOINED`, `host_name`, `game_uid`). Its players are stored as hidden per-game players (`players.joined_only = 1`, never in the Players list). The **outbox** (pending intents) is persisted too, so a scan made while offline survives the guest app being killed.
- **DB migration** (v5, M1): `games.game_uid`, `games.origin`, `games.host_name`, `players.joined_only`, `share_session`. Each gets a MigrationTest, as for v3/v4.

### 4.6 Lifecycles
- **Late join / reconnect:** `Hello{lastSeq}` → `Welcome{seq, snapshot, roster}` → the guest resends its outbox (same ids).
- **Guest drop:** the host marks it offline and keeps its claims. The guest retries discovery of the same endpoint name (with backoff, up to 10 min, then "Rejoin" + QR).
- **Host app killed / phone dies:** guests show the banner (P5) and keep queueing. Guest phones still have a full copy. When the host reopens Scan7, Resume → sharing restarts from `share_session` → guests reconnect. If the host phone is gone for good, the guests keep their read-only history copy. Host migration ("continue on this phone") is a later option, not v1.
- **Same players, new game:** `HostSession.newGame(game, players)`. The seq continues, staleness and authors reset, and claims are kept for the players still in the game. Guests receive a `Snapshot` with the new game id.
- **Stop sharing / remove a phone:** removed phones get `HelloRejected(REMOVED)` and are disconnected. They can't rejoin this session; a new share session (new token) resets that.

### 4.7 Managing players during a game (planned on main)
- Adding or removing a player is a **host-only** change made through the repository (it is not a guest intent). The host then calls `HostSession.updateGame(game, players)`. The seq advances and everyone gets a `Snapshot` + `Roster`.
- A **removed** player's claims (`me` or `scans`) are dropped on every device, and so are their entry authors. Intents still in flight for them fail as `INVALID` ("Cleo is no longer in this game").
- An **added** player appears unclaimed, so the host scans them by default and any guest can claim them.
- Once main has `GameState.addPlayer` / `removePlayer`, both become ordinary `GameAction`s (`AddPlayer{PlayerDto}`, `RemovePlayer{id}`, host-only). That keeps them in the event log, and the snapshot path stays as the fallback.

### 4.8 Threading
Sessions are plain Kotlin and not thread-safe. In the app, each one runs on a single-threaded dispatcher (`Dispatchers.Default.limitedParallelism(1)`). Nearby callbacks post to it, and the UI observes `StateFlow`s (`ClientSession.view`, host state via Room flows).

---

## 5. Protocol

### 5.1 Framing
The transport payload is one BYTES payload per message: `[format byte][body]`. Format `0` = UTF-8 JSON, `1` = gzipped JSON (used from 4 KB). The body is `{"v": <protocol>, "m": {"type": "...", ...}}`, encoded with kotlinx.serialization (already a dependency: polymorphic sealed classes, `classDiscriminator = "type"`, `ignoreUnknownKeys`). The design limit is **32 KB per frame**. Nearby's own limit is about 1 MB, and a measured 10-player, 30-round snapshot is about 7 KB gzipped.

### 5.2 Messages
```
guest → host                                  host → guest
hello{sessionId, token, deviceId,             welcome{sessionId, seq, snapshot, roster}
      deviceName, lastSeq?}        [frozen]   hello_rejected{reason, hostProtocol}   [frozen]
intent{intentId, baseSeq, action}             event{seq, at, action, intentId?, by?}
claim{me?, scans[], force}                    intent_ack{intentId, seq}          (duplicate)
resync{lastSeq}                               intent_rejected{intentId, reason, detail?, author?}
(leave{} – M1)                                snapshot{seq, snapshot}
                                              roster{devices[]}
                                              claim_rejected{conflicts[]}
snapshot = {game: GameDto, players: [PlayerDto], authors: [{round, playerId, deviceId}]}
```
`reason`: join `VERSION_MISMATCH | BAD_TOKEN | WRONG_SESSION | SESSION_FULL | REMOVED`, intent `STALE | NOT_ALLOWED | INVALID`.

### 5.3 Wire DTOs
The domain classes (`GameState`, `PlayerResult`, …) are **not** annotated. The protocol has its own DTOs (`GameDto`, `RoundDto`, `ResultDto`, `WireCard{label, ignored}`). Card labels are the stable detector labels (`ml/config/cards.yaml`), so refactoring the domain can't silently change the wire format.

### 5.4 Versioning
- `PROTOCOL_VERSION = 1`. Peers need the **same major version**. Adding an optional field with a default needs no bump, because old peers ignore unknown keys. Anything else bumps the version.
- The codec reads `v` **before** decoding the message and refuses non-frozen messages of another version (`UnsupportedVersion`).
- **Frozen forever:** the frame layout, `hello`, `hello_rejected`, and the `JoinRejection` values. An outdated phone can therefore always be told "Update Scan7 on this phone (host uses protocol 2)".
- The QR carries `v` as well, so the joiner can warn before connecting.

---

## 6. Transport: Nearby Connections

### 6.1 Shape
- Strategy **`P2P_STAR`**: the host advertises, guests discover. The service id is `net.moesli.scan7.mp` (the app id + `.mp`; debug suffixes give separate ids, so a debug build and a release build never mix). Only BYTES payloads are used.
- The **endpoint name** = `scan7:<sessionId>`. The guest discovers, matches the session id from the QR, requests the connection and **accepts automatically** (no "compare the codes" dialog). The token then authorises the phone in `hello`.
- `NearbyHostTransport` / `NearbyClientTransport` implement the spike's `HostTransport` / `ClientTransport` (`multiplayer/Transport.kt`). They deliver in order per endpoint and on one dispatcher.
- **Foreground service** while hosting or joined (`foregroundServiceType="connectedDevice"`, notification "Scan7 is sharing a game"), so screen-off and the camera don't kill the link. "Keep screen on" already applies on the round and camera screens.
- Disconnects: Nearby `onDisconnected` → the transport's `onPeerDisconnected`. The guest reconnects with exponential backoff (1, 2, 4 … 30 s). The host sends `hello_rejected` and waits about 300 ms before `disconnectFromEndpoint`, so the frame is not lost.

### 6.2 QR content
`scan7://join?v=1&s=<sessionId>&t=<token, 128-bit base64url>&n=<host name>`, shown with ZXing core. The short code on the share sheet is the first 6 characters of the session id; it is a fallback for a broken camera and goes with a "Nearby games" list. Joining by short code asks the host "Let Anna's phone join?" because the token is missing.

### 6.3 Permissions (requested only on Share or Join, with a one-line rationale)
| API level | Runtime permissions Nearby needs | Manifest |
|---|---|---|
| 29–30 (Android 10–11) | `ACCESS_FINE_LOCATION` (and Location services must be on) | `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion=30`), `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` |
| 31–32 (Android 12) | `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` + `ACCESS_FINE_LOCATION` (Nearby still wants location up to 31/32) | `ACCESS_FINE_LOCATION` with `maxSdkVersion=32` |
| 33+ (Android 13+) | the three Bluetooth permissions + `NEARBY_WIFI_DEVICES` (`neverForLocation`); no location | — |
| 37 (Android 17, our target) | + `ACCESS_LOCAL_NETWORK` for the Wi-Fi LAN medium (same "Nearby devices" group, no extra dialog; **verify on a device**) | — |
| all | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` (34+), `POST_NOTIFICATIONS` (33+, optional) | — |

On 12+ these mostly show as **one "Nearby devices" dialog**. On 10–11 it is the location dialog, which needs a clear rationale ("Android needs location access to find nearby phones; Scan7 never uses your location"). The exact list is checked against the current [Nearby get-started guide](https://developers.google.com/nearby/connections/android/get-started) when M2 starts.

### 6.4 Dependency, flavors, license
- `com.google.android.gms:play-services-nearby` is **proprietary** (Play services) and needs Play services on the phone (excludes de-Googled ROMs and Huawei).
- **Build flavors:** `play` (default for Play and GitHub releases) has Nearby + multiplayer UI. `foss` has no Play services; Share and Join are hidden, and everything else is identical. The `multiplayer/` protocol package stays plain Kotlin in `main`. Only `src/play/…/NearbyTransport.kt` depends on Play services. F-Droid (optional, docs/RELEASE.md) builds `foss`. The user decided multiplayer isn't an F-Droid target.
- **AGPL:** linking a proprietary library into an AGPL app is fine for the copyright holder. To let others redistribute the `play` build, add an **AGPL §7 additional permission** for Google Play services to the license notice (a standard pattern). The About screen lists Play services Nearby in the third-party notices (`ThirdPartyNotices`).
- Privacy note: the Play services SDK may send usage diagnostics (users can turn this off in system settings). Say so in the privacy policy.

### 6.5 Not chosen (short)
- **Local Wi-Fi with NSD + sockets (Ktor/WebSocket):** needs everyone on the same Wi-Fi (guest Wi-Fi with client isolation breaks it), or a local-only hotspot dance. Its main advantage would be F-Droid or iOS or browser viewers, and none of those is a goal. It stays possible later behind the same `HostTransport` interface.
- **Wi-Fi Direct / Wi-Fi Aware / raw BLE GATT / Bluetooth RFCOMM:** lower-level, OEM-flaky or missing on many phones; Nearby already uses them internally.
- **Online (Firebase, Supabase, relay):** needs internet and a backend, against "fully local".
- Research links: [Nearby overview](https://developers.google.com/nearby/connections/overview), [strategies](https://developers.google.com/nearby/connections/strategies), [payloads](https://developers.google.com/nearby/connections/android/exchange-data), [Android 17 local network permission](https://developer.android.com/privacy-and-security/local-network-permission).

---

## 7. Security and privacy
- **Local only:** no account, no server, no internet. Nearby encrypts the link.
- **Join token:** 128 random bits per share session, only in the QR. It is compared in constant time in `hello`; a wrong token → `BAD_TOKEN` + disconnect. Stop sharing invalidates it. Removed phones are blocked for the session.
- **Data exchanged:** player **names + avatar ids**, seats, results (status, points, **card labels**), adjustments (delta + note), the device names chosen for the roster, and timestamps. **No photos, no training JPEGs, no other games, no player stats.** Guests store the joined game (and those players) only as their read-only history copy.
- **Input from guests is untrusted:** frames are size-limited (32 KB); malformed or unknown messages are dropped; every intent is validated by `GameState` and `GuestRules`; a guest can't name another guest's `deviceId` (the host fills `by` from the link).
- `PRIVATE_*` photo rules are unaffected (nothing image-like is sent).

---

## 8. Milestones

| | Scope | Estimate | Test plan |
|---|---|---|---|
| **M1 Protocol + fake transport** (spike ≈ 60 % done) | `GameAction` + DTOs + codec, `HostSession`, `ClientSession`, `GuestRules`, claims and roster, `FakeNetwork` (**on the branch**). Still to do: `GameRepository.apply(gameId, GameAction)` so the host UI uses the same path; DB v5 (`game_uid`, `origin`, `host_name`, `joined_only`, `share_session`, adjustment uid) + migrations; host session persistence (seq, intent outcomes, roster) and resume; `leave`; guest outbox persistence; joined history copy; `AddPlayer`/`RemovePlayer` once main has them | 4–6 d | JVM: the spike suite (22 tests, §9) + Room tests for `apply` + MigrationTest 4→5 + randomized interleavings (seeds × 4 phones, also with random drops) where everyone converges with the host DB; restart test (host session reloaded from `share_session`, retried intents not re-applied) |
| **M2 Nearby transport behind a flag** | `play`/`foss` flavors, `play-services-nearby`, `NearbyHost/ClientTransport`, foreground service, permission flow per API level, QR encode/decode (ZXing core), deep link; a debug-menu toggle "Multiplayer (experimental)" and a debug screen "Host / Join" with the raw roster and event log | 5–8 d | JVM: frame size and the transport adapter against a fake `ConnectionsClient`; **2 physical phones** (Pixel + any Play phone): join, 100 intents back and forth, screen off for 5 min, airplane mode toggle, host app kill → resume; permission flows on API 29/31/33+ (one device each, or a borrowed one). Emulators can't do this |
| **M3 UX** | Design pass first (share sheet, join, "Who are you · Who do you scan", badges, waiting states, banners), then: Home "Join a game", the share icon + menu item, the share sheet, the guest round screen variants (hidden actions, highlights and badges, Scan next on own players), pending ✓, refusal dialogs, joined history rows, EN + DE strings, TalkBack labels ("Ben, scanned by Anna's phone") | 6–10 d | Screenshot matrix: new `debug_seed` scenarios (`mp_host`, `mp_guest`, `mp_waiting`, `mp_offline`) that drive an in-process `FakeNetwork`, so no radio is needed; Compose UI tests for hidden guest actions; font 2.0 and 21:9 profiles; TalkBack pass |
| **M4 Field test with 2–4 phones** | A real game night: 1 host + 1–3 guests, mixed OEMs if possible (Pixel + Samsung) | 2–3 d + fixes | Script: share → 3 joins by QR within 1 min; everyone scans their own row in parallel × 5 rounds; one guest scans a neighbour; a conflicting double scan; guest screen off / out of range mid-round; host app killed mid-round → resume; late join in round 3; remove a phone; Same players, new game; battery drain per hour (target < 8 %); note every latency > 1 s |

Total ≈ **17–27 focused days**. M1 + M2 are the technical risk. M3 is mostly reuse of existing composables.

---

## 9. Spike on this branch (device-free)

Package `app/app/src/main/java/net/moesli/scan7/multiplayer/` is plain Kotlin with no Android dependencies, not wired into the UI, and has no Play services:
- `GameAction.kt`: sealed `@Serializable` actions → `GameState` mutations, plus `touches()` for the stale check and `hostOnly`.
- `Snapshot.kt`: wire DTOs (`GameSnapshot`, `GameDto`, …) that round-trip exactly to `GameState`.
- `Protocol.kt`: messages, `PROTOCOL_VERSION`, and `WireCodec` (versioned frame, gzip, frozen hello).
- `GuestRules.kt`: guest permissions (shared by the host and the guest UI), `DeviceInfo`, `HOST_DEVICE`.
- `HostSession.kt`: the single writer (intents → checks → apply → seq'd event broadcast), claims and roster, remove device, `newGame`, `updateGame`.
- `ClientSession.kt`: the mirror (`StateFlow<ClientView>`), outbox and resend, gap → resync, `mayEdit` / `mayPerform`.
- `Transport.kt` + `FakeTransport.kt`: transport interfaces, and an in-memory star network with per-link FIFO, random interleaving across links, drops and lost frames.

Tests (`app/app/src/test/.../multiplayer/`, 22, all green): 3 guests + host score a round concurrently and converge (50 random interleavings); reconnect after a drop gets a snapshot and sends the outbox; a late joiner gets the game; a retried intent is applied once; a stale double scan is refused with its author, and a replace by another guest is refused while the host can replace it; a scan for a closed round doesn't land in the next round; a guest can't close a round, end the game, reopen, adjust or remove totals, or reorder seats; a guest edits only its own entries of the running round; claim conflict → ask → forced take-over, and badges survive a drop and rejoin; Same players, new game keeps guests connected and their claims; a player removed mid-game drops claims and entries (and an added one shows up); a removed device can't rejoin; protocol version mismatch and a wrong token are rejected; a lost event triggers a resync; a rejoin from a new endpoint replaces the old link; codec round-trips, frame size, version gate and malformed input.
