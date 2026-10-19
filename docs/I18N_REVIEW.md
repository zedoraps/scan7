# German strings review (in context)

Every string id of `values/strings.xml` / `values-de/strings.xml`: where it is used (screen + role,
found by grepping `R.string.*` / `R.plurals.*`), English, German and the review note. Each German text
was read in its screen (screenshots `i18n2_<screen>_{en,de}.png`). Terms: **Runde**, **Spieler**,
**Punkte / PKT**, **RAUS**, **Tabelle**, **Korrektur**; tone "du"; buttons are
imperatives / verbs, headers nouns.

## Open questions for the user

1. ✅ **Decided (user, 2026-10-18): "Startseite".** **Home**: "Startseite" (round menu, round logo, Game over button). Short alternative: "Start" (can read as "start a game" next to "Neues Spiel") or "Übersicht".
2. ✅ **Decided: "Tabelle" everywhere (title, menu, links).** **Scoreboard link / toast action**: "Tabelle" vs. the full title "Punktetabelle" (longer in the toast).
3. ✅ **Decided: "Spieler".** **Player**: the app says "Spieler" (generic masculine) everywhere. Gender-neutral "Spieler:in" would make headers and TalkBack longer. Keep "Spieler"?
4. ✅ **Decided: "↻ Uhrzeigersinn" / "↺ Gegenuhrzeigersinn".** **Scan direction segments**: "↻ Uhrzeigersinn" / "↺ Gegenrichtung". OK, or "↺ Andersrum" (more casual)?
5. ✅ **Decided: "Bonuskarten".** **Modifier cards**: "Bonuskarten" for +2 … +10 and ×2. Some players say "Modifikatoren".
6. ✅ **Decided: "IGNORIERT" (TalkBack "ignoriert", action "ignorieren").** **Excluded box tag**: "WEG" (very short). Alternative "RAUS" is taken by bust; "AUSGELASSEN" is long.

## Strings

| id | screen (role) | EN | DE | note |
|---|---|---|---|---|
| `app_name` | Wordmark (text) | Scan7 | = EN (not translated: proper name) | |
| `bust_word` | Confirm dialogs (text) | BUST | RAUS | BUST → RAUS everywhere (tags, dialogs, toasts, scoreboard). |
| `label_bust` | Review (text); Review / manual (confirm button, bust overlay) (text); Scoreboard (text); Shared component (text) | BUST | RAUS | |
| `label_bust_tag` | Camera (text); Player sheet / adjust (text); Shared component (text) | ✕ BUST | ✕ RAUS | |
| `action_back` | Shared component (TalkBack) | Back | Zurück | |
| `action_cancel` | Confirm dialogs (text); Manual entry (button); Player sheet / adjust (button) | Cancel | Abbrechen | |
| `action_delete` | Confirm dialogs (TalkBack); Players / editor (button) | Delete | Löschen | |
| `action_save` | Manual entry (button); Players / editor (button) | Save | Speichern | |
| `action_reset` | Confirm dialogs (TalkBack) | Reset | Zurücksetzen | |
| `action_start` | Confirm dialogs (TalkBack) | Start | Starten | |
| `action_next` | New game / Seats (button) | Next | Weiter | |
| `action_done` | Round (button) | Done | Fertig | |
| `action_edit` | Player sheet / adjust (button); Players / editor (TalkBack); Scoreboard (TalkBack) | Edit | Bearbeiten | |
| `action_undo` | Player sheet / adjust (button); Round (toast) | Undo | Rückgängig | |
| `action_remove` | Manual entry (TalkBack) | Remove | Entfernen | |
| `action_home` | Round (button); Round (text) | Home | Startseite | Changed "Start" → "Startseite": round ⋮ menu row and round logo; "Start" next to "Spiel beenden" read like "start". |
| `action_menu` | Round (TalkBack) | Menu | Menü | |
| `action_scoreboard` | Game over (button); Round (toast) | Scoreboard | Tabelle | "Tabelle" everywhere (user). |
| `action_new_game` | Game over (text); Home (button) | New game | Neues Spiel | |
| `action_end_game` | Confirm dialogs (TalkBack); Round (button) | End game | Spiel beenden | |
| `action_close_round` | Confirm dialogs (TalkBack); Shared component (TalkBack); Shared component (button) | Close round | Runde beenden | |
| `action_close_round_two_lines` | Shared component (button) | Close / round | Runde / beenden | |
| `action_scan` | Player sheet / adjust (text) | Scan | Scannen | |
| `action_scan_player` | Player sheet / adjust (text); Shared component (TalkBack); Shared component (text) | Scan %1$s | %1$s scannen | |
| `action_bust_player` | Camera (TalkBack); Player sheet / adjust (text); Shared component (TalkBack) | Mark %1$s as bust | %1$s als raus markieren | Taken from the release agent: an action label ("als raus markieren"), not a statement. |
| `action_undo_bust_player` | Review / manual (confirm button, bust overlay) (TalkBack); Round (toast) | Undo bust for %1$s | Raus für %1$s rückgängig | |
| `action_enter_manually` | Player sheet / adjust (button); Review (button) | Enter manually | Von Hand eingeben | |
| `action_rearrange` | Round (button); Round (text); Shared component (TalkBack) | Rearrange seats / order | Plätze / Reihenfolge ändern | |
| `label_scan_next` | Shared component (text) | SCAN NEXT | WEITER SCANNEN | "WEITER SCANNEN" kept (shorter than "NÄCHSTEN SCANNEN"; same term as the menu hint). |
| `action_scan_next_player` | Shared component (text field) | Scan next: %1$s | Als Nächstes scannen: %1$s | Taken from the release agent ("Als Nächstes scannen"). |
| `view_table` | Shared component (TalkBack) | Table view | Tischansicht | |
| `view_list` | Shared component (TalkBack) | List view | Listenansicht | |
| `names_and` | Name lists (shared) (text) | %1$s and %2$s | %1$s und %2$s | |
| `points_short` | Camera (text); Home (text) | PTS | PKT | |
| `points` | Camera (text); Game over (TalkBack); Game over (text); Home (text); Manual entry (text); New game / Seats (TalkBack); Player sheet / adjust (text); Review (text); Scoreboard (TalkBack); Shared component (text) | one: %d point | other: %d points | one: %d Punkt | other: %d Punkte | |
| `points_with` | Game over (TalkBack); Home (TalkBack) | one: %d point | other: %d points | one: %d Punkt | other: %d Punkten | New (UX fix): dative after "mit" ("mit 88 Punkten"). |
| `rounds` | Game over (header); Home (text); Scoreboard (header) | one: %d round | other: %d rounds | one: %d Runde | other: %d Runden | |
| `target_short` | Home (header) | to %1$d | bis %1$d | |
| `round_label` | Home (text); Round (text) | ROUND | RUNDE | |
| `round_number` | Round (text); Scoreboard (text) | Round %1$d | Runde %1$d | |
| `error_save` | Players / editor (text) | Couldn't save | Speichern hat nicht geklappt | |
| `error_delete` | Players / editor (text) | Couldn't delete | Löschen hat nicht geklappt | |
| `tag_open` | Shared component (text) | OPEN | OFFEN | |
| `tag_scored` | Shared component (text) | ✓ +%1$d | ✓ +%1$d | |
| `status_open` | Shared component (text) | Not scored yet | Noch nicht gewertet | |
| `status_scored` | Shared component (text) | Scored %1$d | %1$d Punkte | |
| `status_bust` | Shared component (text) | Bust | Raus | |
| `progress_scored` | Shared component (text) | one: %1$d of %2$d scored · to %3$d | other: %1$d of %2$d scored · to %3$d | one: %1$d von %2$d fertig · bis %3$d | other: %1$d von %2$d fertig · bis %3$d | |
| `language_title` | Home (text) | Language | Sprache | |
| `language_system` | Home ⋮ language (text) | System default | Systemsprache | |
| `language_english` | Home ⋮ language (text) | English | = EN (not translated: proper name) | |
| `language_german` | Home ⋮ language (text) | Deutsch | = EN (not translated: proper name) | |
| `confirm_delete_player_title` | Confirm dialogs (header) | Delete %1$s? | %1$s löschen? | |
| `confirm_delete_player_body` | Confirm dialogs (TalkBack) | Past games keep showing them. | In alten Spielen bleibt der Name sichtbar. | |
| `confirm_reset_title` | Confirm dialogs (header) | Reset the last entry for %1$s? | Letzten Eintrag von %1$s zurücksetzen? | Renamed with the sheet button ("Reset last entry"). |
| `confirm_reset_body_points` | Confirm dialogs (TalkBack) | Removes %1$s from this round. | Entfernt %1$s aus dieser Runde. | |
| `confirm_reset_body_bust` | Confirm dialogs (TalkBack) | Removes the BUST from this round. | Entfernt das RAUS aus dieser Runde. | |
| `confirm_close_round_title` | Confirm dialogs (header) | Close round %1$d? | Runde %1$d beenden? | |
| `confirm_close_round_one` | Confirm dialogs (text) | %1$s hasn't been scored. They'll be marked BUST. | %1$s ist noch nicht gewertet und gilt dann als RAUS. | Changed: "gilt dann als RAUS" (the app marks them). |
| `confirm_close_round_some` | Confirm dialogs (text) | %1$s haven't been scored. They'll be marked BUST. | %1$s sind noch nicht gewertet und gelten dann als RAUS. | Changed: plural verb "gelten". |
| `confirm_close_round_nobody` | Confirm dialogs (text) | Nobody has been scored yet – mark everyone BUST? | Noch niemand ist gewertet – alle auf RAUS setzen? | Changed: question form "alle auf RAUS setzen?". |
| `confirm_end_game_title` | Confirm dialogs (header) | End the game now? | Spiel jetzt beenden? | |
| `confirm_end_game_body` | Confirm dialogs (TalkBack) | The round is closed as it is; open players count as BUST. The leader wins. | Die Runde endet so, wie sie ist; wer noch offen ist, gilt als RAUS. Wer vorne liegt, gewinnt. | Changed: "gilt als RAUS". |
| `confirm_start_game_title` | Confirm dialogs (header) | Start a new game? | Neues Spiel starten? | |
| `confirm_start_game_body` | Confirm dialogs (TalkBack) | The unfinished game (round %1$d) ends and can't be resumed. | Das laufende Spiel (Runde %1$d) endet und lässt sich nicht fortsetzen. | |
| `home_past_games` | Home (header) | Past games | Frühere Spiele | |
| `home_players` | Home (button) | Players | Spieler | |
| `home_debug_action` | Home (TalkBack) | Open debug menu | Debug-Menü öffnen | |
| `home_resume_title` | Home (header) | Resume game | Spiel fortsetzen | |
| `home_resume` | Home (button) | Resume | Weiterspielen | |
| `home_no_points` | Home (text) | No points yet | Noch keine Punkte | |
| `home_leader_one` | Home (text) | %1$s leads · %2$d | %1$s führt · %2$d | |
| `home_leader_many` | Home (text) | %1$s lead · %2$d | %1$s führen · %2$d | |
| `home_resume_description` | Home (TalkBack) | one: Unfinished game, round %2$d, %1$d player, %3$s | other: Unfinished game, round %2$d, %1$d players, %3$s | one: Laufendes Spiel, Runde %2$d, %1$d Spieler, %3$s | other: Laufendes Spiel, Runde %2$d, %1$d Spieler, %3$s | |
| `home_open_scoreboard` | (via UiText / plurals helper) | Open scoreboard | Tabelle öffnen | |
| `home_intro_title` | Home (text) | Scan a row, get the score. | Reihe scannen, fertig gezählt. | Changed: "Punkte haben" sounded odd → "fertig gezählt". |
| `home_intro_players` | Home (text) | Pick who's playing, then point the camera at each player's row of cards. Scan7 adds it up. | Wähl aus, wer mitspielt, und halt die Kamera über die Kartenreihe jedes Spielers. Scan7 rechnet zusammen. | |
| `home_intro_no_players` | Home (text) | No players yet. New game starts with adding everyone at the table — they're saved for next time. | Noch keine Spieler. Bei „Neues Spiel“ legst du alle am Tisch an – sie bleiben fürs nächste Mal gespeichert. | |
| `home_version` | Home (text) | Scan7 %1$s (%2$d) | Scan7 %1$s (%2$d) | Version line in the Home ⋮ menu (no model details; those are on About). |
| `history_today` | Home (text) | Today, %1$s | Heute, %1$s | |
| `history_yesterday` | Home (text) | Yesterday, %1$s | Gestern, %1$s | |
| `history_won` | Home (text) | one: %2$s won | other: %2$s won | one: %2$s hat gewonnen | other: %2$s haben gewonnen | Split into plurals: "Anna und Ben haben gewonnen" (EN "won" works for both, DE does not). |
| `history_led` | Home (text) | one: %2$s led | other: %2$s led | one: %2$s lag vorne | other: %2$s lagen vorne | Split into plurals: "lagen vorne" for a shared lead. |
| `history_won_points` | Home (TalkBack) | one: %2$s won with %3$s | other: %2$s won with %3$s | one: %2$s hat mit %3$s gewonnen | other: %2$s haben mit %3$s gewonnen | New (UX fix), replaces `history_with_points` ("Lena hat gewonnen mit 88 Punkte" → "Lena hat mit 88 Punkten gewonnen"). |
| `history_led_points` | Home (TalkBack) | one: %2$s led with %3$s | other: %2$s led with %3$s | one: %2$s lag mit %3$s vorne | other: %2$s lagen mit %3$s vorne | New (UX fix), abandoned games. |
| `history_not_finished` | Home (text) | Not finished | Nicht beendet | Changed "Nicht fertig gespielt" → "Nicht beendet" (same term on the scoreboard). |
| `history_no_winner` | Home (text) | No winner | Kein Sieger | |
| `history_ended_early` | Home (text) | ended early | vorzeitig beendet | |
| `history_abandoned` | Home (text) | not finished | nicht beendet | Changed to "nicht beendet" (consistent). |
| `history_players` | Home (text) | one: %1$d player: %2$s | other: %1$d players: %2$s | one: %1$d Spieler: %2$s | other: %1$d Spieler: %2$s | |
| `players_title` | Players / editor (header) | Players | Spieler | |
| `players_subtitle` | Players / editor (header) | one: %d stored · tap to edit | other: %d stored · tap to edit | one: %d gespeichert · tippen zum Bearbeiten | other: %d gespeichert · tippen zum Bearbeiten | |
| `players_add` | Players / editor (button) | Add player | Spieler hinzufügen | |
| `players_add_first` | New game / Seats (text); Players / editor (text) | Add your first player | Leg deinen ersten Spieler an | |
| `players_empty_body` | Players / editor (text) | Name and a color. They're saved on this phone for every game. | Name und Farbe. Sie bleiben für jedes Spiel auf diesem Handy gespeichert. | |
| `players_new` | New game / Seats (text); Players / editor (text) | New player | Neuer Spieler | |
| `editor_new_title` | Players / editor (header) | New player | Neuer Spieler | |
| `editor_edit_title` | Players / editor (header) | Edit player | Spieler bearbeiten | |
| `editor_name` | Players / editor (text field) | Name | Name | |
| `editor_name_placeholder` | Players / editor (text field) | e.g. Anna | z. B. Anna | |
| `editor_name_missing` | Players / editor (text) | Enter a name to save | Gib einen Namen ein, um zu speichern | |
| `editor_name_counter` | Players / editor (text) | %1$d/%2$d | %1$d/%2$d | New (UX fix): name counter from 16 of 20 characters. |
| `editor_name_counter_description` | Players / editor (TalkBack) | Characters: %1$d of %2$d | Zeichen: %1$d von %2$d | New (UX fix). |
| `editor_name_counter_full` | Players / editor (TalkBack) | Characters: %1$d of %2$d, limit reached | Zeichen: %1$d von %2$d, Maximum erreicht | New (UX fix). |
| `editor_shuffle` | Players / editor (TalkBack) | Shuffle avatar | Avatar würfeln | |
| `editor_face` | Players / editor (text) | Face | Gesicht | |
| `editor_pattern` | Players / editor (text) | Pattern | Muster | |
| `editor_color` | Players / editor (text) | Color | Farbe | |
| `editor_face_option` | Players / editor (button) | %1$s face | Gesicht %1$s | |
| `editor_pattern_option` | Cards / avatars (shared) (text) | %1$s pattern | Muster %1$s | |
| `editor_no_pattern` | Cards / avatars (shared) (text) | No pattern | Kein Muster | |
| `editor_color_used` | Players / editor (button) | %1$s, used by %2$s | %1$s, schon vergeben an %2$s | Changed: "schon vergeben an Philip" reads naturally in TalkBack. |
| `avatar_description` | Cards / avatars (shared) (TalkBack) | %1$s avatar, %2$s face | Avatar %1$s, %2$s | TalkBack: "Mia, Avatar Sand, grinsend". |
| `avatar_description_named` | Cards / avatars (shared) (TalkBack) | %1$s, %2$s | %1$s, %2$s | |
| `avatar_color_amber` | Cards / avatars (shared) (text) | Amber | Bernstein | |
| `avatar_color_violet` | Cards / avatars (shared) (text) | Violet | Violett | |
| `avatar_color_lime` | Cards / avatars (shared) (text) | Lime | Limette | |
| `avatar_color_sand` | Cards / avatars (shared) (text) | Sand | Sand | |
| `avatar_color_sky` | Cards / avatars (shared) (text) | Sky | Himmel | |
| `avatar_color_mint` | Cards / avatars (shared) (text) | Mint | Minze | |
| `avatar_color_khaki` | Cards / avatars (shared) (text) | Khaki | Khaki | |
| `avatar_color_salmon` | Cards / avatars (shared) (text) | Salmon | Lachs | |
| `avatar_color_steel` | Cards / avatars (shared) (text) | Steel | Stahl | |
| `avatar_color_chartreuse` | Cards / avatars (shared) (text) | Chartreuse | Gelbgrün | |
| `avatar_color_chalk` | Cards / avatars (shared) (text) | Chalk | Kreide | |
| `avatar_color_peach` | Cards / avatars (shared) (text) | Peach | Pfirsich | |
| `avatar_color_rose` | Cards / avatars (shared) (text) | Rose | Rosé | |
| `avatar_color_leaf` | Cards / avatars (shared) (text) | Leaf | Blattgrün | |
| `avatar_color_olive` | Cards / avatars (shared) (text) | Olive | Oliv | |
| `avatar_face_happy` | Cards / avatars (shared) (text) | happy | fröhlich | |
| `avatar_face_cool` | Cards / avatars (shared) (text) | cool | cool | |
| `avatar_face_wink` | Cards / avatars (shared) (text) | wink | zwinkernd | |
| `avatar_face_wow` | Cards / avatars (shared) (text) | wow | staunend | |
| `avatar_face_grin` | Cards / avatars (shared) (text) | grin | grinsend | |
| `avatar_face_sleepy` | Cards / avatars (shared) (text) | sleepy | müde | |
| `avatar_face_cheeky` | Cards / avatars (shared) (text) | cheeky | frech | |
| `avatar_face_focus` | Cards / avatars (shared) (text) | focus | konzentriert | |
| `avatar_face_hearts` | Cards / avatars (shared) (text) | hearts | verliebt | |
| `avatar_face_grumpy` | Cards / avatars (shared) (text) | grumpy | grummelig | |
| `avatar_face_silly` | Cards / avatars (shared) (text) | silly | albern | |
| `avatar_face_dizzy` | Cards / avatars (shared) (text) | dizzy | schwindelig | |
| `avatar_pattern_stripes` | Cards / avatars (shared) (text) | Stripes | Streifen | |
| `avatar_pattern_dots` | Cards / avatars (shared) (text) | Dots | Punkte | |
| `avatar_pattern_zigzag` | Cards / avatars (shared) (text) | Zigzag | Zickzack | |
| `avatar_pattern_grid` | Cards / avatars (shared) (text) | Grid | Gitter | |
| `avatar_pattern_waves` | Cards / avatars (shared) (text) | Waves | Wellen | |
| `newgame_title` | New game / Seats (header) | New game | Neues Spiel | |
| `newgame_subtitle` | New game / Seats (header) | Who's playing? · %1$d of %2$d | Wer spielt mit? · %1$d von %2$d | |
| `newgame_min_players` | New game / Seats (text) | Pick at least %1$d players. | Wähl mindestens %1$d Spieler aus. | Changed: "Wähl … aus". |
| `newgame_max_players` | New game / Seats (text) | %1$d players is the maximum. | Höchstens %1$d Spieler. | Changed: short "Höchstens 10 Spieler." |
| `newgame_no_players` | New game / Seats (text) | No players yet. Add everyone who's at the table — they're saved for next time. | Noch keine Spieler. Leg alle am Tisch an – sie bleiben fürs nächste Mal gespeichert. | |
| `newgame_target` | New game / Seats (header) | Target | Ziel | |
| `newgame_target_description` | New game / Seats (TalkBack) | Target, points to win | Ziel, Punkte zum Sieg | |
| `newgame_target_lower` | New game / Seats (TalkBack) | Lower target | Ziel senken | |
| `newgame_target_raise` | New game / Seats (TalkBack) | Raise target | Ziel erhöhen | |
| `seats_title` | New game / Seats (header) | Seats | Plätze | |
| `seats_hint_table` | New game / Seats (text) | Drag players to where they sit, or just start | Zieh alle an ihren Platz oder starte direkt | Says arranging is optional (no "Skip seats" any more); up to two lines. |
| `seats_hint_list` | New game / Seats (text) | Drag rows into seating order, or just start | Zieh die Zeilen in Sitzreihenfolge oder starte direkt | |
| `seats_start` | New game / Seats (button) | Start game | Spiel starten | |
| `seats_error_start` | New game / Seats (text) | Couldn't start the game | Spielstart hat nicht geklappt | |
| `scoreboard_title` | Round (button); Scoreboard (header) | Scoreboard | Tabelle | user: "Tabelle" |
| `scoreboard_final` | Scoreboard (header) | Final · %1$s · to %2$d | Endstand · %1$s · bis %2$d | |
| `scoreboard_abandoned` | Scoreboard (header) | Not finished · %1$s · to %2$d | Nicht beendet · %1$s · bis %2$d | Changed to "Nicht beendet". |
| `scoreboard_running` | Scoreboard (text) | Round %1$d · to %2$d · tap to edit | Runde %1$d · bis %2$d · tippen zum Ändern | |
| `scoreboard_round_short` | Scoreboard (text) | RND | RUNDE | Changed "RD" → "RUNDE" (fits the 44 dp column, see screenshot). |
| `scoreboard_round` | Scoreboard (text) | Round | Runde | |
| `scoreboard_round_running` | Scoreboard (text) | Round %1$d, running | Runde %1$d, läuft | |
| `scoreboard_now` | Scoreboard (text) | NOW | JETZT | |
| `scoreboard_total_label` | Scoreboard (text) | TOTAL | SUMME | |
| `scoreboard_total` | Scoreboard (text) | Total | Summe | |
| `scoreboard_total_description` | Scoreboard (TalkBack) | %1$s, total %2$d | %1$s, Summe %2$d | |
| `scoreboard_adjusted_legend` | Scoreboard (text) | ± Total includes adjustments | ± Summe enthält Korrekturen | New (UX fix): legend under the table when a total is adjusted (replaces `scoreboard_adjusted`). |
| `a11y_leading` | Scoreboard (text) | , leading | , führt | |
| `scoreboard_cell_points` | Scoreboard (TalkBack) | Round %1$d, %2$s: %3$s | Runde %1$d, %2$s: %3$s | |
| `scoreboard_cell_bust` | Scoreboard (text) | Round %1$d, %2$s: bust | Runde %1$d, %2$s: raus | |
| `scoreboard_cell_open` | Scoreboard (text) | Round %1$d, %2$s: not scored yet | Runde %1$d, %2$s: noch nicht gewertet | |
| `gameover_title` | Game over (header) | Game over · %1$s | Spiel vorbei · %1$s | |
| `gameover_standings` | Game over (header) | Final standings | Endstand | |
| `gameover_shared` | Game over (header) | Shared win | Geteilter Sieg | |
| `gameover_wins` | Game over (text) | %1$s wins | %1$s gewinnt | |
| `gameover_share` | Game over (text) | %1$s share the win | %1$s teilen sich den Sieg | |
| `gameover_no_winner` | Game over (text) | No winner | Kein Sieger | |
| `gameover_points_each` | Game over (text) | one: %d point each | other: %d points each | one: je %d Punkt | other: je %d Punkte | |
| `gameover_headline_description` | Game over (TalkBack) | %1$s with %2$s | %1$s mit %2$s | %2$s = `points_with` ("Lena gewinnt mit 88 Punkten"). |
| `gameover_standing_description` | Game over (TalkBack) | %1$s, %2$s, %3$s | %1$s, %2$s, %3$s | |
| `gameover_winner` | Game over (TalkBack) | , winner | , Sieger | |
| `gameover_same_players` | Game over (text) | SAME PLAYERS | GLEICHE SPIELER | |
| `gameover_same_players_description` | Game over (TalkBack) | New game with the same players | Neues Spiel mit den gleichen Spielern | |
| `sheet_points_round` | Player sheet / adjust (text) | %1$s · round %2$d | %1$s · Runde %2$d | |
| `sheet_points_this_round` | Player sheet / adjust (text) | points this round | Punkte in dieser Runde | Changed: "in dieser Runde". |
| `sheet_zero_points_this_round` | Player sheet / adjust (text) | 0 points this round | 0 Punkte in dieser Runde | Changed: "in dieser Runde". |
| `sheet_marked_bust` | Player sheet / adjust (text) | Marked bust, no cards recorded. | Als raus markiert, keine Karten erfasst. | |
| `sheet_points_only` | Player sheet / adjust (text) | Points entered without cards. | Punkte ohne Karten eingetragen. | |
| `sheet_rescan` | Player sheet / adjust (button) | Rescan | Neu scannen | |
| `sheet_edit_cards` | Player sheet / adjust (TalkBack) | Edit %1$s's cards | Karten von %1$s bearbeiten | |
| `sheet_rescan_player` | Player sheet / adjust (TalkBack) | Rescan %1$s | %1$s neu scannen | |
| `sheet_reset` | Player sheet / adjust (button) | Reset last entry | Letzten Eintrag zurücksetzen | Was "Reset to open"; fits the full-width sheet button (screenshot opts_sheet_reset_de). |
| `sheet_reset_description` | Player sheet / adjust (TalkBack) | Reset the last entry for %1$s | Letzten Eintrag von %1$s zurücksetzen | |
| `sheet_card_duplicate_description` | Player sheet / adjust (TalkBack) | %1$s, duplicate | %1$s, doppelt | |
| `sheet_adjustments` | Player sheet / adjust (header) | Total adjustments | Korrekturen | |
| `sheet_adjust` | Player sheet / adjust (button) | Adjust total | Summe korrigieren | |
| `sheet_adjust_description` | Player sheet / adjust (TalkBack) | Adjust %1$s's total (penalty, house rule) | Summe von %1$s korrigieren (Strafe, Hausregel) | |
| `sheet_removed` | Player sheet / adjust (text) | Removed %1$s | %1$s entfernt | |
| `sheet_adjustment_description` | Player sheet / adjust (TalkBack) | Adjustment %1$s | Korrektur %1$s | |
| `sheet_remove_adjustment` | Player sheet / adjust (TalkBack) | Remove adjustment %1$s | Korrektur %1$s entfernen | |
| `adjust_title` | Player sheet / adjust (text) | Adjust %1$s's total | Summe von %1$s korrigieren | |
| `adjust_note_placeholder` | Player sheet / adjust (text field) | Note (optional): penalty … | Notiz (optional): Strafe … | |
| `adjust_note_label` | Player sheet / adjust (text field) | Note, optional | Notiz, optional | |
| `adjust_apply` | Player sheet / adjust (button) | Apply | Übernehmen | |
| `adjust_apply_delta` | Player sheet / adjust (button) | Apply %1$s | %1$s übernehmen | |
| `adjust_field_description` | Player sheet / adjust (TalkBack) | Points to add, use minus to subtract | Punkte zum Addieren, mit Minus abziehen | Changed: clearer TalkBack text. |
| `adjust_flip_sign` | Player sheet / adjust (TalkBack) | Switch between add and subtract | Zwischen Plus und Minus wechseln | Changed: "Plus und Minus" (matches the ± button). |
| `adjust_error_sign` | Player sheet / adjust (text) | Enter a number after the sign | Gib nach dem Vorzeichen eine Zahl ein | |
| `adjust_error_range` | Player sheet / adjust (text) | Up to %1$d points | Höchstens %1$d Punkte | |
| `adjust_error_zero` | Player sheet / adjust (text) | Enter a number other than 0 | Gib eine Zahl außer 0 ein | |
| `adjust_step_add` | Player sheet / adjust (text) | Add %1$d | %1$d addieren | Changed: "addieren" pairs with "abziehen". |
| `adjust_step_subtract` | Player sheet / adjust (text) | Subtract %1$d | %1$d abziehen | |
| `adjust_preview` | Player sheet / adjust (text) | Total %1$d → %2$d | Summe %1$d → %2$d | |
| `adjust_round` | Player sheet / adjust (text) | round %1$d | Runde %1$d | |
| `adjust_plus` | Player sheet / adjust (text) | plus %1$d | plus %1$d | |
| `adjust_minus` | Player sheet / adjust (text) | minus %1$d | minus %1$d | |
| `breakdown_bust` | Player sheet / adjust (text) | BUST · %1$s | RAUS · %1$s | |
| `breakdown_flip7` | Player sheet / adjust (text) | Flip 7 +%1$d | Flip 7 +%1$d | |
| `toast_bust` | Round (toast) | %1$s marked bust | %1$s ist raus | |
| `card_freeze` | Cards / avatars (shared) (text) | Freeze | = EN (not translated: proper name) | |
| `card_flip_three` | Cards / avatars (shared) (text) | Flip 3 | Flip 3 | Short chip label "Flip 3" (card says Flip Three). |
| `card_second_chance` | Cards / avatars (shared) (text) | 2nd chance | 2nd Chance | Card name as printed: "2nd Chance". |
| `card_back` | Cards / avatars (shared) (text) | Back | Rückseite | |
| `card_plus_spoken` | Cards / avatars (shared) (text) | plus %1$d | plus %1$d | |
| `card_times_two_spoken` | Cards / avatars (shared) (text) | times 2 | mal 2 | |
| `dup_two` | Manual / review (hints) (text); Player sheet / adjust (text) | two %1$ds | zwei %1$der | |
| `dup_three` | Manual / review (hints) (text) | three %1$ds | drei %1$der | |
| `dup_four` | Manual / review (hints) (text) | four %1$ds | vier %1$der | |
| `dup_five` | Manual / review (hints) (text) | five %1$ds | fünf %1$der | |
| `dup_many` | Manual / review (hints) (text) | %2$d× %1$d | %2$d× %1$d | |
| `formula_plus` | Manual / review (hints) (text) | plus | plus | |
| `formula_times` | Manual / review (hints) (text) | times | mal | |
| `formula_equals` | Manual / review (hints) (text) | %1$s equals %2$s | %1$s gleich %2$s | |
| `formula_flip7` | Review / manual / player sheet (formula text) | %1$s Flip 7 | %1$s Flip 7 | New (UX fix): the bonus is labelled ("+ 15 Flip 7"); card name stays as printed. |
| `formula_flip7_spoken` | Review / manual / player sheet (TalkBack) | %1$s Flip 7 bonus | %1$s Flip-7-Bonus | New (UX fix): "plus 15 Flip-7-Bonus". |
| `second_chance_used` | Cards / avatars (shared) (text) | Second Chance used · %1$s counted once | Second Chance genutzt · %1$s zählt einmal | |
| `review_photo` | Review (TalkBack) | Frozen photo of %1$s's cards | Standbild der Karten von %1$s | |
| `review_back_to_camera` | Review (TalkBack) | Back to camera | Zurück zur Kamera | |
| `review_fix` | Review (button) | Fix | Ändern | |
| `review_confirm_bust` | Manual entry (button); Review (button) | Confirm bust | Raus bestätigen | |
| `review_ok` | Review (button) | OK | OK | No "✓" (the second line says where it goes). |
| `review_round_progress` | Review (text) | Round %1$d · %2$s | Runde %1$d · %2$s | |
| `review_in_row` | Review (text) | %1$s in the row | %1$s in der Reihe | |
| `review_round_points` | Review (text) | Round points | Rundenpunkte | |
| `review_zero_bust` | Review (text) | 0 points, bust | 0 Punkte, raus | |
| `review_no_cards` | Review (text) | No cards found | Keine Karten gefunden | |
| `review_detector_failed` | Review (text) | The card detector failed: %1$s | Die Kartenerkennung ist fehlgeschlagen: %1$s | |
| `review_no_cards_hint` | Review (text) | Hold the phone over the whole row, with enough light, and try again. | Halt das Handy über die ganze Reihe, mit genug Licht, und versuch es noch mal. | |
| `review_retake` | Review (TalkBack); Review (button) | Retake | Neues Foto | |
| `review_include` | Review (text field) | Include %1$s | %1$s mitzählen | |
| `review_leave_out` | Review (text field) | Leave out %1$s | %1$s weglassen | |
| `review_tap_hint` | Review (text) | Tap a card to leave it out | Tipp eine Karte an, um sie wegzulassen | |
| `review_flip7_description` | Review (TalkBack) | Flip 7 bonus, plus %1$d | Flip-7-Bonus, plus %1$d | |
| `review_flip7` | Review (TalkBack) | FLIP 7 | = EN (not translated: proper name) | |
| `review_number_limit` | Manual / review (hints) (text) | More than %1$d number cards – tap cards to leave them out | Mehr als %1$d Zahlenkarten – tipp Karten an, um sie wegzulassen | |
| `chip_duplicate` | Review / camera (chips, boxes) (TalkBack) | %1$s, duplicate | %1$s, doppelt | |
| `chip_ignored` | Review / camera (chips, boxes) (text) | %1$s, doesn't score | %1$s, zählt nicht | |
| `chip_excluded` | Review / camera (chips, boxes) (text) | %1$s, left out | %1$s, ignoriert | user: "ignoriert" |
| `box_excluded` | Review / camera (chips, boxes) (text) | EXCLUDED | IGNORIERT | user: "IGNORIERT"; TalkBack "ignoriert". |
| `manual_cards_for` | Manual entry (text) | Cards for | Karten von | |
| `manual_in_row` | Manual entry (header) | In the row | In der Reihe | |
| `manual_empty` | Manual entry (text) | Tap the cards below to add them | Tipp unten auf die Karten, um sie hinzuzufügen | |
| `manual_numbers` | Manual entry (header) | Numbers | Zahlen | |
| `manual_modifiers` | Manual entry (header) | Modifiers | Bonuskarten | |
| `manual_bust` | Manual entry (text) | %1$s – remove one, or confirm the bust. | %1$s – nimm eine weg oder bestätige das Raus. | |
| `manual_over_limit` | Manual entry (text) | More than %1$d number cards – remove %2$d to save | Mehr als %1$d Zahlenkarten – nimm %2$d weg, um zu speichern | |
| `manual_limit_reached` | Manual / review (hints) (text) | %1$d number cards – the maximum | %1$d Zahlenkarten – das Maximum | |
| `manual_limit_over` | Manual / review (hints) (text) | %1$d number cards – at most %2$d fit in a row | %1$d Zahlenkarten – höchstens %2$d passen in eine Reihe | |
| `manual_limit_state` | Manual / review (hints) (text) | %1$d number cards selected | %1$d Zahlenkarten ausgewählt | |
| `manual_count_state` | Manual / review (hints) (text) | %1$d selected, tap to remove one | %1$d ausgewählt, tippen nimmt eine weg | |
| `after_scan_title` | Review / manual (confirm button, bust overlay) (text) | AFTER SAVING | NACH DEM SPEICHERN | |
| `after_scan_next` | Review / manual (confirm button, bust overlay) (text) | Next: %1$s | Weiter: %1$s | |
| `after_scan_table` | Review / manual (split OK, second line; ▾ menu option) | Back to table | Zurück zum Tisch | Capitalised: own line / menu item now. |
| `after_scan_choose` | Review / manual (confirm button, bust overlay) (TalkBack) | Choose what happens after %1$s | Wähl, was nach „%1$s“ passiert | Changed: du-form "Wähl". |
| `after_scan_label` | Review / manual (confirm button, bust overlay) (text) | %1$s · %2$s | %1$s · %2$s | |
| `bust_overlay_description` | Review / manual (confirm button, bust overlay) (TalkBack) | %1$s bust | %1$s ist raus | |
| `bust_overlay_undo` | Review / manual (confirm button, bust overlay) (text) | UNDO | RÜCKGÄNGIG | |
| `ordinal_st` | Game over (text) | %1$dst | %1$d. | |
| `ordinal_nd` | Game over (text) | %1$dnd | %1$d. | |
| `ordinal_rd` | Game over (text) | %1$drd | %1$d. | |
| `ordinal_th` | Game over (text) | %1$dth | %1$d. | |
| `player_heading` | Review / camera (chips, boxes) (TalkBack) | %1$s: %2$s | %1$s: %2$s | |
| `about_title` | About (header); Home (text) | About & licenses | Über & Lizenzen | |
| `about_disclaimer` | About (text) | Unofficial score keeper for Flip 7. Not affiliated with The Op Games. | Inoffizieller Punktezähler für Flip 7. Keine Verbindung zu The Op Games. | |
| `about_version` | About (text) | Version %1$s (%2$d) | Version %1$s (%2$d) | |
| `about_model` | About (text) | Detector model: %1$s | Erkennungsmodell: %1$s | |
| `about_license_body` | About (text) | Scan7 is free software: you may use, study, share and change it under the GNU Affero General Public License v3.0. Its source code is public. | Scan7 ist freie Software: Du darfst sie unter der GNU Affero General Public License v3.0 nutzen, untersuchen, weitergeben und verändern. Der Quellcode ist öffentlich. | |
| `about_source` | About (text) | Source code | Quellcode | |
| `about_privacy` | About (text) | Privacy policy | Datenschutzerklärung | |
| `about_third_party` | About (header) | Third-party software | Software von Dritten | |
| `about_show_license` | About (text) | Show license | Lizenz anzeigen | |
| `gameover_home` | Game over (button) | Home | Startseite | New id (was action_home): Game over button next to "Neues Spiel"; "Startseite" fits 88 dp. |
| `round_next` | Round (button) | Next round | Nächste Runde | |
| `round_move_up` | Round (text) | Move %1$s up | %1$s nach oben schieben | |
| `round_move_down` | Round (text) | Move %1$s down | %1$s nach unten schieben | |
| `seat_number` | Round (TalkBack) | Seat %1$d | Platz %1$d | |
| `menu_direction` | Round (button) | Scan direction | Scan-Richtung | |
| `direction_cw_short` | Round (text) | ↻ Clockwise | ↻ Uhrzeigersinn | |
| `direction_ccw_short` | Round (text) | ↺ Counter | ↺ Gegen-\nuhrzeigersinn | user: "Gegenuhrzeigersinn"; explicit break so it fits the half-width segment on 360 dp. |
| `direction_cw` | Round (text) | ↻ clockwise | ↻ im Uhrzeigersinn | |
| `direction_ccw` | Round (text) | ↺ counter-clockwise | ↺ gegen den Uhrzeigersinn | |
| `direction_cw_spoken` | Round (text) | Scan clockwise | Im Uhrzeigersinn scannen | |
| `direction_ccw_spoken` | Round (text) | Scan counter-clockwise | Gegen den Uhrzeigersinn scannen | |
| `menu_settings` | Round ⋮ menu (section label) | Settings | Einstellungen | |
| `menu_target` | Round ⋮ menu (setting) | Target points | Zielpunkte | |
| `menu_target_description` | Round ⋮ menu (TalkBack, adjustable) | Target points of this game | Zielpunkte dieses Spiels | |
| `menu_after_score` | Round ⋮ menu (setting) | After a score | Nach einer Wertung | |
| `after_score_next` | Round ⋮ menu (segment) | Next player | Nächster Spieler | Wraps to two lines at 360 dp × font 1.3. |
| `after_score_table` | Round ⋮ menu (segment) | Back to table | Zum Tisch | Short segment; the review's ▾ says "Zurück zum Tisch". |
| `menu_entry` | Round ⋮ menu (setting) | Entry | Eingabe | |
| `entry_scan` | Round ⋮ menu (segment) | Scan | Scannen | |
| `entry_type` | Round ⋮ menu (segment) | Type | Eingeben | Not "Tippen" (also means "tap"); same as the camera's Type button. |
| `entry_scan_spoken` | Round ⋮ menu (TalkBack) | Enter results by scanning | Ergebnisse scannen | |
| `entry_type_spoken` | Round ⋮ menu (TalkBack) | Enter results by typing | Ergebnisse von Hand eingeben | |
| `confirm_lower_target_title` | Confirm dialogs (header) | Lower the target to %1$d? | Ziel auf %1$d senken? | |
| `confirm_lower_target_body` | Confirm dialogs (text) | The game ends after this round. | Das Spiel endet nach dieser Runde. | |
| `action_lower_target` | Confirm dialogs (button) | Lower | Senken | |
| `label_enter_next` | Round bottom button, entry Type (text) | ENTER NEXT | WEITER EINGEBEN | Parallel to "WEITER SCANNEN". |
| `action_enter_next_player` | Round bottom button, entry Type (TalkBack) | Enter next: %1$s | Als Nächstes eingeben: %1$s | |
| `action_enter_player` | Round card / sheet, entry Type (TalkBack) | Enter cards for %1$s | Karten von %1$s eingeben | |
| `after_scan_description_next` | Review / manual split OK (TalkBack) | %1$s, then next: %2$s | %1$s, dann weiter: %2$s | "OK, then next: Ben". |
| `after_scan_description_table` | Review / manual split OK (TalkBack) | %1$s, then back to table | %1$s, dann zurück zum Tisch | |
| `camera_reading` | Camera (text) | Reading cards… | Karten werden gelesen … | |
| `camera_starting` | Camera (text) | Camera is starting – try again | Kamera startet – versuch es gleich noch mal | |
| `camera_detector_unavailable` | Camera (text) | Card detector unavailable | Kartenerkennung nicht verfügbar | |
| `camera_back` | Camera (TalkBack) | Back to table | Zurück zum Tisch | |
| `camera_label_next` | Camera (text field) | Next | Als Nächstes | |
| `camera_label_scanning` | Camera (text field) | Scanning | Scannen | |
| `camera_type_description` | Camera (TalkBack) | Type the cards instead | Karten stattdessen von Hand eingeben | Changed: "von Hand eingeben" (same term as the sheet). |
| `camera_type` | Camera (text) | Type | Eingeben | Changed "Tippen" → "Eingeben": "Tippen" also means "tap"; matches "Von Hand eingeben". |
| `camera_banner_description` | Camera (TalkBack) | %1$s: %2$s, %3$s | %1$s: %2$s, %3$s | |
| `camera_banner` | Camera (text) | %1$s · %2$d %3$s | %1$s · %2$d %3$s | |
| `camera_torch` | Camera (text) | Torch | Taschenlampe | |
| `camera_zoom` | Camera (text field) | Zoom %1$s | Zoom %1$s | |
| `camera_cards_seen` | Camera (text) | one: %d card seen | other: %d cards seen | one: %d Karte erkannt | other: %d Karten erkannt | |
| `camera_cards_seen_label` | Camera (text) | one: card seen | other: cards seen | one: Karte erkannt | other: Karten erkannt | |
| `camera_shutter` | Camera (TalkBack) | Take photo | Foto aufnehmen | |
| `camera_permission_title` | Camera (text) | Camera access needed | Kamerazugriff nötig | |
| `camera_permission_body` | Camera (text) | Scan7 uses the camera to read the cards on the table. Images stay on this phone. | Scan7 liest mit der Kamera die Karten auf dem Tisch. Die Bilder bleiben auf diesem Handy. | |
| `camera_permission_allow` | Camera (button) | Allow camera | Kamera erlauben | |
| `camera_permission_settings` | Camera (button) | Open app settings | App-Einstellungen öffnen | |
| `a11y_player_open` | Shared component (text) | not scored yet | noch nicht gewertet | |
| `a11y_player_scored` | Shared component (text) | one: scored %d this round | other: scored %d this round | one: diese Runde %d Punkt | other: diese Runde %d Punkte | |
| `a11y_player_bust` | Shared component (text) | bust this round | diese Runde raus | |
| `a11y_player_leading` | Shared component (text) | leading | führt | |
| `a11y_adjusted_by` | Shared component; Scoreboard; Game over (TalkBack) | adjusted by %1$s | korrigiert um %1$s | New (UX fix): "korrigiert um minus 5" (uses `adjust_plus` / `adjust_minus`). |
| `a11y_adjusted` | Shared component; Scoreboard; Game over (TalkBack) | includes total adjustments | mit Korrekturen | New (UX fix): adjustments that cancel out. |
| `about_tp_yolo` | About (text) | Detector architecture + pretrained YOLO26n weights · AGPL-3.0 | Detektor-Architektur + vortrainierte YOLO26n-Gewichte · AGPL-3.0 | |
| `about_tp_litert` | About (text) | On-device inference, GPU delegate · Apache-2.0 | Erkennung auf dem Gerät, GPU-Delegate · Apache-2.0 | |
| `about_tp_jetpack` | About (text) | Compose, Material 3, Activity, Core, Lifecycle, Navigation 3, CameraX, Room, SplashScreen · Apache-2.0 | Compose, Material 3, Activity, Core, Lifecycle, Navigation 3, CameraX, Room, SplashScreen · Apache-2.0 | |
| `about_tp_kotlin` | About (text) | Standard library, kotlinx.coroutines, kotlinx.serialization · Apache-2.0 | Standardbibliothek, kotlinx.coroutines, kotlinx.serialization · Apache-2.0 | |
| `about_tp_unbounded` | About (text) | Font · © 2022 The Unbounded Project Authors · SIL OFL 1.1 | Schrift · © 2022 The Unbounded Project Authors · SIL OFL 1.1 | |
| `about_tp_dmmono` | About (text) | Font · © 2020 The DM Mono Project Authors · SIL OFL 1.1 | Schrift · © 2020 The DM Mono Project Authors · SIL OFL 1.1 | |
| `home_more_options` | Home (TalkBack) | More options | Weitere Optionen | TalkBack of the Home ⋮ button. |
| `home_game_open_scoreboard` | Home (TalkBack) | Open scoreboard | Tabelle öffnen | Long-press menu: "Tabelle öffnen" (same as the row tap label). |
| `home_game_resume` | Home (TalkBack) | Resume | Weiterspielen | |
| `home_game_delete` | Home (TalkBack); Home (text) | Delete game | Spiel löschen | Long-press menu item + TalkBack action: "Spiel löschen". |
| `home_game_same_players` | Home (text); Home (TalkBack) | Same players | Gleiche Spieler | New (UX fix): history long-press menu item + TalkBack action, opens New game on Seats. |
| `home_game_round` | Home (text) | Round %1$d | Runde %1$d | Delete dialog summary; non-breaking space keeps "Runde 6" together. |
| `home_game_rounds` | Home (text) | one: %1$d round | other: %1$d rounds | one: %1$d Runde | other: %1$d Runden | |
| `confirm_delete_game_title` | Confirm dialogs (header) | Delete this game? | Dieses Spiel löschen? | |
| `confirm_delete_game_action` | Confirm dialogs (TalkBack) | Delete | Löschen | |
| `confirm_delete_game_cancel` | Home (TalkBack) | Cancel | Abbrechen | |
| `confirm_delete_game_running` | Confirm dialogs (text) | The game in progress will be lost. | Das laufende Spiel geht verloren. | |
| `confirm_delete_game_irreversible` | Confirm dialogs (text) | This can't be undone. | Das lässt sich nicht rückgängig machen. | Changed: "Das lässt sich nicht rückgängig machen." (shorter, natural). |
| `action_enter_manually_two_lines` | Player sheet / adjust (button) | Enter / manually | Von Hand / eingeben | New: two-line sheet button at font scale > 1.4 ("Von Hand / eingeben"). |
