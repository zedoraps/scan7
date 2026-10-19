# Scan7 – Multiple phones in one game (spec)

> Spec, 2026-10-19. Status: **merged into main as a beta feature** (Share this game / Join a game, both labelled BETA; DB v6). M1–M3 are built and JVM-tested; a first field test on 2 phones (Pixel 10 Pro, Android 17 + Pixel 8 Pro, Android 16) is done and its fixes are in (§8, M4). Still open: the remaining proposals in §0.2, the design pass (§8), a full game night with 3–4 phones and older API levels.
> Build: the flavorless default is the Play build with Nearby; `-Pscan7.foss=true` builds without Play services (no multiplayer, F-Droid). Field-test log: `adb logcat -s Scan7Nearby Scan7Frames` (every Nearby call / callback and every game message as JSON).
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
15. **A guest never replaces a value that is already scanned**, not even for a player it claimed. Only the host can. A guest may change only what that phone entered itself, in the running round (rule 6).
16. **The host's "Scan next" skips players a guest has claimed.** It walks the unclaimed players plus the host's own.
17. **Phones are named only after people:** "Philip's phone" ("Philips Handy"), taken from the phone's "me" player. There are no device names.
18. **Guests can save a joined game's players to their own players list** (U17).
19. **A phone may join without a player of its own ("Just helping").** It must then enter a name, which names the phone ("Grandma's phone"). At most **11 phones**: the host + 10 guests, helpers included.
20. **Guests' training scans stay on the guest phone**, under that phone's own debug setting.
21. **Test hardware:** 2–3 real phones are available later. Until then, everything is covered by unit tests with fakes (FakeNetwork, a fake `ConnectionsApi`).
22. **The host always plays in practice.** If it hasn't picked a player, its label stays **"Host"**. There is no helper name for the host.
23. **"Save players" matches by name only** (trimmed, ignoring case). The avatar doesn't count.
24. **When multiplayer ships, the `play` flavor becomes the default build** (`installPlayDebug`, …). The **`foss` flavor without Play services stays buildable.** **Done:** the flavorless default is the Play build; `-Pscan7.foss=true` (or `-Pscan7.flavors=true` → `foss`) builds without Play services.

### 0.2 Proposals (please confirm or change)
- **P1. One screen for steps 3 + 4**, "Who are you? · Who do you scan?". "Me" is preselected as scanned. You can add extra players, e.g. a neighbour without a phone. "Just helping" asks for a name instead. A player another device already scans shows that device's badge. Claiming them anyway asks first ("Take over Cleo from Anna's phone?"), and then they move to you. The same goes for "I am Anna" when another phone already is Anna (e.g. a new phone).
- **P2. "Share this game"** sits in the round menu (Actions group) and as a small icon in the round header (host only). It opens a **share sheet** with the QR, the connected devices (who they are, whom they scan, online or offline) and **Remove** per device (confirmation, destructive style). The sheet also has **Stop sharing**.
- **P3. Conflicts:** the host decides, and its DB is the single source of truth. If two devices score the same player, the later one gets **"Already scored by Ben's phone (+30)"** with only **OK** (decided: guests never replace; the host can fix it). The host can always replace any entry.
- **P4. Guest restrictions are hidden, not greyed out:** Close round, Next round, End game, Adjust total, Rearrange seats, target and game settings. When everyone is done, the guest's bottom bar shows **"Waiting for <host> to close the round"** (text, not a button). Edit, Rescan and Reset to open show only on the guest's own entries of the running round. Other entries show "Scored by <device>".
- **P5. Host leaves or its phone dies:** guests see a non-blocking banner, "Connection lost · reconnecting…". They can keep scanning, and their scans wait in an outbox and are sent on reconnect. When the host app comes back, the same session resumes on its own. **Host migration is out of scope for v1.**
- **P6. "Scan next"** on a guest walks only its own claimed players. (The host side is decided, see rule 16.)
- **P7. Badges:** the own players get a cyan outline plus a small "YOU SCAN" tag on the card or row. Other devices' players get a small phone badge with that device's owner avatar (initial). This needs a design pass (handoff rule).
- **P8. Leaving:** "Leave game" (guest round menu) releases that phone's claims. A plain disconnect keeps them, so the badge goes to "offline" and the claims come back on rejoin.
- **P9. Guest scoreboard:** read-only, including closed rounds (no reopen or edit).

### 0.3 Open questions
None at the moment (all answered, see rules 22–24).

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
| U17 | guest | to save the joined game's players to my own players list | "Save players…" lists the players I don't have yet (matched by name), all ticked. Save adds them with their avatars |
| U18 | helper without a player (e.g. Grandma keeps score for the kids) | to join and scan others | "Just helping" + a name. My phone shows as "Grandma's phone", and I claim the players I scan |

---

## 2. UX per screen (minimal, reuse first)

Everything new uses existing components: `Scan7ConfirmDialog`, the M3 bottom sheet, `PlayerAvatar`, the round cards and rows, the camera chrome and the snackbar or toast. **New screens and badges need a design pass with the user before M3 is built** (DESIGN_HANDOFF rule).

**Home**
- A new tertiary text button **"Join a game"** goes under the pinned **Players · New game** row. It opens the join flow.
- The Resume card of a shared game shows "Shared · 3 phones". On a guest it shows "Joined · Philip's phone" while the session lives.
- In Past games, joined games show the tag **"Joined"**. Tapping one opens the read-only scoreboard, and they can be deleted like any other game.
- **Save players (U17):** the joined game's scoreboard and its long-press menu get **"Save players to my list"**. A sheet lists the players this phone doesn't have yet (the existing player rows with avatars, all ticked), and **Save** adds them. If none are new, it says "All players are already in your list".

**Join flow (guest, new)**
1. **QR scanner:** the existing camera chrome (← back, title "Scan the QR on the host's phone", no shutter). Decoding uses ZXing core (Apache-2.0, pure Java) on CameraX frames. A `scan7://join?...` deep link also works from the system camera.
2. **Permission rationale** (one line + Continue), then the system **"Nearby devices"** prompt (§6.3). On denial: "Scan7 needs Nearby devices to join · Open settings".
3. "Connecting…", then **Who are you? · Who do you scan?** (P1). This reuses the New game player list (avatar rows). The top section, "I am", is a single choice plus **"Just helping"**, which shows a name field (required, max 24 characters: "Your name"). The second section, "I scan", is multi-select with me preselected. Rows claimed by another phone show its badge. **Done** → round screen.
- **Naming everywhere:** a phone is "<me>'s phone" (DE "<me>s Handy"), a helper "<name>'s phone", and the host before it picked a player "Host". A phone that hasn't picked yet shows "Joining…". No device names are shown.

**Round screen: host**
- Header: a small **share icon** next to ⋮ (filled while sharing, with the count of phones). Round menu → Actions → **Share this game**.
- **Share sheet:** big QR + short code, then the devices (owner avatar, name, "scans: avatars…", ● online / ○ offline, ✕ Remove → confirm "Remove Anna's phone?" destructive), then **Stop sharing** (confirm).
- Cards and rows of guest-claimed players carry the device badge. A remote result flashes "by Anna's phone" for 2 s.
- **Scan next** on the host skips guest-claimed players (rule 16). When only guest players are open, the bottom shows "Waiting for Anna's phone, Ben's phone" + **Close round**.
- Close round: if a guest still has an open player, the existing confirmation adds "Anna's phone is still scanning Ben."

**Round screen: guest** (the same composables, fewer actions)
- Header: logo · Table|List · ⋮. The guest's ⋮ holds: Scoreboard, Scan direction, **Who do you scan?**, **Leave game** (confirm). Rearrange seats and End game are left out.
- Own players are highlighted (P7). Others' players have badges.
- Bottom: **Scan next · <my next open player>** (P6). Close round is left out. When all my players are done, **Scan next** gives way to "Waiting for Philip to close the round" (muted text), or to "All your players are done" while others are still open.
- Player sheet: an open player has Scan · Enter manually · Bust. My own done entry has Edit · Rescan · Reset to open. Someone else's entry shows the result + "Scored by Ben's phone", read-only. Adjust total is left out.
- Banner states: "Connection lost · reconnecting…" (non-blocking); after 20 s without a connection "Not connected · this is only a copy · Join again"; "Removed by the host" (blocking, → Home; the phone can rejoin by scanning the QR).

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
| Replace a value another phone or the host entered (even for a claimed player) | ✅ | ❌ ("Already scored by …", OK only) |
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

### 4.1 Host-authoritative event log + snapshot (built)
- The **host's Room DB is the source of truth.** Guests never write game state. They send **intents** (`Intent{intentId, baseSeq, action: GameAction}`).
- `GameAction` is a sealed, `@Serializable` wire form of the `GameState` mutations:
  - **player actions:** `ScoreCards`, `ScorePoints`, `Bust`, `UndoBust`, `ResetToOpen`;
  - **host-only:** `AdjustTotal`, `RemoveAdjustment`, `RestoreAdjustment`, `CloseRound`, `NextRound`, `CloseRoundAndStartNext`, `ReopenRound`, `EndGame`, `SetTarget`, `ReorderSeats`, `SwapSeats`, `MoveSeat`, `MovePosition`, `ChangeLineup`.
  
  Player actions carry an **explicit round**, so a late intent never lands in the next round.
- **One action path on every phone.** `GameRepository.apply(gameId) { state -> GameAction }` handles every game change the UI makes. Each existing method (`scoreCards`, `bust`, `closeRoundAndStartNext`, `changePlayers`, …) is now a one-liner on top of it, so the screens didn't change. Without a session, the action goes to `applyLocal`: one transaction, and it returns the stored state, so new adjustments come back with their row ids. While the game is shared, a registered `ActionRouter` takes the action instead:
  - **Host:** `HostSession.perform`. The session's `store` is `applyLocal`, so the host's own taps and the guests' intents take the same path. The event then carries the ids the host's database assigned (`withAssignedIds`).
  - **Guest:** the action is mapped to host ids and sent as an intent (§4.5). The copy changes only when the event arrives; the caller gets an optimistic state, so "Scan next" can move on.
- The host numbers every applied action with a gap-free **`seq`** and broadcasts `Event{seq, at, action, intentId, by}`. **Guests mirror** it by running the same `GameAction.apply(state, at, seq)`. A seq gap or an apply failure → `ResyncRequest` → `Snapshot`. Every (re)join gets a `Welcome` with the full snapshot, which is ≈ 7 KB gzipped for 10 players × 30 rounds.
- `GameState.scanOnly` (not stored, filled in by the repository like `scanDirection`) restricts "Scan next" per phone: a guest walks its own players, the host walks everyone no guest scans.

### 4.2 Conflict handling (in order, per intent)
1. **Duplicate** `intentId` (a retry after a reconnect): answered with the original outcome (`IntentAck` or the same refusal). It is never applied twice.
2. **Host-only** action → `NOT_ALLOWED`.
3. **Stale:** the result the intent targets changed after the guest's `baseSeq` (another phone scored that player, or the round was closed) → `STALE` with `author` ("Already scored by Ben's phone"). `baseSeq` is the seq the guest saw when the user **started** the action (opened the camera), not when it was sent.
4. **Guest rules** (§3): older round, or another device's entry → `NOT_ALLOWED` with `author`.
5. **`GameState` refuses** it (game over, unknown player, nothing to undo) → `INVALID`.

There are no merges and no CRDT. Order of arrival at the host decides, and every refusal is shown to the user (P3).

### 4.3 Claims ("who do you scan")
- The host keeps a **roster**: `DeviceInfo{deviceId, me, helperName, scans, connected}`, with the host itself as `"host"`. It broadcasts `Roster` on every change (join, drop, claim, leave, remove). The UI names a phone with `DeviceInfo.label(players)` (`OfPlayer(name)`, `Helper(name)`, `Host` or `Joining`). `ShareText.phone()` turns that into the localized name: "Philip's phone", "Philips Handy", and "Klaus’ Handy" after s, ß, x or z.
- `Claim{me, scans, helperName, force}`:
  - `me = null` means "just helping" and needs a `helperName` (`ClaimProblem.HELPER_NAME_MISSING`); an unknown `me` is `UNKNOWN_PLAYER`.
  - Players another device scans, or a `me` another phone already is → `ClaimRejected{conflicts}`, and nothing changes. The UI asks, then resends with `force = true`, which moves the players (and the `me`).
  - The host's own claims always win; the host picks its "me" on the share sheet.
- **Scan next:** `ScanOrder.next(state, roster, device)` decides it (rule 16), and the app applies it through `GameState.scanOnly`.
- Claims are **UI guidance only**, not permissions. Any guest may score an open player (rule 4). Badges, highlights and Scan next come from the roster.
- Claims survive a disconnect (shown offline) and a rejoin (same `deviceId`). "Leave game" (`leave` message) releases them (P8).
- **Limit:** 10 guest phones (`MAX_GUEST_DEVICES`) + the host = 11. Helpers count too. The 11th guest gets `SESSION_FULL`, while a known phone rejoining is always let in.

### 4.4 Identity
- **`deviceId`:** a random UUID per install (`GameRepository.deviceId()`, settings table). It recognises a returning phone on a new transport endpoint, and the old link is dropped. It is never shown; phones are named after people (rule 17).
- **Player ids** in the protocol are the **host's** Room ids, valid inside the session. The snapshot and `ChangeLineup` carry `PlayerDto{id, name, avatar}`. A guest keeps a host → local id map (`mapPlayerIds` on states and actions).
- **Shared game id:** `sessionId:hostGameId` (`games.gameUid` on the guest's copy). A rejoin updates the same copy; "Same players, new game" makes a new copy.
- **Adjustment ids:** the host's database assigns them, and the event carries that id (`AdjustTotal.id`, `RestoreAdjustment.id`), so "remove adjustment" addresses the same adjustment on every phone. The guest's copy keeps its own row ids and matches adjustments by content. No extra column was needed.

### 4.5 Offline-first and persistence (built: DB v6)
- **DB v6** (AutoMigration 5 → 6, schema `app/schemas/…/6.json`, `MigrationTest.v5ToV6_…` in androidTest):
  - `games.joinedFrom` (the host's name; `null` = this phone's game) and `games.gameUid`;
  - `players.joinedOnly` (hidden guest stand-ins);
  - table `share_sessions(gameId, role HOST|GUEST, sessionId, token, stateJson, updatedAt)`.
  
  Joined games are excluded from "the game to resume", from "abandon other unfinished games", from previous-seating lookups and from the Players list. They are included in the history, even while unfinished ("Joined · Philip").
- **Host:** game state as today. `HostSessionState` (seq, roster, removed phones, entry authors, the last 1024 intent outcomes) is saved in `share_sessions` after each change. Sharing the same game again (e.g. after an app restart) **resumes the same session**: same QR, nothing applied twice, claims kept, older intents stale (`floorSeq`).
- **Guest:** `ShareRuntime` writes the `ClientSession` mirror into a **read-only joined copy** (`saveJoinedGame`), using hidden local players (`joinedPlayers`). `applyLocal` refuses joined games; only the session's router changes them, and the view mode stays per phone. `share_sessions` holds the outbox, intent counter and id map (`GuestSaved`), so a scan made offline survives an app restart. "Save players to my list" turns hidden players into normal ones (`keepJoinedPlayers`, matched by name).

### 4.6 Lifecycles
- **Late join / reconnect:** `Hello{lastSeq}` → `Welcome{seq, snapshot, roster}` → the guest resends its outbox (same ids).
- **Guest drop:** the host marks it offline and keeps its claims. The Nearby client transport rediscovers the same session with backoff (1 s … 30 s) and says hello again.
- **Host app killed / phone dies:** guests show the banner (P5) and keep queueing, and their phones keep a full copy. When the host shares again, the session resumes from `share_sessions` and guests reconnect. If the host phone is gone for good, the guests keep their read-only history copy. Host migration is not in v1.
- **Same players, new game:** `ShareRuntime.hostMovedTo(newGameId)` → `HostSession.newGame`. The seq continues, staleness and authors reset, and claims are kept for players still in the game. Each guest gets a new joined copy and is moved to its round screen.
- **Stop sharing / remove a phone:** removed phones get `HelloRejected(REMOVED)` and are disconnected; they can't rejoin this session. Stop sharing drops the session row, so the next share is a new QR.

### 4.7 Managing players during a game (built on main's `withLineup`)
- Adding or removing players goes through main's Players flow (`changePlayers` / `addPlayerToGame` / `removePlayerFromGame`), which is now the host-only `ChangeLineup` action on `GameState.withLineup`. The event carries the new players' names and avatars. A player who already played stays in the scoreboard as `DepartedPlayer`.
- A **removed** player's claims (`me` or `scans`) are dropped on every phone, and so are their entry authors. Intents still in flight for them are `STALE`, or `INVALID` once the guest saw the change ("Cleo is no longer in this game").
- An **added** player appears unclaimed, so the host scans them by default and any guest can claim them.

### 4.8 Threading
Sessions are plain Kotlin and not thread-safe. `ShareRuntime` runs all of them on one session thread (`scan7-share`). The radio transports and their timers post to it, the repository routers switch to it, and the UI observes `ShareRuntime.state` plus the usual Room flows. The host's store calls Room with `runBlocking` on that thread; Room works on its own executors, so this can't deadlock.

---

## 5. Protocol

### 5.1 Framing
The transport payload is one BYTES payload per message: `[format byte][body]`. Format `0` = UTF-8 JSON, `1` = gzipped JSON (used from 4 KB). The body is `{"v": <protocol>, "m": {"type": "...", ...}}`, encoded with kotlinx.serialization (already a dependency: polymorphic sealed classes, `classDiscriminator = "type"`, `ignoreUnknownKeys`). The design limit is **32 KB per frame**. Nearby's own limit is about 1 MB, and a measured 10-player, 30-round snapshot is about 7 KB gzipped.

### 5.2 Messages
```
guest → host                                  host → guest
hello{sessionId, token, deviceId, lastSeq?}   welcome{sessionId, seq, snapshot, roster}
                                   [frozen]   hello_rejected{reason, hostProtocol}   [frozen]
intent{intentId, baseSeq, action}             event{seq, at, action, intentId?, by?}
claim{me?, scans[], helperName?, force}       intent_ack{intentId, seq}          (duplicate)
resync{lastSeq}                               intent_rejected{intentId, reason, detail?, author?}
leave{}                                       snapshot{seq, snapshot}
                                              roster{devices[]}
                                              claim_rejected{conflicts[{playerId, deviceId, isMe}], problem?}
snapshot = {game: GameDto, players: [PlayerDto], authors: [{round, playerId, deviceId}]}
```
`reason`: join `VERSION_MISMATCH | BAD_TOKEN | WRONG_SESSION | SESSION_FULL | REMOVED`, intent `STALE | NOT_ALLOWED | INVALID`; claim `problem`: `HELPER_NAME_MISSING | UNKNOWN_PLAYER`.

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
- Strategy **`P2P_STAR`**: the host advertises, guests discover. The service id is `<applicationId>.mp` (`nearbyServiceId`; debug suffixes give separate ids, so a debug build and a release build never mix). Only BYTES payloads are used.
- **Names on the Nearby layer** (`JoinAuth`): the host advertises `s7|<sessionId>` (short, because it goes into BLE advertisements). The guest ignores every other name, so other Scan7 games nearby are skipped. It requests the connection as `j1|<nonce>|<HMAC-SHA256(token, "scan7-join|sessionId|nonce")>`. The host **rejects requests without a valid proof** before any game data flows, and accepts the rest automatically (no "compare the codes" dialog). The token never goes over the air in clear, and it is checked again in `hello`.
- **Owned interface:** `ConnectionsApi` (`multiplayer/nearby/`) is the slice of `ConnectionsClient` we use (advertise, discover, request, accept/reject, send BYTES, disconnect, lifecycle callbacks). `GmsConnectionsApi` (`src/play/`) forwards it 1:1 and maps status codes to `ApiError`. All logic lives in plain Kotlin:
  - `NearbyHostTransport` / `NearbyClientTransport` implement `HostTransport` / `ClientTransport`.
  - `FrameChunker` splits frames above the BYTES limit (9-byte header: magic, message id, index, count) and drops lost, foreign or oversized frames. The session then resyncs.
  - The client transport **keeps its link**: after a drop it reports `onDisconnected`, rediscovers with backoff (1, 2, 4, 8, 16, 30 s; a connection attempt times out after 30 s) and reports `onConnected` again, so `ClientSession` sends a fresh `hello`. An `onEndpointLost` before the handshake also retries. A connection the host rejected is not retried.
  - The `missingPermissions` check runs before Nearby is touched, and `MISSING_PERMISSION` from Nearby shows as `TransportStatus.MissingPermissions` (no retry loop).
  - Graceful disconnects wait 300 ms, so `hello_rejected` or `leave` frames still get out.
  - Callbacks are expected on one thread (Nearby: the main thread).
- **Foreground service** while hosting or joined (`foregroundServiceType="connectedDevice"`, notification "Scan7 is sharing a game"), so screen-off and the camera don't kill the link. "Keep screen on" already applies on the round and camera screens.
- Disconnects: Nearby `onDisconnected` → the transport's `onPeerDisconnected`. The guest reconnects with exponential backoff (1, 2, 4 … 30 s). The host sends `hello_rejected` and waits about 300 ms before `disconnectFromEndpoint`, so the frame is not lost.

### 6.2 QR content
`scan7://join?v=1&s=<sessionId>&t=<token, 128-bit base64url>` (`JoinLink`), shown with ZXing core. The short code on the share sheet is the first 6 characters of the session id; it is a fallback for a broken camera and goes with a "Nearby games" list. Joining by short code asks the host "Let Anna's phone join?" because the token is missing.

### 6.3 Permissions (requested only on Share or Join, with a one-line rationale)
| API level | Runtime permissions Nearby needs | Manifest |
|---|---|---|
| all | `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` (field test: Play services Nearby refuses discovery without both even on Android 17, status 8034 / 8036; on 10–11 the location setting must be on) | `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` on **all** API levels (Nearby checks them, 8032); location without `maxSdkVersion` |
| 29–30 (Android 10–11) | location only | `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) |
| 31–32 (Android 12 / 12L) | + `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` | — |
| 33+ (Android 13+) | + `NEARBY_WIFI_DEVICES` | — |
| 37 (Android 17, our target) | + `ACCESS_LOCAL_NETWORK` for the Wi-Fi LAN medium (declared) | — |
| all (M3) | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` (34+), `POST_NOTIFICATIONS` (33+, optional) | — |

This is implemented as `NearbyPermissions.required(sdk)` / `missing(sdk, isGranted)` / `rationale(sdk)` and unit-tested per API level. The `play` manifest (`src/play/AndroidManifest.xml`) follows the Nearby get-started guide.

On 12+ the radio permissions show as **one "Nearby devices" dialog**, followed by the location dialog (precise). Location needs a clear rationale on every API level ("Android needs location access to find nearby phones; Scan7 never uses your location"). The exact list is checked against the current [Nearby get-started guide](https://developers.google.com/nearby/connections/android/get-started) when M2 starts.

### 6.4 Dependency, flavors, license
- `com.google.android.gms:play-services-nearby` is **proprietary** (Play services) and needs Play services on the phone (excludes de-Googled ROMs and Huawei).
- **Builds:** the flavorless default is the **Play build** (`com.google.android.gms:play-services-nearby:19.5.1` + `GmsConnectionsApi` + the permission manifest from `src/play/`), with the usual task names (`assembleDebug`, `installDebug`, `testDebugUnitTest`, `lintDebug`). `-Pscan7.foss=true` compiles `src/foss/` instead: no Play services, Share and Join hidden. `-Pscan7.flavors=true` instead adds the dimension `distribution` with `play` (default) and `foss`; the tasks then carry the flavor (`assemblePlayDebug`, `testFossDebugUnitTest`, …). Everything except `GmsConnectionsApi` is plain Kotlin in `main`, so all the tests run in every build. F-Droid (optional, docs/RELEASE.md) builds `foss`. The user decided multiplayer isn't an F-Droid target.
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
- **Join token:** 128 random bits per share session, only in the QR. It is compared in constant time in `hello`; a wrong token → `BAD_TOKEN` + disconnect. Stop sharing invalidates it. Removing a phone disconnects it without a ban: it can rejoin by scanning the QR again.
- **Data exchanged:** player **names + avatar ids**, seats, results (status, points, **card labels**), adjustments (delta + note), helper names, a random per-install device id, and timestamps. Device models and device names are never sent. **No photos, no training JPEGs, no other games, no player stats.** Guests store the joined game (and those players) only as their read-only history copy, unless they save the players to their own list (U17).
- **Input from guests is untrusted:** frames are size-limited (32 KB); malformed or unknown messages are dropped; every intent is validated by `GameState` and `GuestRules`; a guest can't name another guest's `deviceId` (the host fills `by` from the link).
- `PRIVATE_*` photo rules are unaffected (nothing image-like is sent).

---

## 8. Milestones

| | Scope | Status | Test plan |
|---|---|---|---|
| **M1 Protocol + session layer** | `GameAction` + DTOs + codec, `HostSession` / `ClientSession`, `GuestRules`, `ScanOrder`, claims + roster + labels, leave / remove / the 11-phone limit, new game, lineup changes, export / restore, `PlayerImport`, `FakeNetwork` | **Done** | JVM: `MultiplayerSessionTest`, `WireCodecTest`, `HostStoreTest` |
| **M2 Nearby transport (no device)** | Owned `ConnectionsApi`, Nearby host / client transports, `FrameChunker`, `JoinLink` / `JoinAuth`, `NearbyPermissions`, opt-in `foss` / `play` flavors | **Done** (`GmsConnectionsApi` not run on hardware yet) | JVM: `NearbyTransportTest`, `NearbyPartsTest` |
| **M3 Wiring + UX** | **Wiring:** merge of main (DB v5, strings, round menu); `GameRepository.apply` + `ActionRouter`; new actions; DB v6 + migration; `ShareRuntime` (session thread, host store, guest joined copy, saved sessions, `hostMovedTo`); `RadioSupport` (foss: none, also the default build; play: Nearby + `ShareService` foreground service `connectedDevice`, notification, `scan7://join` deep link, permissions manifest); permission rationale + request; ZXing QR encode + CameraX / ZXing QR reader. **UI** (existing components): Home "Join a game"; header share icon + menu row + share sheet (QR, code, "You are", phones with claims, online / offline, remove, stop sharing); "Who are you · Who do you scan" sheet (+ "Just helping" name, ask before taking over); guest round screen (host actions hidden, own players highlighted, other phones' badges, Scan next over own players, waiting line, connection banner, refusal dialog, sheet "Scored by …" read-only, no Adjust total); guests follow the host to game over and into the next game; joined history rows + read-only scoreboard + "Save players to my list"; EN + DE strings; Debug → "Multiplayer (experimental)" in-process demo | **Built, JVM-tested, merged into main as beta**; build default flipped (Play default, `-Pscan7.foss=true`). Still open: the **design pass** (below); screenshot-matrix scenarios; a Compose test for the Join screen (camera); TalkBack pass; a joined copy opened with no session and no radio still shows Scan buttons that fail silently (hide them) | JVM (Robolectric 4.16): `RepositoryActionPathTest` (Room), `ShareRuntimeTest` (host + in-process guest through the real repository calls: claims, badges, Scan next, refusals, round close, adjustments, removed player, Same players new game, leave), `RoundShareTest`, `RoundScreenShareUiTest` + `ClaimSheetUiTest` + `ShareSheetUiTest` (Compose on the JVM), `ShareTextTest`; androidTest: `MigrationTest.v5ToV6` (needs an emulator) |
| **M4 Field test with 2–4 phones** | A real game night with 1 host + 1–3 guests, mixed OEMs if possible. The first run of `GmsConnectionsApi` + `ShareService` on hardware | **2-phone test done** (Pixel 10 Pro, Android 17 host / guest + Pixel 8 Pro, Android 16). Fixes from it: Wi-Fi state permissions on all API levels, coarse + fine location on all API levels, `ACCESS_LOCAL_NETWORK` declared; connect timeout 30 s; rejoin routing; the host resumes sharing automatically (same QR) after a restart. Open: 3–4 phones, other OEMs, API 29–31 | See "Verify on devices" below |

**Design pass (handoff rule, before shipping):**
- the share sheet layout and QR framing;
- the badge style (`ShareBadge`: cyan phone = mine, phone + initial = another phone; corner and size);
- the guest waiting line and connection banner;
- the claim sheet (radio + checkbox lists);
- the "Joined · Philip" history detail;
- the share icon in the header next to ⋮;
- the Home "Join a game" text button;
- the refusal dialog. It currently reuses `Scan7ConfirmDialog`, so it shows Cancel + OK; a single-button info dialog would be cleaner.

**Behaviour confirmed in the field test:** the host shares a QR; guests join, pick who they are and whom they scan; a host restart resumes sharing with the same code; guests rejoin automatically; a guest that can't reconnect within 20 s shows "Not connected · this is only a copy · Join again"; removing a phone disconnects it without a ban (it can rejoin by scanning).

**Still to verify on devices (Nearby doesn't run between emulators):**
1. Debug build on one phone: Debug → Multiplayer → host the running game → join as a virtual guest → open both round screens. Then check claims, badges, guest limits, waiting line, refusal dialog, Same players new game, leave and the joined history row. No radio needed.
2. `migration`: `./gradlew connectedDebugAndroidTest --tests '*MigrationTest*'` on an emulator (v5 → v6).
3. Play build (default `installDebug`) on 3–4 phones (2 done):
   - share → permission rationale → system dialog; the notification appears;
   - QR join from Scan7's Join screen **and** from the system camera (deep link);
   - parallel scans, screen off for 5 min, airplane-mode toggle, host app killed → share again (same QR);
   - remove a phone; an 11th phone gets "game full".
4. API 29/30 (location rationale + location setting), 31, 33 (16 + 17 done; `ACCESS_LOCAL_NETWORK` is declared).
5. `ShareService` stays alive with the screen off and the camera open; battery per hour.

---

## 9. Where the code lives

- **`multiplayer/`** (plain Kotlin): protocol and sessions (M1), id mapping (`IdMapping.kt`), session state (`SessionState.kt`).
- **`multiplayer/nearby/`** (plain Kotlin): the Nearby adapter (M2).
- **`share/`**: `ShareRuntime` (sessions on the phone), `RadioSupport`, `ShareText`.
  - `src/play/…/share/Radio.kt` + `ShareService.kt` + `multiplayer/nearby/GmsConnectionsApi.kt` + `AndroidManifest.xml`: Nearby; compiled into the flavorless default build.
  - `src/foss/…/share/Radio.kt`: no radio (`-Pscan7.foss=true` or the `foss` flavor).
- **`ui/share/`**: share sheet, claim sheet, refusal dialog, Join screen (QR reader), permission request, save-players dialog.
- **`ui/round/RoundShare.kt`**: the round-screen mapping, banner and waiting line. Small hooks went into the round screen, header, menu, player sheet, cards / rows (`ShareMark` / `ShareBadge`), Home, history, scoreboard, game over and navigation (`Route.Join`, `DebugScreen.MULTIPLAYER`).
- **`data/`**: action path + routers, DB v6, joined copies, share sessions, device id.
- **`debug/MultiplayerDemoScreen.kt`**: the in-process demo.

**Builds checked (before the default flip; re-check `-Pscan7.foss=true` too):**
- the flavorless `assembleDebug testDebugUnitTest lintDebug`;
- with `-Pscan7.flavors=true`: `assembleDebug` (foss + play), `testFossDebugUnitTest`, `testPlayDebugUnitTest`, `lintFossDebug`, `lintPlayDebug`.

Only the Play build contains Nearby, the service and the deep link.
