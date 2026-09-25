# sr_RS review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Scripted checks: tokens in English order, no
placeholder followed by a letter; Latin only in protected tokens (NCCasino,
NCCASINO, Vault, VAULT, PvP, PvE, /ncc commands, RTP, Shift, x/y/z,
K-K/K-Q, multiplier x); no mixed-script words; every quote pair is „“.
Read-through: no Croatian/ijekavian forms, gender-neutral toward the player
("Потврђујеш?", "Изабрано је „Стој“", "Улог је дуплиран"), label forms for
numbers, genitive `occupations.*`, lowercase genitive-plural Dragon
fillers, distinct `closed-*` messages, completed-action `resplit-offer`,
round-count max chain, chip size as a value, distinct Same Rank / Same
Value.

KEY: test-menu.title
TIER: 3
ISSUE: "Тестни" is uncommon in Serbian UI text and leans Croatian; "пробни" is standard.
SUGGEST: "Пробни мени"

KEY: mob-selection.no-variants
TIER: 3
ISSUE: "за смену" is odd for "cycle"; elsewhere "Cycle …" is "Промени …".
SUGGEST: "&cНема варијанти за промену: &e{mob}&c."

KEY: complex-variant.changed-detailed
TIER: 3
ISSUE: "у {value}" puts a nominative value after "у"; other "changed to" lines use "у: {value}".
SUGGEST: "&a{change} у: &e{value}&a."

KEY: blackjack-settings.turn-timer-timeout-desc-2
TIER: 3
ISSUE: "то место затим непромењено иде до исхода" is a stiff literal rendering; the meaning is that the absent player's stake stays in play until the round is decided.
SUGGEST: "&7Ово је и време чекања пре него што се разреши потез играча који затвори мени или прекине везу; његов улог затим остаје у игри до исхода."

KEY: admin.economy-missing
TIER: 3
ISSUE: Double "за" and undeclined "Vault" after a verbal noun.
SUGGEST: "&cНема додатка за економију. Инсталирај додатак за економију да би се Vault могао користити."

KEY: admin.install-economy
TIER: 3
ISSUE: Same.
SUGGEST: "&7Инсталирај додатак за економију да би се Vault могао користити"

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: In the finish-editing template this yields two "за" phrases and the game name's commas read as list separators.
SUGGEST: "највећег броја рунди у низу (Камен, папир, маказе)"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same double "за".
SUGGEST: "највећег броја рунди у низу (Писмо или глава)"

## Terminology notes

No Tier 2 inconsistencies across делилац, опклада/улог, Понови улог, Уложи
све, жетон / вредност жетона, тајмер, чет, осигурање, Карта / Стој /
Дуплирај / Подели, кутија за дељење, кец, пузавице, јахач, створење, носач /
путник, гомила, подешавања, шпил, чанк, страна. "Max Chain Rounds" is
"највећи број рунди у низу" in settings and "Највише рунди у низу" in admin
lore; both are round counts (not scored).

Counts: Tier 0: 0, Tier 1: 0, Tier 2: 0, Tier 3: 8

## Disposition (translator)

All 8 applied. `turn-timer-timeout-desc-2` took the reviewer's meaning
("stake stays in play") but kept a gender-neutral construction instead of
"играча који … његов": "… пре него што се разреши потез када играч затвори
мени или прекине везу; улог на том месту затим остаје у игри до исхода."
Consistency follow-up: `game-options.test-game` "Тестна игра" → "Пробна
игра" to match the menu title.
