# is_IS review -- packet 2 (mines.select-mines-first .. commands.help-reload, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 clean. Context notes hold:
`resplit-offer` as a completed split, three distinct closed-* timer
messages, occupations nominative inside finish-editing, vines as climbing
plants ("klifurjurta"), `none` agreeing with "skraut". Advisory, not
scored: the Dragon Descent name is also inserted bare but stays readable.

KEY: blackjack-settings.split-matching-desc-1
TIER: 1
ISSUE: The Same Rank rule was labelled "Sama gildi spils", nearly a synonym of "Sama virði" (Same Value) and "gildi" is the point value in desc-2; it also mismatched the button "Sama tegund".
SUGGEST: "&7Sama tegund: aðeins spilum af sömu tegund er skipt (K-K)."

KEY: blackjack.closed-before-turn
TIER: 2
ISSUE: "umferð" means round everywhere else; a turn is "röð".
SUGGEST: "&eÞú verður að snúa aftur áður en tímamælirinn fyrir röðina þína rennur út, annars verður „Standa“ valið sjálfkrafa fyrir höndina þína."

KEY: blackjack.leave-exit
TIER: 3
ISSUE: "Standa upp/Hætta" sits beside the Stand action "Standa".
SUGGEST: "Fara úr stól/Hætta"

KEY: mines.rebet-broke
TIER: 3
ISSUE: "Blankur vasi" is an odd collocation; the idiom is "tómir vasar".
SUGGEST: "&cTómir vasar -- ekki nóg til að endurtaka veðmálið."

KEY: blackjack.insufficient-bet
TIER: 3
ISSUE: Same as mines.rebet-broke.
SUGGEST: "&cTómir vasar -- ekki nóg til að veðja."

KEY: blackjack-settings.toggle-aces-hit
TIER: 3
ISSUE: "á skipta ása" mismatches "á skiptum ásum" in the sibling key.
SUGGEST: "Kveikja/slökkva á spili á skiptum ásum"

KEY: blackjack-settings.aces-hit-updated
TIER: 3
ISSUE: Same as toggle-aces-hit.
SUGGEST: "&eSpil á skiptum ásum: {value}"

KEY: admin.delete-confirmation
TIER: 3
ISSUE: "Eyða í alvöru?" is too colloquial for a delete confirmation.
SUGGEST: "Viltu örugglega eyða?"

KEY: admin.slots-rtp-lore
TIER: 3
ISSUE: "endurgreiðsla til leikmanns" uses the plugin's refund word, so the RTP line reads as a current refund.
SUGGEST: "&7Núverandi útborgunarhlutfall (RTP): &a{rtp}"

KEY: rock-paper-scissors-settings.title
TIER: 3
ISSUE: The bare comma-list game name reads as a list, not a title.
SUGGEST: "Stillingar fyrir „Steinn, skæri, blað“ ({dealer})"

KEY: rock-paper-scissors-settings.invalid-settings-option
TIER: 3
ISSUE: Same bare comma-list name.
SUGGEST: "&cÓgildur valkostur valinn í stillingum fyrir „Steinn, skæri, blað“."

KEY: rock-paper-scissors-settings.dealer-not-found
TIER: 3
ISSUE: Same bare comma-list name.
SUGGEST: "&cGjafari fyrir stillingar „Steinn, skæri, blað“ fannst ekki."

KEY: occupations.rps-max-chain
TIER: 3
ISSUE: Inside commands.finish-editing the bare name reads as a list; quoting keeps a nominative noun phrase.
SUGGEST: "hámarksumferðir hrinu í „Steinn, skæri, blað“"

Tier 0: 0 · Tier 1: 1 · Tier 2: 1 · Tier 3: 11

## Disposition (translator)

All 13 applied. `leave-exit` became "Yfirgefa stólinn/Hætta" (the
chair word used by the other leave-chair strings, still distinct from
"Standa"); `delete-confirmation` became "Ertu viss?", the direct
rendering of "Are you sure?". Follow-up in self-review: every other RTP
line moved from "endurgreiðsla" to "útborgunarhlutfall" (guide-machine-rtp,
guide-volatility-normalized, house-edge-current, house-edge-updated,
variance-same-rtp), and slots.partial-return no longer says "Endurgreitt".
