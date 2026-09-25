# sw_KE review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean (script-checked token order,
protected literals, `/ncc claim`, typed `-1` / `off`, no placeholder
joined to a letter). Context notes hold (seat not yet available, current
pot, future cash-out, round-count max chain, vines `mitambaa`, RTP not a
refund, variance = hit frequency, "2.5%"). No gendered person words.

KEY: mines.insufficient-currency
TIER: 1
ISSUE: "Hazitoshi: {currency}" puts a class-10 subject concord on the verb that must agree with the placeholder; its noun class is unknown at runtime.
APPLIED: "&cKiasi cha {currency} hakitoshi."

KEY: slots.paytable-leftmost-rule
TIER: 1
ISSUE: "Lazima uanze" makes the player the actor; the rule is about the winning run.
APPLIED: "&8Msururu lazima uanzie kwenye reeli ya kushoto kabisa."

KEY: slots.rail-exit-session
TIER: 1
ISSUE: "kilichokwisha kushindwa" reads as "already defeated / lost", reversing "anything already won".
APPLIED: "&7Hilo humaliza kipindi chako hapa; chochote ulichokwisha kushinda bado kinalipwa."

KEY: slots.auto-profit-target-description
TIER: 1
ISSUE: "tangu kilipoanzishwa" uses a class-7 concord with no antecedent; the subject "kuzungusha kiotomatiki" is class 15 (ku-).
APPLIED: "&7Husimamisha kuzungusha kiotomatiki faida tangu kulipoanzishwa ikifikia kiasi hiki."

KEY: slots.auto-profit-target-set
TIER: 1
ISSUE: Same wrong concord.
APPLIED: "&aKuzungusha kiotomatiki kutasimama faida tangu kulipoanzishwa ikifikia lengo: {amount}."

KEY: slots.auto-loss-limit-description
TIER: 1
ISSUE: Same wrong concord.
APPLIED: "&7Husimamisha kuzungusha kiotomatiki kabla hasara tangu kulipoanzishwa haijazidi kiasi hiki."

KEY: slots.auto-loss-limit-set
TIER: 1
ISSUE: Same wrong concord.
APPLIED: "&aKuzungusha kiotomatiki kutasimama kabla hasara tangu kulipoanzishwa haijazidi kikomo: {amount}."

KEY: preferences.overflow.bank-explained
TIER: 3
ISSUE: Subjectless "unatunzwa" reads first as "YOU are kept safe"; name the winnings.
APPLIED: "&7Hifadhi: ushindi unatunzwa salama hadi utakapotoa nafasi."

KEY: preferences.overflow.drop-explained
TIER: 3
ISSUE: Same ambiguity, and the remainder switched to a generic ki- concord.
APPLIED: "&7Dondosha: ushindi unadondoshwa karibu hadi kikomo cha seva, unaobaki unatunzwa salama."

KEY: common.current
TIER: 3
ISSUE: Bare "Sasa:" reads as "Now:" rather than "Current:".
APPLIED: "&7Kwa sasa: {value}"

KEY: coin-flip.player-turn
TIER: 3
ISSUE: A colon straight after the connective "ya" is awkward; "ya" agrees with "zamu", not the placeholder.
APPLIED: "&oZamu ya {player}&o"

KEY: rock-paper-scissors.player-turn
TIER: 3
ISSUE: Same.
APPLIED: "&oZamu ya {player}&o"

KEY: betting.inventory-full
TIER: 3
ISSUE: The amount looked like the value of "no room"; "kwa {amount}" says "no room FOR".
APPLIED: "&cHakuna nafasi kwenye mkoba kwa {amount}; vitu hivyo vinadondoshwa karibu."

KEY: payout.wager-blocked
TIER: 3
ISSUE: The trailing amount was detached from the items it counts.
APPLIED: "&cToa nafasi kwa vitu vilivyohifadhiwa ({amount}) kabla ya kucheza tena."

KEY: game-options.roulette
TIER: 3
ISSUE: "Rulet" is not a Swahili adaptation; the Swahili form is "Ruleti".
APPLIED: "Ruleti"

KEY: slots.rail-paylines-feedback
TIER: 3
ISSUE: "mstari ulioongeza" lacks the object marker its partner "uliouondoa" has.
APPLIED: "&7Mmweko wa kijani huonyesha mstari uliouongeza, na mweusi mstari uliouondoa."

KEY: slots.auto-any-win
TIER: 3
ISSUE: Intransitive "Simama" reads as an order to the player; the toggle stops Auto Spin ("Simamisha").
APPLIED: "&eSimamisha kwa ushindi wowote"

KEY: slots.auto-settings-reset-hint
TIER: 3
ISSUE: "chaguo-msingi za" has a class-10 concord that does not agree with "chaguo".
APPLIED: "&7Bofya ili kurejesha mipangilio chaguo-msingi ya kuzungusha kiotomatiki."

KEY: slots.profiles-count
TIER: 3
ISSUE: Generic vi- "Vilivyohifadhiwa" instead of the "Wasifu uliohifadhiwa" used by the rail count.
APPLIED: "&7Wasifu uliohifadhiwa: &a{count}&7/&a{max}"

KEY: slots.paytable-card-no-runs
TIER: 3
ISSUE: "kwenye reeli (reeli: {columns})" says "reels" twice; noun + digit is natural.
APPLIED: "&8Hakuna msururu wa alama hii unaoenea kwenye reeli {columns}."

KEY: slots-settings.variance-tradeoff
TIER: 3
ISSUE: "kubwa zaidi sana" is awkward stacking.
APPLIED: "&7Kuyumba zaidi kunamaanisha mistari hulipa mara chache zaidi, lakini jakpoti za misururu mirefu ni kubwa zaidi kwa kiasi kikubwa."

KEY: mines.mode-chords
TIER: 3
ISSUE: "Kodi" mainly means rent / tax ("Hali: Kodi" = "Mode: Tax"); "Akodi" is the musical term.
APPLIED: "Akodi"

Tier 0: 0 · Tier 1: 7 · Tier 2: 0 · Tier 3: 15

## Disposition (translator)

All 22 applied. Follow-ups in self-review: every other "Sasa:" label
(14 lines) became "Kwa sasa:", the other seven "Rulet" lines became
"Ruleti", and the mines / roulette / blackjack `inventory-full` lines take
"kwa {amount}; vitu hivyo".
