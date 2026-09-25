# pl_PL review -- packet-2 (592 records), isolated reviewer 2

Scripted order check of placeholders, & codes, \n, protected literals and
/ncc tokens: all match (only flag: "-1" inside "Stand-on-17", false
positive). No gendered second-person forms. No Tier 0 or Tier 1.

Tier 2:
- complex-variant.cycle-horse-style / horse-style-changed, mob-settings.style-lore,
  jockey-options.style, admin.current-style-lore: "umaszczenie" = coat colour,
  a synonym of the adjacent "kolor"; Minecraft horse style = markings.
  SUGGEST "wzór sierści".
- blackjack.closed-before-turn: "czas twojej kolejki" (kolejka = queue) vs
  "kolej" everywhere else. SUGGEST "&eWróć, zanim skończy się twój czas na
  decyzję, inaczej twoja ręka automatycznie spasuje."

Tier 3:
- blackjack.round-summary-hand-busted: "ponad 21 -{amount}" reads as
  arithmetic; SUGGEST "&cRęka {number}: przekroczono 21, -{amount}"
- blackjack-settings.max-hands-desc: calque; SUGGEST "&7Maksymalna liczba
  rąk, jaką jeden gracz może mieć naraz dzięki rozdzielaniu."
- blackjack-settings.turn-timer-timeout-desc-2: masculine past forms for a
  generic player; SUGGEST conditional present ("jeśli gracz zamknie menu lub
  się rozłączy").
- blackjack-settings.prompt-stand-17 / -detailed: "(w %)" splits the phrase.
- coin-flip-settings / rock-paper-scissors-settings .mode-switching-updated:
  ambiguous; SUGGEST "&aZaktualizowano ustawienie „Zmiana trybu gry przez
  gracza”."
- commands.help-create: "w twoim położeniu" = "in your predicament".
  SUGGEST "w miejscu, w którym stoisz".
- admin.slots-rtp-lore: "zwrot" also means refund; SUGGEST "zwrot dla gracza".
- mob-settings.baby, admin.baby, mob-selection.age-unsupported: "Młode"
  (neuter) vs "Dorosły" (masc.); optional SUGGEST "Młody".

Terminology notes: horse colour vs markings (above); kolej vs kolejka; bust
wording differs (Przekroczono 21 vs ponad 21); "zwrot" = refund and RTP;
game-name capitalisation mixed (Ruletki vs ruletki). Everything else
consistent.

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 6, Tier 3 = 12.
