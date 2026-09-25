# pl_PL review -- packet-1 (592 records), isolated reviewer 1

No Tier 0: ordered placeholders, & codes, \n, /ncc claim, -1 and off match
in every record; no placeholder+letter joins. Informal address throughout;
no gendered form referring to the player.

Tier 1:
- slots.demo-result-win / demo-result-loss / demo-cell-note: "wymienić
  walutę" / "bez wymiany waluty" means currency *conversion*; EN means no
  money changed hands. SUGGEST "Nie pobrano ani nie wypłacono waluty." /
  "&eWynik demo – waluta nie została pobrana ani wypłacona."

Tier 2:
- slots.auto-profit-target-description / -set, auto-loss-limit-description /
  -set: "batch" rendered "sesja", which already means the whole Slots session
  (rail-exit-session, prompt-another-game-warning). SUGGEST "z tej serii
  obrotów".
- slots.auto-summary-unlimited: "Bez limitu" vs "brak"; likely fills {spins}
  ("Limit obrotów: Bez limitu", "(limit Bez limitu)"). SUGGEST "nieograniczony"
  (call site to be checked).

Tier 3:
- preferences.language.server-default: "Domyślny serwera" headless.
  SUGGEST "Zgodny z serwerem"
- payout.context-disconnected: double "podczas"; SUGGEST "Twoje połączenie
  zostało przerwane w trakcie gry {game}. Gra zakończyła się pod twoją
  nieobecność."
- payout.context-server-restart: "więc został zwrócony" binds to "Serwer".
  SUGGEST "... więc zakład został zwrócony."
- coin-flip.awaiting-wager: "Złóż stawkę" unusual. SUGGEST "&f&oZłóż zakład,
  aby zacząć"
- baccarat.banker-win-odds: decimal point; SUGGEST "0,95:1" (display-only).
- test-game.title: SUGGEST "Gra w zakłady dla dwóch graczy"
- slots.no-safe-denomination: "zagrać stawkę" -> "postawić".
- slots.rail-exit-session: verbless clause. SUGGEST "&7To kończy tutaj twoją
  sesję; dotychczasowe wygrane i tak zostaną wypłacone."
- slots.rail-wager-total: "na 1 liniach". SUGGEST "&7Łączny zakład: &a{amount}
  &7(liczba linii: &a{lines}&7)"
- slots.rail-profiles-controls-empty: SUGGEST "&7Lewy klik poniżej: zapisz
  pierwszy profil."
- slots.prompt-another-game-cancelled: "pytanie Slotów" awkward.
- slots-settings.variance-top-line: reads as physically biggest line.
  SUGGEST "&7Najwyższy mnożnik linii na całą szerokość: &e{multiplier}x"

Terminology notes: "sesja" double meaning; "Unlimited" Bez limitu vs brak;
line-count construction differs between rail-wager-total and
spin-lore-breakdown. Everything else consistent.

Counts: Tier 0 = 0, Tier 1 = 3, Tier 2 = 5, Tier 3 = 12.
