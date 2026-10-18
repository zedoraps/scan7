# Scan7 – Multiple phones in one game (research + proposal)

> Research note, 2026-10-18. Status: **proposal, nothing decided or built.** Open questions for the user are at the end.
> Context: [APP_FLOW.md](APP_FLOW.md), [../AGENTS.md](../AGENTS.md). The app is `minSdk 29`, **`targetSdk 37` (Android 17)**, which matters for local networking (see §2.4).

## TL;DR

- **Yes, both are possible fully locally**, with no online service and no account: (a) a phone or tablet as a **second screen** (live scoreboard / round view), and (b) **several phones scanning different players** of the same round into one game.
- **Architecture: host-authoritative.** One phone (the host) owns the game in its Room DB. Other devices send **commands** ("score Ben in round 3 with these cards"). The host applies them through the existing `GameRepository`/`GameState` code and broadcasts an ordered **event log + snapshots**. No CRDT is needed: a card table always has one obvious owner, and Scan7's game logic is already a pure function `GameState -> GameState` (`GameRepository.mutate`).
- **Transport recommendation: Google Nearby Connections (`P2P_STAR`) for the MVP**. It is zero-config, works offline without a router (Bluetooth/BLE discovery, upgrade to Wi-Fi), and is encrypted. Joining is by **QR code**, with the nearby list as a fallback. Keep the protocol behind a small `SessionTransport` interface so a **LAN + WebSocket** transport can be added later. That later transport covers iOS and lets a browser show the scoreboard as a viewer.
- **MVP plan:** Phase 0 makes game actions serializable commands (stable IDs). Phase 1 is a read-only **viewer**. Phase 2 adds **scanner clients** with player locks. Phase 3 adds resume and host-loss handling. Phase 4 (optional) adds LAN/WebSocket and a web or iOS viewer. Rough effort is about 3–5 weeks of focused work for phases 0–3.

---

## 1. What the user wants, in protocol terms

| Use case | Data flow | Needs |
|---|---|---|
| **Second screen** (tablet on the table, or a guest's phone) | host → viewers: snapshot, then deltas | one-way, small JSON (< 5 KB per update), latency < 1 s, survives screen-off/reconnect |
| **Multiple scanners** | scanner → host: commands; host → everyone: events | ordered, reliable, idempotent commands; conflict handling when two phones touch the same player; host stays the single writer to Room |
| **Late join** | host → new device: full snapshot (players, seats, rounds, results, adjustments) | snapshot of about 10–30 KB for a long game |

Bandwidth is tiny. A scan result is the list of confirmed cards (≤ ~20 cards), not the photo. **The camera frame and the training JPEG stay on the scanning phone** (training capture keeps working locally; see §5). So throughput is irrelevant for choosing a transport. What matters is **setup UX, offline robustness, permissions and iOS**.

---

## 2. Technologies

### 2.1 Google Nearby Connections (Play services)
- **What:** a peer-to-peer API that "combines Bluetooth, BLE and Wi-Fi", works "regardless of network connectivity", and is **fully encrypted**. Both sides accept the connection (a symmetric auth flow with an auth token that can be compared). [Overview](https://developers.google.com/nearby/connections/overview)
- **Strategies:** `P2P_CLUSTER` (M-to-N mesh, lower bandwidth, "multiplayer gaming"), `P2P_STAR` (one hub with N spokes, higher bandwidth), and `P2P_POINT_TO_POINT` (1:1, highest throughput). [Strategies](https://developers.google.com/nearby/connections/strategies). Scan7 maps directly onto **`P2P_STAR`**: host = hub, scanners and viewers = spokes. Range is about 100 m. The docs give no fixed spoke count; in practice a table of up to 10 players is well within what people run.
- **Payloads:** `BYTES` (one chunk), `FILE`, `STREAM`. Payloads of the same type arrive **in order**. [Exchange data](https://developers.google.com/nearby/connections/android/exchange-data). The BYTES limit is documented inconsistently: the overview says "32k", while the reference `MAX_BYTES_DATA_SIZE` is 1,047,552 bytes ([ConnectionsClient reference](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/ConnectionsClient)). **Design for ≤ 32 KB per message** and chunk or compress large snapshots.
- **Permissions** (from the [Android get-started guide](https://developers.google.com/nearby/connections/android/get-started)):
  - API 29–31: `ACCESS_FINE_LOCATION`
  - API 31+: `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT`
  - API 32+: `NEARBY_WIFI_DEVICES`
  - **API 37+: `ACCESS_LOCAL_NETWORK`** (Android 17, for the `WIFI_LAN` medium)

  The API refuses to advertise or discover if any of them is denied. On Android 12+ these permissions mostly fall into one **"Nearby devices"** prompt.
- **Requirements:** Google Play services (fine on the Pixel and almost all phones; not on de-Googled ROMs or Huawei). The Play services SDK collects analytics, which users can turn off in Settings → Google → Usage & diagnostics ([overview](https://developers.google.com/nearby/connections/overview)). That is a privacy caveat for a "local only" promise. No internet is needed at runtime.
- **iOS:** Google ships a [Swift library](https://developers.google.com/nearby/connections/swift/get-started) from [google/nearby](https://github.com/google/nearby), which needs `NSBluetoothAlwaysUsageDescription` and `NSLocalNetworkUsageDescription`. Its maturity is doubtful: a maintainer reports that BLE between iOS devices works but **iOS↔Android has discovery/connection issues**, and the BLE v2 flag is not production-ready ([issue #1619](https://github.com/google/nearby/issues/1619)). The Swift package registry entry lists only a 2021 v0.0.1 ([registry](https://swiftpackageregistry.com/google/nearby)). Apple's own MultipeerConnectivity shares **no wire protocol** with Nearby, so an iPhone using it never sees an Android phone ([Codename One notes](https://codenameone.com/javadoc/com/codename1/nearby/transport/package-summary)). **Treat Nearby as Android-only.**
- **Pros:** zero-config (no shared Wi-Fi, no IPs), works in a basement or at a cottage, encrypted, the medium upgrade is handled for you, and the dependency is small.
- **Cons:** a closed-source Play services black box with OEM-specific quirks. Bluetooth plus Wi-Fi radios have to be on. Android-only in practice. Connection setup takes a few seconds.

### 2.2 Plain Bluetooth: Classic RFCOMM and BLE GATT
- **Permissions:** Android 12+ needs `BLUETOOTH_SCAN` (with `neverForLocation`), `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT`, all runtime ("Nearby devices" prompt). ≤ Android 11 needs `ACCESS_FINE_LOCATION`. [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- **Classic RFCOMM:** stream sockets with roughly 1–2 Mbit/s. In practice this needs **pairing or a discoverability dialog** ("Make discoverable for 120 s?"), and a server phone holds about 7 links at most (piconet). The UX is poor for guests.
- **BLE GATT:** the host runs a GATT server and phones connect as centrals. Throughput is phone-dependent. A benchmark with 2M PHY and DLE reached about 1.2 Mbit/s to Pixel/Galaxy phones, but only about 0.3 Mbit/s to iPhones ([Infineon benchmark](https://community.infineon.com/t5/ModusToolbox-Blog/BLE-Throughput-Rate-Benchmark-Test-for-Cypress-BT-SoCs/bc-p/267238)). Nordic's model gives about 256 kbit/s at a 7.5 ms interval ([Nordic SoftDevice docs](https://infocenter.nordicsemi.com/topic/sds_s140/SDS/s1xx/ble_data_throughput/ble_data_throughput.html)). That is plenty for Scan7. The concurrent-connection limit isn't documented and depends on the stack and chip (commonly cited as about 7). You must build MTU negotiation, chunking, reliability and reconnects yourself. Throughput also drops when Wi-Fi is busy ([Nordic DevZone](https://devzone.nordicsemi.com/f/nordic-q-a/85258/comunicating-at-least-300-kbp-s-via-ble-nrf5340-with-android-phones/357443)).
- **iOS:** BLE GATT is the one radio protocol that iOS *does* speak natively (CoreBluetooth, as central or peripheral). It is therefore the only offline radio route to iOS, but it costs the most to build well.
- **Verdict:** Nearby Connections already wraps BLE/BT and does this work for us. Raw BLE only makes sense if offline iOS support becomes a hard requirement.

### 2.3 Wi-Fi Direct (Wi-Fi P2P) and Wi-Fi Aware (NAN)
- **Wi-Fi Direct:** a group owner (GO) plus clients, with ordinary sockets on top. It needs `NEARBY_WIFI_DEVICES` (API 33+) or `ACCESS_FINE_LOCATION` (≤ 32), and **Location Mode must be on** for `discoverPeers()` ([Wi-Fi P2P guide](https://developer.android.com/develop/connectivity/wifi/wifip2p)). Known UX quirks: a system invitation dialog on the other device, flaky GO negotiation across OEMs, discovery that stops, and conflicts with an active hotspot. **No iOS support.**
- **Wi-Fi Aware (NAN):** since API 26. It is **optional hardware** (check `FEATURE_WIFI_AWARE` + `isAvailable()`), and some devices can't run it while Wi-Fi Direct, hotspot or tethering is active. Data paths and sessions are limited (`getAvailableAwareResources()`), and discovery messages are about 255 bytes and unreliable. The same `NEARBY_WIFI_DEVICES` permission applies. [Wi-Fi Aware guide](https://developer.android.com/develop/connectivity/wifi/wifi-aware). Pixels support it; many mid-range phones don't. iOS 26 added a Wi-Fi Aware framework, but mainly for EU interoperability with accessories; I found no evidence of phone-to-phone interoperability with Android. Treat it as unproven.
- **Verdict:** both are lower-level than Nearby (which can use them internally) and give no benefit for Scan7's tiny payloads.

### 2.4 Same Wi-Fi LAN: NSD/mDNS discovery + small server on the host (Ktor / WebSocket)
- **How:** the host runs an embedded server (Ktor CIO + WebSockets) on a port, announces `_scan7._tcp` via `NsdManager`, and clients connect with a WebSocket. The QR can carry `ws://192.168.x.y:port/?s=<session>&k=<key>` directly, so no discovery is needed at all.
- **Android 17 caveat (we target SDK 37):** local network protection is **enforced for apps targeting API 37**. Outgoing TCP, **accepting incoming TCP**, all UDP/multicast and `.local` mDNS resolution need **`ACCESS_LOCAL_NETWORK`** (runtime, in the `NEARBY_DEVICES` group; no second prompt if Bluetooth/nearby was already granted). Alternatively, the **NSD system picker** (`DiscoveryRequest.FLAG_SHOW_PICKER`) grants access to the one chosen device without the permission. Blocked TCP shows up as **timeouts** and UDP as `EPERM`. [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission). So the host needs the permission (it accepts connections), and clients need it or the picker.
- **No router (host hotspot):** `WifiManager.startLocalOnlyHotspot()` creates a hotspot with **no internet access**. The system picks the SSID and passphrase (readable via `getSoftApConfiguration()`, API 30+). It needs `NEARBY_WIFI_DEVICES` (target 33+). [LocalOnlyHotspot](https://developer.android.com/develop/connectivity/wifi/localonlyhotspot). The host can show a **Wi-Fi QR** (`WIFI:T:WPA;S:…;P:…;;`) that the system camera can join, then a second QR or a deep link for the session. Quirks: the guest's phone leaves its own Wi-Fi, may warn "no internet" and loses mobile data on some phones, and some chipsets can't run the hotspot and a Wi-Fi client together. This is clunkier than Nearby.
- **iOS:** this path works natively. `URLSessionWebSocketTask` or the Network framework connects to the host, browsing needs `NSLocalNetworkUsageDescription` + `NSBonjourServices`, and there is a known flaky "Local Network" prompt with no API to query its state ([Apple forums](https://developer.apple.com/forums/thread/653316), [MASTG](https://mas.owasp.org/MASTG/knowledge/ios/MASVS-PLATFORM/MASTG-KNOW-0128/)).
- **Bonus:** the same Ktor server can serve a small **web scoreboard** (`http://host:port/`). Any tablet, laptop or iPhone could then be a **viewer with no app install**, by scanning the QR. Browsers are adding their own local network access prompts, so test that.
- **Pros:** open, debuggable (curl, browser), cross-platform, and the server/WebSocket is plain Kotlin that can be tested on the JVM.
- **Cons:** needs a shared Wi-Fi (guests are often not on the host's Wi-Fi) or the hotspot dance. Guest Wi-Fi with client isolation silently breaks it. More moving parts (server lifecycle in a foreground service, IP changes).

### 2.5 QR-code joining (works with any transport)
The host shows a QR containing `scan7://join?v=1&s=<sessionId>&k=<base64 32-byte key>&h=<host endpoint name>[&ws=<url>]`. The joiner scans it with Scan7's own CameraX (or the system camera via a deep link).
- With **Nearby**, the joiner discovers only the endpoint whose name matches `h`/`s` and auto-accepts, so there is no list and no "compare these codes" step. After connecting, it proves knowledge of `k` with an HMAC challenge (Nearby already encrypts; the key adds *authorization*).
- With **LAN**, the QR carries the URL directly, so there is no mDNS step (which also sidesteps flaky mDNS).
- Fallback without a camera, or for a tablet with a bad camera: a "Nearby games" list plus a 4-digit code shown on the host.

### 2.6 Online options (fallback, brief)
| Option | What it adds | Cost / limits | Privacy |
|---|---|---|---|
| **Firebase Realtime DB / Firestore** | No host phone needed; works across networks (a remote player, a TV at home); built-in offline cache | Spark free tier: RTDB 100 concurrent connections, 1 GB, 10 GB/month; Firestore 50K reads / 20K writes per day ([pricing](https://firebase.google.com/pricing)). Plenty for game nights | Data at Google; needs anonymous auth + security rules; the app gets an internet dependency and a project to maintain |
| **Supabase Realtime** | Same, Postgres-based, open source | Free: 500 MB, 200 concurrent realtime connections, 2M messages/month, **paused after 1 week of inactivity** ([pricing](https://supabase.com/pricing)), which is bad for occasional game nights | Hosted (EU region possible) or self-hosted |
| **Self-hosted relay** (a tiny WebSocket relay on a VPS / Cloudflare Worker + Durable Object) | Same protocol as the LAN transport, just relayed; can be end-to-end encrypted with the QR key, so the relay sees only ciphertext | about €0–5/month; you run it | Best of the online options if E2E-encrypted |

The online options solve "no host phone" and "different rooms", which nobody asked for. **Not recommended for now.** The relay is the natural later extension because it reuses the same protocol and key.

### 2.7 Comparison

| | Offline (no internet) | No router needed | Range | Throughput | Setup UX for a guest | Android requirements | iOS | Effort / complexity |
|---|---|---|---|---|---|---|---|---|
| **Nearby Connections (P2P_STAR)** | ✅ | ✅ | ~100 m | high (BLE → Wi-Fi upgrade) | ★★★ scan QR → connected (one "Nearby devices" prompt) | Play services; BT + Wi-Fi on; Nearby/BT/Wi-Fi perms; `ACCESS_LOCAL_NETWORK` on 17 | ⚠️ Google Swift lib exists, Android interop unreliable | **Low–medium** |
| **BLE GATT (raw)** | ✅ | ✅ | ~10–30 m indoors | ~0.2–1.2 Mbit/s | ★★ scan → connect, no pairing needed | BT perms (12+), location ≤ 11 | ✅ CoreBluetooth | High (MTU, chunking, reliability) |
| **Bluetooth Classic RFCOMM** | ✅ | ✅ | ~10 m | ~1–2 Mbit/s | ★ pairing/discoverable dialogs | BT perms | ❌ (MFi only) | Medium; ~7 links |
| **Wi-Fi Direct** | ✅ | ✅ | ~50 m | high | ★ system invite dialogs, flaky across OEMs | `NEARBY_WIFI_DEVICES` + Location Mode on | ❌ | Medium–high |
| **Wi-Fi Aware (NAN)** | ✅ | ✅ | ~50 m | high | ★★ when supported | `FEATURE_WIFI_AWARE` (not on many phones), `NEARBY_WIFI_DEVICES` | ❓ iOS 26 framework, interop unproven | High |
| **LAN + NSD + WebSocket (Ktor)** | ✅ (LAN only) | ❌ (shared Wi-Fi) | Wi-Fi coverage | high | ★★ if on the same Wi-Fi: scan QR → connected | `ACCESS_LOCAL_NETWORK` (17) or NSD picker | ✅ native + **browser viewer** | **Medium** (server + lifecycle) |
| **LAN via host LocalOnlyHotspot** | ✅ | ✅ | ~30 m | high | ★ join hotspot (QR) + session QR; leaves own Wi-Fi | `NEARBY_WIFI_DEVICES`; chipset concurrency | ✅ (joins Wi-Fi via QR) | Medium–high |
| **Firebase / Supabase** | ❌ | n/a | global | fine | ★★★ (QR = room id) | internet | ✅ | Low–medium + backend ops |
| **Self-hosted relay** | ❌ | n/a | global | fine | ★★★ | internet | ✅ | Medium + a server |

---

## 3. Architecture

### 3.1 Host-authoritative vs. replicated state
| | **Host-authoritative** (recommended) | Peer-replicated (CRDT / merged event log) |
|---|---|---|
| Source of truth | Host's Room DB, unchanged schema | Every device; merge on reconnect |
| Fit with existing code | Direct: commands map 1:1 to `GameRepository` methods (`scoreCards`, `bust`, `undoBust`, `resetToOpen`, `adjustTotal`, `removeAdjustment`, `closeRound`, `nextRound`, `reopenRound`, `endGame`, …), which already run `GameState` transitions inside `mutate()` | Needs commutative ops or vector clocks; `closeRound` (marks every open player BUST) and game-over are inherently order-dependent |
| Conflicts | Host decides by arrival order + version checks | Merge rules for every field; surprising results ("Ben scored twice") |
| Host leaves | Game pauses for clients (see §3.5) | Keeps going on all devices |
| Effort | Low–medium | High |

A game round at one table has a natural single owner. **Go host-authoritative.** The P2P_STAR topology (or a WebSocket server) matches it exactly.

### 3.2 Identity: needed groundwork (Phase 0)
Room IDs are local `Long` autoincrements, so they are not meaningful on another phone. For the session:
- Give every game a **`gameUid` (UUID)** and use **session-scoped player refs**: the host's `playerId` is sent along with name + colorIndex. Clients keep **no** copy in their own player list (optional "save a copy of this game" later; see open questions).
- Results already key on `(roundNumber, playerId)`. Adjustments get a `uid` so removal can address them across devices.
- Introduce a serializable **`GameCommand`** sealed class (kotlinx.serialization) in plain Kotlin under `game/`, with one `GameRepository.apply(gameId, cmd)` that dispatches to the existing methods. Local UI can use the same path, so single-device and multi-device take one code path. This is fully JVM-testable.

### 3.3 Protocol (transport-independent)
JSON messages (≤ 32 KB), a version field, and a thin `SessionTransport` interface (`send(peer, bytes)`, `broadcast(bytes)`, `events: Flow<PeerEvent>`) with a Nearby implementation and, later, a WebSocket one.

```
client → host
  Hello{protocol:1, deviceName, role:viewer|scanner, hmac(k, nonce), lastSeq?}
  Command{cmdId: UUID, baseSeq, expect?: {round, playerId, resultVersion}, cmd: GameCommand}
  Lock{playerId, round} / Unlock{playerId}        // scanner "I'm scanning Ben"
  Ping

host → client
  Welcome{sessionId, yourPeerId, role, snapshot: GameSnapshotDto, seq}
  Event{seq, cmdId?, by: peerId|host, event: GameEvent}   // applied, ordered
  Reject{cmdId, reason: STALE|LOCKED|ROUND_CLOSED|NOT_ALLOWED, current?}
  Locks{map playerId -> peerId+expiresAt}
  Snapshot{seq, …}                                       // on resume gap / periodically
```

- **Sequence numbers:** the host increments `seq` per applied event and persists `seq` with the game. A client that sees a gap asks for `Resume{lastSeq}`. The host replays events from a bounded in-memory/Room log, or just sends a fresh snapshot (simpler: **snapshot on any gap**, because a snapshot is small).
- **Idempotency:** the host remembers recent `cmdId`s, so a retried command after a reconnect is acknowledged, not applied twice.
- **Events = commands as applied** plus derived effects (e.g. `RoundClosed{bustedPlayers}`), so viewers can animate "Ben BUST" without diffing. Clients can also simply re-render from snapshot + event application using the same `GameState` code.

### 3.4 Conflict rules
1. **Locks first, as a soft lease (UX):** when a scanner opens the camera for Ben, it sends `Lock(Ben)`. The host grants it if it is free (lease of 90 s, renewed while the camera is open; released on OK/back/disconnect). Other devices show "**Ben · being scanned by Anna's phone**" and their Scan button asks "Take over?". The host's own UI can always break a lock.
2. **Optimistic check on write (correctness):** `Command.expect.resultVersion` (e.g. the result's `updatedAt` or a per-result counter). If the result changed since the scanner looked, the host replies `Reject(STALE, current)`, and the scanner shows "Ben was already scored +46 by Max. Replace with +38?" → resend without `expect`. **Last confirmed write wins, but never silently.**
3. **Round boundaries:** a command for a round that the host has since closed is rejected (`ROUND_CLOSED`). The client offers "Apply to round N (closed, reopens totals)", which maps to the existing edit-closed-round path.
4. **Close round / Next round / End game / Reopen / seating / target** are **host-only** in the MVP (one person runs the game). The host sees a warning if any lock is active: "Anna is still scanning Ben".
5. **Undo** (bust undo toast, adjustment removal) is just another command with an `expect` check.

### 3.5 Reconnect, late join, host loss
- **Late join / reconnect:** `Hello{lastSeq}` → `Welcome{snapshot}`. A viewer that comes back after screen-off is instantly current. Nearby connections drop when the app is backgrounded for long; the host should run a **foreground service** while hosting (also needed so the host can keep serving while its own camera is open).
- **Client offline queue:** a scanner that loses the link mid-scan keeps the confirmed result in an outbox and sends it on reconnect (idempotent `cmdId`, `expect` check → may get STALE).
- **Host leaves or crashes:** clients show "Host disconnected – reconnecting…" and keep retrying for a while. The game is safe on the host's disk (Room) and resumes when the host app returns (re-advertise the same session id and key, so clients auto-reconnect). **No automatic host migration in the MVP.** Phase 3 option: "**Continue on this phone**" lets a client turn its latest snapshot into a local game (new `gameUid`, forked) and optionally re-host it. Merging the fork back is out of scope.

### 3.6 Roles
| Role | Can | UI |
|---|---|---|
| **Host** | everything (as today) + start/stop sharing, see who is connected, kick, break locks | Round menu ⋮ → **Share game…** shows the QR + list of connected devices |
| **Scanner** | scan / enter manually / bust / edit *assigned or any open* players (setting on the host: "Scanners may edit: any player · only open players · only assigned"), adjust total (optional, see questions) | Same round screen; lock badges; host-only actions hidden or disabled; a header pill "Joined · Philip's Pixel" |
| **Viewer** | read only | Big scoreboard / round view (landscape, tablet-friendly, keep-screen-on), auto-follows round changes and the game-over screen |

Optional "assigned players": the host taps a player → "Scanned by: Anna's phone". It is a soft default for *Scan next* on that phone (Scan next cycles only through its assigned open players), not a hard permission.

---

## 4. UX sketch

- **Host:** ⋮ → **Share game** → sheet with a large QR, a "Viewer / Scanner" toggle (the QR encodes the role or the joiner picks one), a short code for manual entry, and the connected-devices list (name, role, ● online). Sharing starts a foreground service (persistent notification "Scan7 is sharing a game").
- **Joiner:** Home → **Join game** → camera QR scanner (CameraX is already in the app; ML Kit barcode or ZXing) → one "Nearby devices" permission prompt → "Joined Philip's game · Round 3". Fallback: "Nearby games" list + 4-digit code.
- **Round screen on a scanner:** identical look. A player card being scanned elsewhere shows a small device chip + "scanning…". The card shows "✓ +46 · by Anna" briefly when a remote result arrives.
- **Disconnect:** non-blocking banner "Reconnecting to Philip's phone…". Commands taken meanwhile are shown as "pending" (hollow ✓).
- Per the design-handoff rule, **these new screens need a design pass with the user** before building (Share sheet, Join scanner, viewer layout, lock chip).

## 5. Security & privacy
- **Local only:** no account, no server, no internet. Nearby is encrypted on the link. For a LAN transport, add TLS or, simpler, **encrypt and authenticate each message with the session key from the QR** (e.g. AES-GCM/ChaCha20 via Tink or `javax.crypto`), so other devices on the same Wi-Fi can't read or inject messages.
- **Session key** (32 random bytes) per share session, only in the QR (or a short code + PAKE if the manual-code path must be secure; for a card game, a 4-digit code with a host-side "Accept Anna's phone?" confirmation is enough). Rotating the key on "Stop sharing" ends all access.
- **Data shared:** player names + colors, results, cards. **No photos**: training JPEGs stay on the device that took them (each phone's own "Save scans for training" setting applies). The `PRIVATE_*` rules are unaffected.
- **Nearby caveat:** the Play services SDK sends usage analytics unless the user disables it ([overview](https://developers.google.com/nearby/connections/overview)). Mention this honestly if "local only" is advertised.
- **Permissions:** only requested when the user taps Share/Join (never at startup), with a rationale screen. On Android 17 the Nearby/Bluetooth and local-network permissions sit in one "Nearby devices" group.

## 6. Recommendation

**MVP: Nearby Connections `P2P_STAR` + QR join + host-authoritative command/event protocol behind a `SessionTransport` interface.**

Rationale:
1. **The game is played at a table, often away from home.** Nearby works with no router, no shared Wi-Fi and no internet; LAN needs a common Wi-Fi or the clumsy hotspot dance. Zero-config wins for guests.
2. **The permission cost is about the same either way on Android 17**: LAN needs `ACCESS_LOCAL_NETWORK` too (in the same "Nearby devices" group), so LAN isn't simpler on our target SDK.
3. **The payloads are tiny**, so Nearby's medium upgrades and the 32 KB limit don't matter.
4. **iOS is the one real argument for LAN/WebSocket.** Nearby's iOS interop is unreliable, so the protocol stays transport-agnostic, and a **LAN/WebSocket transport + browser viewer** is Phase 4 if iPhones or a non-Android tablet need to join. Raw BLE (the only offline radio path to iOS) is not worth it.

**Flip condition:** if the main wish is "*a tablet/laptop/iPhone as a scoreboard without installing anything*", then start with **LAN + Ktor WebSocket + a web viewer page** instead. The command/event design stays identical.

## 7. Phases & effort (rough, one developer with agents; days = focused working days)

| Phase | Scope | Effort |
|---|---|---|
| **0 – Groundwork** | `GameCommand`/`GameEvent` (kotlinx.serialization), `GameRepository.apply`, `gameUid`, adjustment uid, `seq` in the games table (DB v4 migration + MigrationTest), snapshot DTO; JVM tests for command → state | 2–4 d |
| **1 – Viewer (second screen)** | `SessionTransport` + Nearby impl, host foreground service, Share sheet with QR, Join with QR scanner, HMAC hello, snapshot + events, read-only viewer screen (reuse round/scoreboard composables), reconnect banner; test on Pixel + emulator/second phone | 5–8 d |
| **2 – Multiple scanners** | Scanner role, commands from the client round screen/scan flow, locks + lease UI, STALE/ROUND_CLOSED handling, outbox, host-only actions, "by Anna" attribution, assigned players for Scan next | 6–10 d |
| **3 – Robustness** | Resume after host restart (same session), kick/stop sharing, "Continue on this phone" fork, multi-OEM testing (Samsung/Xiaomi quirks), battery/screen-off behaviour | 3–6 d |
| **4 – optional** | LAN/WebSocket transport (Ktor CIO) + web viewer page, encrypted messages with the session key; later iOS viewer/scanner or E2E relay | 5–10 d |

**MVP = Phases 0 + 1** (about 1.5–2.5 weeks) gives a live second screen. **Phase 2** adds multiple scanners (about 1.5 weeks more).

## 8. Risks
- **Nearby OEM quirks / black box:** connection drops, slow discovery on some brands, background limits. Mitigations: foreground service, snapshot-on-reconnect, a transport interface so LAN can replace it.
- **Testing needs ≥ 2 physical devices.** Emulators don't do Nearby radios well. Today there is one Pixel test phone, so a second Android phone or tablet is needed.
- **Android 17 local-network permission** if/when we add LAN: test TCP timeouts vs. denial carefully ([docs](https://developer.android.com/privacy-and-security/local-network-permission)).
- **Scope creep in conflict UX:** keep host-only control for round/game-level actions in the MVP.
- **Design handoff:** new screens aren't in `DESIGN_HANDOFF.md`, so they need agreement before UI work.
- **Play services dependency** excludes de-Googled phones and adds the analytics caveat.
- **iOS later:** a Nearby-only app can't add iPhones without a second transport. This is accepted, given the plan above.

## 9. Open questions for the user
1. **Who will join?** Only Android phones (yours + friends'), or also **iPhones / an iPad / a laptop browser**? This decides Nearby-first vs. LAN-first (§6 flip condition).
2. **Where do you play?** Mostly at home with shared Wi-Fi, or also away (cottage, train, a friend's place)?
3. **Priority:** is the **second screen** (Phase 1) enough at first, or is **multi-scanner** the real goal?
4. **Scanner permissions:** may scanners edit any player, only open ones, or only assigned ones? May they adjust totals, bust, reopen? Or is the host the only one who closes rounds (proposed)?
5. **Guest data:** should a joined phone keep a copy of the finished game (and the players) in its own history, or forget it when the session ends?
6. **Host leaving:** is "game pauses until the host is back" acceptable, or do you want "continue on this phone" (a fork) in the MVP?
7. **Is the Play services analytics caveat OK** for a "local only" promise, or must it be strictly open-source and local (→ LAN/WebSocket)?
8. **Test hardware:** is a second Android device available for development?
