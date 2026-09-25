# fil_PH review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks clean. Context notes hold
(completed-action `resplit-offer`, distinct `closed-*`, distinct Same Rank
/ Same Value, round-count max chain, chip value, `player-only`,
"sa kinaroroonan mo", fillers read correctly).

KEY: admin.title
TIER: 2
ISSUE: English word order "Admin Menu" vs "Menu ng …" elsewhere.
SUGGEST: "Menu ng Admin ({dealer})"

KEY: admin.invalid-admin-option
TIER: 2
ISSUE: Same.
SUGGEST: "&cHindi wastong opsyon sa Menu ng Admin."

KEY: admin.dealer-not-found-chat
TIER: 2
ISSUE: Same.
SUGGEST: "&cHindi mahanap ang dealer para sa sagot sa chat ng menu ng admin."

KEY: blackjack.wager-not-insurance-compatible
TIER: 2
ISSUE: "even" left in English although "Tukol" is the catalog term.
SUGGEST: "&cHindi mahahati nang pantay ang tayang iyan para sa insurance -- subukan ang tukol na halaga."

KEY: mines.rebet-off-reset
TIER: 3
ISSUE: Feature name after "ang" parses awkwardly; quote it.
SUGGEST: "&cNaka-off ang 'Ulitin ang Taya', na-reset sa 0 ang taya."

KEY: roulette.bets-close-in
TIER: 3
ISSUE: Colloquial "SASARADO"; standard future "MAGSASARA".
SUGGEST: "MAGSASARA ANG PAGTAYA SA LOOB NG {seconds} SEGUNDO!"

KEY: roulette.bets-closed-final
TIER: 3
ISSUE: "TAPOS NA ANG TAPOS" is not an idiom; "wala nang bawian".
SUGGEST: "SARADO NA ANG PAGTAYA, WALA NANG BAWIAN"

KEY: blackjack.must-sit-all-in
TIER: 3
ISSUE: "Kailangan mong" + stative adjective is nonstandard.
SUGGEST: "&cKailangang nakaupo ka para itaya lahat."

KEY: blackjack.must-sit-to-bet
TIER: 3
ISSUE: Same.
SUGGEST: "&cKailangang nakaupo ka para makataya."

KEY: blackjack.not-your-turn
TIER: 3
ISSUE: "pa" adds "yet", wrong after the turn has ended.
SUGGEST: "&cHindi mo turno ngayon."

KEY: blackjack.insurance-taken
TIER: 3
ISSUE: Actor-less "Kumuha" reads as an imperative.
SUGGEST: "&aKumuha ka ng insurance sa halagang {amount}."

KEY: mob-settings.delete-mode-on
TIER: 3
ISSUE: "Delete Mode" untranslated.
SUGGEST: "Mode ng Pagbura: &aNAKA-ON"

KEY: mob-settings.delete-mode-off
TIER: 3
ISSUE: Same.
SUGGEST: "Mode ng Pagbura: &cNAKA-OFF"

KEY: mob-settings.exit-delete-mode-lore
TIER: 3
ISSUE: Same.
SUGGEST: "I-click para umalis sa mode ng pagbura"

KEY: mob-settings.enter-delete-mode-lore
TIER: 3
ISSUE: Same.
SUGGEST: "I-click para pumasok sa mode ng pagbura,"

KEY: mob-settings.delete-mode-enabled
TIER: 3
ISSUE: Same.
SUGGEST: "&aNaka-on ang mode ng pagbura."

KEY: mob-settings.delete-mode-enabled-detailed
TIER: 3
ISSUE: Same.
SUGGEST: "&aNaka-on ang mode ng pagbura. I-click ang anumang sakay para burahin ito."

KEY: mob-settings.delete-mode-disabled
TIER: 3
ISSUE: Same.
SUGGEST: "&aNaka-off ang mode ng pagbura."

KEY: jockey-options.variant-changed
TIER: 3
ISSUE: Two "ng" phrases in a row.
SUGGEST: "&aAng variant ng {mob} ay &e{variant}&a na ngayon."

KEY: complex-variant.unsupported
TIER: 3
ISSUE: "Tropical Fish" left in English in a mixed list.
SUGGEST: "&cHindi Llama, Kabayo, o Tropikal na Isda ang nilalang na ito."

KEY: coin-flip-settings.toggle-mode-switching
TIER: 3
ISSUE: Clipped "Pagpalit"; standard "Pagpapalit".
SUGGEST: "Pagpapalit ng Mode ng Laro ng Manlalaro"

KEY: coin-flip-settings.mode-switching-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&aNa-update ang pagpapalit ng mode ng laro ng manlalaro."

KEY: rock-paper-scissors-settings.toggle-mode-switching
TIER: 3
ISSUE: Same.
SUGGEST: "Pagpapalit ng Mode ng Laro ng Manlalaro"

KEY: rock-paper-scissors-settings.mode-switching-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&aNa-update ang pagpapalit ng mode ng laro ng manlalaro."

KEY: admin.rps-mode-switching-lore
TIER: 3
ISSUE: Same.
SUGGEST: "&7Pagpapalit ng Mode ng Manlalaro: &a{value}"

KEY: admin.coin-flip-mode-switching-lore
TIER: 3
ISSUE: Same.
SUGGEST: "&7Pagpapalit ng Mode ng Manlalaro: &a{value}"

KEY: blackjack-settings.toggle-aces-hit
TIER: 3
ISSUE: Verb "Kumuha" where the sibling rule labels use nouns.
SUGGEST: "I-on/I-off ang Pagkuha sa Nahating Alas"

KEY: blackjack-settings.aces-hit-updated
TIER: 3
ISSUE: Same.
SUGGEST: "&ePagkuha sa Nahating Alas: {value}"

KEY: admin.currency-mode-switched
TIER: 3
ISSUE: English order "currency mode".
SUGGEST: "&eNapalitan ang mode ng currency sa: &a{mode}&e."

KEY: admin.toggle-currency-mode
TIER: 3
ISSUE: Same.
SUGGEST: "Palitan ang Mode ng Currency: {mode}"

KEY: confirm.invalid-confirm-option
TIER: 3
ISSUE: "confirm menu" in English order.
SUGGEST: "&cHindi wastong opsyon sa menu ng kumpirmasyon."

## Terminology notes

"X Menu" and "X mode" use Filipino order ("Menu ng …", "mode ng …");
"Tukol" for even; the delete action and delete mode are both Filipino;
rule labels use nouns (Pagkuha, Pagdoble, Paghati). "Vault Mode" / "VAULT
mode" / "Standard mode" acceptable as proper-noun modes.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 4 | Tier 3: 27 | Total: 31

## Disposition (translator)

All 31 applied. For consistency with packet 1's `baccarat.must-be-seated`,
the two blackjack must-sit keys use "Kailangang nakaupo ka para …" instead
of the suggested "Dapat nakaupo ka para …" (same fix of the nonstandard
construction). Follow-up: `game-options.return-admin` now says "Menu ng
Admin".
