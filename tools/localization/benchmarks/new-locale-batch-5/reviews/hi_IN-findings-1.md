# hi_IN review -- packet 1 (common.current .. mines.place-wager-first, 592 records)

Isolated reviewer; key / English / value only, rubric in
`scratchpad/review/rubric.md`. Tier 0 checks clean (token order in every
record; `/ncc claim`, `off`, `-1`, `11:1`, `0.95:1` intact; danda and “”
quotes). No gendered second-person verbs: seated states use the existential
"आप … सीट पर हैं", other player-subject lines the ergative, dative, nominal
or passive. Context notes hold: `seat-unavailable` "उपलब्ध नहीं" (not
occupied), chain-win `{amount}` as "मौजूदा पॉट", "लगातार जीत" only for the
PvE streak (slots run "क्रम", the auto-spin batch "ऑटो स्पिन शुरू होने के
बाद"), variance tradeoff as hit rate.

KEY: common.return-to
TIER: 2
ISSUE: "वापस जाएँ: {menu}" drifts from "… पर लौटें" in every sibling return button; "{menu} पर" works with any menu name.
SUGGEST: "{menu} पर लौटें"

KEY: player-menu.return-to
TIER: 2
ISSUE: Same drift in the player menu.
SUGGEST: "&d{menu} पर लौटें"

KEY: preferences.overflow.drop-explained
TIER: 3
ISSUE: Label "गिराएँ:" does not match the button "पास में गिरा दें".
SUGGEST: "&7गिरा दें: सर्वर की सीमा तक पास में गिराई जाती है, बाकी सुरक्षित रखी जाती है।"

KEY: cards.ranks.two
TIER: 3
ISSUE: The standard card name is "दुक्की" (pairs with "तिक्की").
SUGGEST: "दुक्की"

KEY: coin-flip.chain-win
TIER: 3
ISSUE: Ergative "आपने जीता!" without an object is awkward; the nominal form matches "आपकी हार हुई!". Pot semantics correct.
SUGGEST: "&aआपकी जीत हुई! लगातार जीत: {streak}। मौजूदा पॉट: &e{amount}&a। कैश आउट करें या फिर से सिक्का उछालें।"

KEY: rock-paper-scissors.chain-win
TIER: 3
ISSUE: Same.
SUGGEST: "&aआपकी जीत हुई! लगातार जीत: {streak}। मौजूदा पॉट: &e{amount}&a। कैश आउट करें या फिर से चाल चलें।"

KEY: rock-paper-scissors.tie
TIER: 3
ISSUE: "Throwing again..." is a status line; the imperative plus ellipsis reads oddly and choice buttons reappear only for seated players (showChoiceButtonsIfSeated).
SUGGEST: "&eबराबरी! फिर से चाल चलने की बारी..."

KEY: dragon-descent.unsafe
TIER: 3
ISSUE: "ख़तरा!" (Danger) drifts from "Unsafe!" and breaks the pair with "सुरक्षित!".
SUGGEST: "&cअसुरक्षित!"

KEY: dragon-descent.dealer-cannot-cover
TIER: 3
ISSUE: "यह उतराई" is an unnatural calque of "This descent" as the payer.
SUGGEST: "&cयह गेम अभी इतना बड़ा पॉट नहीं चुका सकता। बाद में फिर कोशिश करें या अपना दांव कम करें।"

KEY: slots.height-description
TIER: 3
ISSUE: Feminine "दिखने वाली" before masculine "चिह्नों" reads as a mismatch.
SUGGEST: "&7चिह्नों की कितनी पंक्तियाँ दिखें, यह बदलता है।"

KEY: slots-settings.variance-top-line
TIER: 3
ISSUE: Can read as a physical line size; the value is the top multiplier.
SUGGEST: "&7पूरी चौड़ाई वाली लाइन का सबसे बड़ा गुणक: &e{multiplier}x"

## Terminology notes

"Return to …" as "लौटें" vs "वापस जाएँ:"; overflow Drop label vs button;
ख़ nukta used only in "ख़तरा" (polish only: "खत्म" / "आखिरी" drop it).
Consistent elsewhere: दांव दोहराना, सब दांव पर, कैश आउट, पॉट, प्लेयर / बैंकर,
डीलर, कैसीनो का लाभ, खिलाड़ी को वापसी, अस्थिरता, स्लॉट मशीन, रील, पेलाइन,
ऑटो स्पिन, बायाँ / दायाँ क्लिक.

Counts: Tier 0: 0 | Tier 1: 0 | Tier 2: 2 | Tier 3: 9 | Total: 11

## Disposition (translator)

All 11 applied as suggested. Follow-up in the same family:
`blackjack.result-won` now reads "आपकी जीत हुई!" like the chain-win keys.
The nukta note is polish only and was not changed.
