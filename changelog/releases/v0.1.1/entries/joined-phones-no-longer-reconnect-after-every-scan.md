---
title: Joined phones no longer reconnect after every scan
type: bugfix
created: 2026-10-19T13:05:59.149039Z
---

In a shared game, a joined phone opened a new connection to the host about every scan and the host then dropped the old one, so scans could wait for the reconnect. A joined phone now keeps its one connection, and the host keeps one connection per phone.
