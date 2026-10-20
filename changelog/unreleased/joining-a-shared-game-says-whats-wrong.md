---
title: Joining a shared game says what's wrong
type: bugfix
created: 2026-10-20T09:28:32.406285Z
---

When a phone can't join a shared game, the Join screen now says why and shows the button to fix it: Location switched off (Android needs it to find nearby phones; Scan7 never stores or shares your location), a missing permission, Google Play services refusing although the permission is granted, or the host's phone not found yet. Bluetooth or Wi-Fi switched off shows a hint, and the phone keeps trying. It no longer flickers between "Connecting" and "permissions" and no longer shows "Connecting…" forever. Tap "Technical details" on the Join screen or the share sheet to see what the radio did, and copy the log to send it along. The log stays on your phone.
