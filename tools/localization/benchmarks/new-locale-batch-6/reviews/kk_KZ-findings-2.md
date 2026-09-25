# kk_KZ review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean. Context notes hold:
`{setting}` works with every filler, `{occupation}` sits after a colon,
`resplit-offer` is a completed split, vines are climbing plants,
`none` fits both slots, chip size is "фишка құны", the three closed-*
timer messages stay distinct. Not scored: ASCII "--" dashes follow the
English source; literal "1 мен 24" / "0 мен 100" are correct.

KEY: dragon-settings.prompt-setting-detailed
TIER: 1
ISSUE: The мен/бен/пен allomorph must agree with the unknown {min} ("5 мен", "10 мен" are ungrammatical).
SUGGEST: "&aЧатқа жаңа әдепкі {setting} санын жазыңыз ({min}–{max} аралығында)."

KEY: blackjack-settings.number-range
TIER: 1
ISSUE: Same "{min} мен" harmony problem.
SUGGEST: "{min}–{max} аралығындағы сан енгізіңіз."

KEY: roulette.ball
TIER: 2
ISSUE: "Шарик" is a colloquial Russian diminutive; standard Kazakh is "шар".
SUGGEST: "Шар"

KEY: mines.dealer-cannot-cover
TIER: 3
ISSUE: "мұндай үлкен ұтыс алуды төлей алмайды" says "cannot pay such a big taking-of-winnings".
SUGGEST: "&cБұл тақта қазір мұндай үлкен ұтысты төлей алмайды. Кейінірек қайталаңыз немесе бәсіңізді азайтыңыз."

KEY: roulette.bets-close-in
TIER: 3
ISSUE: Lowercase "с" inside an all-caps line, and "ішінде" means "within", not "after N seconds".
SUGGEST: "БӘСТЕР {seconds} СЕКУНДТАН КЕЙІН ЖАБЫЛАДЫ!"

KEY: roulette.bet-placed
TIER: 3
ISSUE: "нысана" (a shooting target) is odd for a bet position, and the line never says a bet was placed.
SUGGEST: "&6{amount} мөлшеріндегі бәс тігілді: {bet}"

KEY: blackjack.leave-exit
TIER: 3
ISSUE: Bare "Тұру" is ambiguous (stand / stay); name the chair.
SUGGEST: "Орындықтан тұру/Шығу"

KEY: blackjack.leave-exit-forfeit-warning
TIER: 3
ISSUE: "күйіп кетеді" is a colloquial calque of Russian "сгорит".
SUGGEST: "&c(Бәсіңізден айырыласыз)"

KEY: blackjack.insurance-lost
TIER: 3
ISSUE: Same "күйіп кетті" calque.
SUGGEST: "&cСақтандыру бәсі ұтылды."

KEY: coin-flip-settings.prompt-max-chain
TIER: 3
ISSUE: "ең көп раундтың жаңа санын" reads as "the new number of the maximum round".
SUGGEST: "&aЧатқа сериядағы раундтардың жаңа ең көп санын жазыңыз (шектеусіз үшін -1)."

KEY: rock-paper-scissors-settings.prompt-max-chain
TIER: 3
ISSUE: Same as coin-flip-settings.prompt-max-chain.
SUGGEST: "&aЧатқа сериядағы раундтардың жаңа ең көп санын жазыңыз (шектеусіз үшін -1)."

KEY: blackjack-settings.insurance-desc-2
TIER: 3
ISSUE: "Бәсіңіздің жартысына тең." has no subject and loses "costs".
SUGGEST: "&7Құны бәсіңіздің жартысына тең. Дилердің жасырын картасы 10 ұпай болса (блэкджек), 2:1 төлейді."

KEY: admin.already-moving
TIER: 3
ISSUE: "әлдеқашан" (long since) with a progressive verb is a calque of "already".
SUGGEST: "&cДилер қазір жылжытылып жатыр. Күте тұрыңыз."

KEY: admin.drag-change
TIER: 3
ISSUE: "оны Shift + басыңыз" is awkward for shift-click.
SUGGEST: "Өзгерту үшін затты осында сүйреңіз немесе Shift пернесін ұстап тұрып, оны басыңыз"

KEY: test-menu.clicked-one
TIER: 3
ISSUE: The case suffix sits inside the quoted button name.
SUGGEST: "Сіз «1-нұсқа» түймесін бастыңыз!"

KEY: test-menu.clicked-two
TIER: 3
ISSUE: Same as test-menu.clicked-one.
SUGGEST: "Сіз «2-нұсқа» түймесін бастыңыз!"

KEY: interaction.no-game-permission
TIER: 3
ISSUE: With the Test Game name this gives "Сынақ ойыны ойынын"; a label works for any {game}.
SUGGEST: "&cМына ойынды ойнауға рұқсатыңыз жоқ: {game}."

KEY: occupations.location
TIER: 3
ISSUE: Possessive "орны" dangles inside finish-editing; the other fillers are bare.
SUGGEST: "орын"

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: Two locatives in a row ("ойынындағы сериядағы").
SUGGEST: "Тас, қайшы, қағаз ойынындағы серияның ең көп раунд саны"

KEY: occupations.coin-flip-max-chain
TIER: 3
ISSUE: Same double locative.
SUGGEST: "Тиын лақтыру ойынындағы серияның ең көп раунд саны"

Tier 0: 0 · Tier 1: 2 · Tier 2: 1 · Tier 3: 17

## Disposition (translator)

All 20 applied. A catalog-wide scan for harmony-dependent particles
after a placeholder found only the five `{min} мен {max}` ranges (these
two plus the three house-edge keys in packet 1), all now en-dash ranges.
