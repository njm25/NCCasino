# NCCasino music composition and MIDI/NBS handoff playbook

Investigated 2026-09-12 against NCCasino baseline `2f1b087` and the bundled
`libs/lib-1.0-SNAPSHOT.jar` (inspected with `javap`), plus the public
[VSE source](https://github.com/NBorow/VSE/tree/main/lib/src/main/java/org/nc/VSE).
This is developer context for subsequent arrangements, not player copy.

## Start here: scope, files, and evidence

Goal: arrange one recognizable real song/riff/section at a time for vanilla
Minecraft, then audition it. Musical fidelity and listening feedback are the
quality gate. Do not substitute vaguely Beatles-like original music for a
requested identifiable piece. A short riff is a valid deliverable when that
is the agreed scope; do not call it the complete song.

All paths below are repository-relative. Read this guide and inspect these
files before editing; method names are more durable than line numbers:

| File | Inspect / purpose |
| --- | --- |
| `src/main/java/org/nc/nccasino/games/Slots/CasinoSongs.java` | The accepted `dayTripper`, `iFeelFine`, and `goldenSlumbers` arrangements plus shared timing/pitch helpers |
| `src/main/java/org/nc/nccasino/games/Slots/SlotsMachine.java` | Golden Slumbers rainbow-housing click integration and lifecycle cleanup |
| `src/main/java/org/nc/nccasino/games/Blackjack/BlackjackInventory.java` | I Feel Fine entrance/countdown integration and per-viewer music channels |
| `src/main/java/org/nc/nccasino/games/Roulette/BettingTable.java` | Day Tripper betting-page loop and bets-closed cleanup |
| `src/main/java/org/nc/nccasino/games/Slots/SlotsSongs.java` | Existing Here Comes the Sun opening score; preserve unless explicitly changing the intro |
| Same directory: `SlotsOpeningColumnMotion.java`, `SlotsTiming.java`, `SlotsControlLayout.java`, `SlotsPaytableLayout.java` | Animation timing, ordinary-click routing, occupied slots |
| `src/test/java/org/nc/nccasino/games/Slots/CasinoSongsTest.java` | Timing, register, and actual bundled VSE playback checks |
| `build.gradle`, `libs/lib-1.0-SNAPSHOT.jar`, `deploy.bat` | Java 21, Spigot 1.21.11, shaded local dependency, deployment |

Library SHA-256 at audit:
`C25B5C6E92EA89DE1776904073CEB0B4B0FAA6FBB803DCAD2BD7E2F90ED7078A`.
The public source was read from `main`; no immutable upstream commit was
recorded. Recheck the shipped JAR if its hash changes. `SingleSongEngine`
is not the playback path used by Slots.

Evidence labels matter: implementation facts below were inspected; all three
accepted arrangements have positive user listening feedback; proposed future
workflow and mix advice are recommendations. No server recording, measured
sample tuning, or model-to-model composition benchmark was produced.

## Accepted production placements

The temporary Slots paytable audition row and the Claude comparison scores were
removed after the final Golden Slumbers smoke test. The paytable's original
Legend card is restored. The retained score factories now have these homes:

- **Golden Slumbers / Slots:** clicking any rainbow housing tile surrounding
  the live reel grid restarts the complete accepted score. Reel cells and the
  Paytable are not triggers. Leaving the live game view, entering a prompt, or
  terminating the session stops/removes the listener.
- **I Feel Fine / Blackjack:** starts for a viewer when that viewer's private
  table-entrance animation finishes. Starting a countdown also starts it for
  any seated viewer who does not already have it playing, which covers later
  rounds without replaying the entrance. It loops if an unusually long
  configured betting timer outlasts the arrangement, and stops exactly when
  countdown zero begins the dealer/start transition. Closing the view or
  tearing down the table also stops it.
- **Day Tripper / Roulette:** starts when the player's BettingTable opens and
  survives page-one/page-two changes because both pages are one table object.
  It loops with a one-second tail after the ending attack, implemented as an
  inaudible length marker because VSE has no rest-duration field. It stops as
  soon as `betsClosed` is received, before the table is covered and before the
  player is moved to the spinning wheel. Closing or leaving the BettingTable
  also stops it. The legacy per-second and five-second accelerating betting
  timer cues are removed so they cannot drift against the music; wheel/ball
  effects remain.

All three obey the player's sound preference at start. Roulette and Blackjack
use UUID-scoped channels so one player's late open/restart cannot reset another
player's music.

## Existing Slots intro

`SlotsMachine.beginOpeningAnimation` captures the finished inventory, clears
it, then feeds nine columns with rainbow panes and their actual final items.
Each column has two passes of nine filler items plus six final items (30
entries). Start offsets are 0, 2, 5, 6, 9, 11, 12, 15, 17 ticks. The final
four advances decelerate. `SlotsOpeningColumnMotion.finalTick(9, 30)` is 66;
the animation runnable starts after one tick. This is separate from the
casino-wide `AnimationMessage` / `AnimationSongs` system.

`SlotsSongs.getOpeningIntro` builds the existing Here Comes the Sun phrase.
The code attributes the onsets to an acoustic-guitar MIDI transcription at
128 BPM, but does not retain the MIDI URL/file, so that provenance has not
been independently verified. It combines harp double-stops with single bass
plucks. `round(seconds * 20) + 10` schedules its 20 sound events from tick
10 through tick 71. Equal start/end ticks allow natural sample decay.

Several comments are stale: the final 3.038-second chord actually lands at
tick 71, not 66, and the 2.575-second double-stop lands at tick 62. The final
chord has three emitted voices, despite comments calling it four-voice.
Normal animation completion leaves the music running; skip cancels future
notes; session cleanup removes the listener and stops known sample sounds.
The existing sound and animation have deliberately been preserved.

## VSE behavior verified in source and the shipped JAR

- `Song` is a title, an unused tempo integer, and a mutable list of notes.
- `MultiChannelEngine` schedules one synchronous Bukkit task every server
  tick. Each channel contains listeners and active songs; channels are
  logical groups, not MIDI instruments or isolated client audio buses.
- `ActiveSong` starts at tick zero. All events matching the current tick
  play together at each listener's location. Playback is normally 20 ticks
  per second; server lag slows both the score and animation.
- `Note(sound, start, end, pitch, volume)` with `start == end` is a one-shot:
  it is never marked active, so its sample decays naturally. `end > start`
  causes a stop at the end; it cannot stretch a sample into a held note.
- String sound notes call `Player.playSound` without a category. The
  category stored on such a Note is not used for playback. Ending a string
  note stops that sound across categories, potentially cutting other pitches
  of the same instrument. Avoid these duration stops for overlapping plucks.
- The Bukkit `Sound` overload does use a category; VSE may switch MASTER
  to MUSIC when the same instrument is active. This is not general polyphonic
  note-off isolation and can change mixer behavior. Keep the proven string
  one-shot approach unless a different approach is deliberately tested.
- The last event is emitted before a non-looping score ends. Natural sample
  tails remain audible afterward. A fresh `Song` is constructed per audition.
- Song length is the maximum `endTick`, not a separate duration field. There
  is no explicit rest event or trailing-rest API. A gap needs no note, but
  do not extend a sounding note's end just to hold the scheduler open: that
  changes its stopping semantics. Loop length/precise silence needs its own
  design and tests. This audition is non-looping.
- Tick zero means the next engine update, not an immediate synchronous sound
  when `playSong` returns. Independent animation/audio tasks can have a tick
  of phase difference; derive alignment from actual scheduling, not comments.
- `stopSong` marks the song stopped; `Channel.tick` removes stopped songs.
  It does not reliably silence one-shot tails. Remove the listener as well
  when stopping/restarting: the engine stops all sound names known to that
  channel. This can affect other uses of those sound names for that player.
- Do not use `removeChannel` as an audition reset: removing the last channel
  cancels the engine ticker, which adding another channel does not restart.
- Termination removes listeners, then calls `shutdown` to cancel the
  per-machine ticker. Simply removing listeners would leave that task alive.
- README remarks about note endings are older than the actual code. Use the
  shipped JAR as the authority if upstream changes.

## Translating music to vanilla sound events

Pitch is a playback-rate multiplier, not a linear note number. Twelve equal
semitones make an octave: `pitch = 2^((targetMidi - sampleReferenceMidi)/12)`.
Use an instrument-specific reference. The conventional vanilla registers at
pitch 1 are harp F#4 (66), guitar F#3 (54), bass F#2 (42). Thus the same
multiplier on guitar and bass sounds an octave apart. Keep the new score
inside 0.5..2.0; change voicing/octave intentionally rather than clamp notes.
Resource packs can change tuning/timbre; confirm the result on the target client.

| Sound suffix (`minecraft:block.note_block.`) | Reference at pitch 1 | Working range at 0.5..2.0 | Role used here |
| --- | --- | --- | --- |
| `harp` | F#4 / MIDI 66 | F#3..F#5 / 54..78 | Clear upper melody / chord color |
| `guitar` | F#3 / MIDI 54 | F#2..F#4 / 42..66 | Plucked riff |
| `bass` | F#2 / MIDI 42 | F#1..F#3 / 30..54 | Low foundation / octave doubling |

Use MIDI scientific notation (`C4=60`, `A4=69`); guitar staff notation often
sounds an octave below written pitch. Guitar fret conversion is open-string
MIDI + fret + capo semitones, using actual tuning; do not count the capo twice.
With A4=440 Hz, `frequency = 440 * 2^((midi-69)/12)`,
`midi = 69 + 12*log2(frequency/440)`, and
`pitch = targetFrequency / sampleReferenceFrequency`.
For example E3=52 on guitar gives `2^(-2/12) ~= 0.890899`; E2=40
on bass uses the same multiplier. F#4 on guitar is exactly 2.0.
Fractional MIDI/cents can represent tuning deviations if supported by the
sample, but do not add them without evidence. For an unfamiliar sound,
verify its reference and stable tonal content first; the universal formula
does not imply a universal sample reference or turn noise into a pitched voice.

The old intro's universal-MIDI-reference comment confuses shared pitch
*classes* with shared octaves: its bass is nominally two octaves below its
commented MIDI register. Preserve its approved sound, but do not reuse that
assumption for new arrangements.

Convert quarter-note beat positions with `round(beat * 1200 / BPM)`.
Compute each absolute onset, rather than repeatedly adding a rounded beat
length; this distributes rounding error and avoids tempo drift. Eighths are
0.5 beats, sixteenths 0.25; at 20 TPS some expressive microtiming disappears.
Use a tempo map when importing MIDI with tempo changes. Guitar tablature
spacing alone is not reliable timing notation.

At 138 BPM a quarter is 8.69565 ticks, an eighth 4.34783. Absolute rounding
has at most 0.5 tick (25 ms at 20 TPS) onset error; rounding every quarter
to 9 ticks instead would drift by more than 7 ticks over 24 beats. Inspect
collisions where two intended successive notes round to one tick. For swing,
triplets or pickups, preserve their actual beat positions; do not force equal
eighths. A 6/8 bar is three quarter-note beats if this formula's BPM is quarter
notes; a dotted-quarter tempo must be converted accordingly.

For MIDI PPQ timing, accumulate delta ticks and integrate tempo segments:
`seconds += deltaMidiTicks / PPQ * microsecondsPerQuarter / 1_000_000`.
Tempo events may live on a separate track. Normalize the selected section's
start once for all parts, preserving pickups and leading rests. Treat
velocity-zero note-on as note-off. Reject or explicitly handle SMPTE timing.
Neither tempo-map import nor MIDI parsing exists in the current plugin;
these are preparation steps, not capabilities of VSE's `Song.tempo` field.

Preserve the motif's intervals, rests and accents first. Chords are
simultaneous events, bass supplies register and harmonic grounding, and
quiet percussion supplies pulse. Dynamics are relative volume multipliers,
not MIDI velocity or acoustic decibels. Vanilla plucked samples have fixed
envelopes: slowing them lowers pitch and lengthens decay. Dense low chords
get muddy. Duration metadata cannot synthesize sustain, vibrato, slides or
distortion. Other Minecraft sound events may select random variants and
are better suited to percussion/effects than a precisely tuned lead.

Start with melody alone, then one bass voice, then sparse harmony/percussion.
Protect signature non-chord tones: Day Tripper's G-to-G# and D/F# color must
not be "corrected" into plain E-major scale notes. Preserve bass motion when
it defines the song; doubling the melody is not a universal bass recipe.
Transpose a complete phrase/part coherently and document the interval. Prefer
an instrument/register change over individually folding notes across octaves,
which can reverse the melody's contour. Spread chord voices instead of
stacking many low roots; omit redundant doublings before distinctive thirds
or sevenths. A bass note can imply a chord without a full strum on every beat.
When a dedicated lead instrument cannot cover a source melody's complete span,
try one coherent transposition of every pitched part before octave-folding the
lead or changing its instrument mid-phrase. Record both keys; leave unpitched
percussion unchanged and recheck every part against its own sample reference.

A rest means no new onset, not necessarily silence: prior plucks still ring.
Use inter-onset spacing and volume to imply articulation. Repeated attacks
sound like tremolo, not a genuinely held string. Explicit note-off support
needs separate validation because sound-name stops are not per-pitch stops.
Same-tick notes form chords without a strum; a one-tick strum is already
50 ms, so do not add delays indiscriminately. Keep drums quiet enough that
rhythmic clarity improves without covering the motif. Do not assume a value
of 0.4 has equal perceived loudness across different samples or client mixers.

## Accepted arrangement: Day Tripper

`CasinoSongs.dayTripper`: three two-bar phrases at an arranged 138 BPM,
then a final E-major chord at tick 209 (10.45 seconds plus natural decay).
Guitar enters alone; octave-lower bass enters on the second phrase; light
hat, kick and snare enter on the third. Repeat count, orchestration, tempo
and ending are our arrangement, not a claim to reproduce the full recording.

The riff combines the blues minor-to-major third movement with dominant
seventh/ninth color. Its held syncopations are crucial: replacing the rests
with evenly spaced notes loses the recognizable groove. The guitar part is
voiced up an octave from low guitar register to fit the vanilla sample.

Day Tripper now belongs to Roulette's per-player BettingTable. Its loop factory
adds an inaudible tick-229 marker, creating roughly one second between the
tick-209 ending attack and the next loop. See Accepted production placements
for its start/stop lifecycle. Keep the base factory/test as a timing reference;
intro replacement is a separate decision.

## Research and arrangement workflow

1. Establish the song version and exact section: recording/link, timestamps
   or measures, pickup, ending, approximate length. If the choice is yours,
   choose a short distinctive motif with obtainable rhythm/pitch evidence.
   A recognizable hook with sparse accompaniment is a better first experiment
   than a dense full-band section whose identity depends on vocals/timbre.
2. Find actual musical evidence. For Day Tripper, Pollack's annotated beat
   grid plus Hein's independent analysis agreed on the signature syncopation
   and pitches. Inspect sources, not just search snippets. Prefer a published
   score or clearly attributed transcription plus recording/reference check.
   A chord sheet establishes harmony but usually cannot establish melody;
   an unlabeled MIDI is a candidate transcription, not the original recording.
3. Write a small score plan before Java: meter and tempo unit; each part's
   sounding notes with octave, beat onset, musical duration/rest, accent;
   section boundary and harmonic changes. Mark uncertain notes/timings. Resolve
   discrepancies against a second source or the recording where accessible.
   Do not fabricate "verified transcription" claims if listening is unavailable.
4. Identify what makes this section recognizable: contour, signature interval,
   syncopation, bass figure, chord change or texture. Keep those elements.
   Choose sample/registers to fit the entire pitch span; then choose explicit
   reductions, tempo adjustments, repeats and an ending. Record which decisions
   adapt the source rather than transcribe it.
5. Implement the motif first with named MIDI values/annotated arrays and
   absolute beat timing. Add bass/harmony only where they improve identity.
   Check min/max pitch, same-tick collisions, density, phrase boundaries and
   final decay. Do not squeeze the entire passage into the old intro's length;
   the paytable audition is independent of the falling-column animation.
6. Verify mechanically, audition, and revise one audible dimension at a time:
   wrong tune -> pitches/octaves; wrong groove -> onsets/rests; muddiness ->
   register/doublings/volume; weak identity -> missing signature line/harmony.
   Record the user's feedback and the exact revision it concerns. Stop after
   this one composition is ready for audition; do not batch new songs.

For each future song, append a compact record here (or link a dedicated note):
title/version + section; source URLs/date/track/measures; original and arranged
key/register/BPM; motif/event representation; omissions and added ending;
sound IDs/volumes; factory and audition slot; tests/build; listening feedback
and unresolved questions. If using an external MIDI, retain its source URL,
track selection, tempo-map method and hash; retain the file only when appropriate.

## Day Tripper lessons and confidence limits

- The user positively evaluated the audible result on 2026-09-12. There was
  no detailed per-part critique. The agent did not directly hear the result,
  measure sample frequencies, or perform recorded A/B comparisons.
- One implemented musical version received that feedback. No failed musical
  variants or listening-led revisions preceded it; do not invent trial/error.
  Fixes during implementation concerned cleanup routes and documentation.
- What supported the result: independently corroborated rhythm, retaining
  syncopated gaps, fitting guitar E3..F#4 without clipping, bass an octave
  below, gradual layering, and a finite cadence. These are plausible reasons
  for success, not separately proven causes of the user's preference.
- Reusable mix starting point: guitar 0.52, accented root/D/F# 0.65; bass
  0.42; hat 0.08/0.12 off/on beats; kick/snare 0.22. The final chord mixes
  E2 bass, guitar E3/B3/E4 and quiet harp G#4. Values are examples, not a
  preset suitable for every song. It contains 84 attacks, not 84 melody notes.
- The first phrase's starts are ticks
  `0,13,17,22,26,30,43,48,57,61,65`; the bass joins at tick 70;
  the last chord is tick 209. This is an arranged 138 BPM choice, not a
  measured assertion about the recording's exact tempo.
- What was misleading: old intro comments claimed identical octaves across
  instruments and chord/animation alignment that the code does not implement.
  Their sound was accepted, so the audition did not retune the intro.
- Next time: record source/section and arrangement choices before coding;
  get focused listening feedback on the motif and balance; retain accepted
  revisions. Inspect samples or a tuner if a new instrument's register is
  uncertain. A rendered MIDI preview can check notes/rhythm but does not
  validate vanilla timbre, decay, client settings or VSE cleanup.
- Current tests check selected pitches/onsets, all pitch bounds, event count,
  completion and no string note-off calls. They do not compare every emitted
  pitch against the source, exercise `SlotsMachine` end-to-end, prove subjective
  fidelity, or test all close/replay/network cases. Add targeted tests when
  new routing, durations or timing behavior is introduced.

## Accepted arrangement: I Feel Fine

`CasinoSongs.iFeelFine` arranges the original single's eight-bar opening
riff twice: D7-C7-G7-G7 in the source, coherently transposed down one semitone
to C#7-B7-F#7-F#7. At 180 BPM, the second pass and final F#-major chord end at
tick 427 (21.35 seconds plus natural decay). Guitar presents the first pass
alone; low root anchors and quiet hat/kick/snare enter for the second pass.

Sources checked 2026-09-12: the licensed Hal Leonard Guitar Tab Play-Along
preview at `https://www.guitarinstructor.com/product/guitar-tab-play-along/the-beatles/i-feel-fine/1000004901`
for the 180 BPM swung-eighth notation and fret sequence; Howard Wright's
independent transcription at `https://www.hakwright.co.uk/music/tab/feel_fine.shtml`
for the movable D/C/G fret shapes; and Alan W. Pollack's analysis at
`https://www.recmusicbeatles.com/public/files/awp/iff.html` for G major, 4/4,
the initial A implication and the opening dominant chain. No external MIDI was
used. The arrangement preserves the riff's root-fifth-octave-flat-seven arc,
descending upper line and long-short swing. It omits the recording's feedback
swell because one-shot vanilla samples cannot reproduce its continuous growth.

The one-semitone transposition is a range adaptation: the original G2..G4 span
misses the vanilla guitar's F#2..F#4 working range by one semitone at the top,
whereas F#2..F#4 fits exactly without octave folding or a mid-riff instrument
change. All events are one-shots. Guitar uses volume 0.48..0.62, bass 0.34,
hat 0.07..0.09, kick 0.18, snare 0.15, with a quieter harp major third only in
the final chord. The second pass and button ending are arrangement choices.

I Feel Fine now uses a UUID-scoped Blackjack channel. It starts after the
private table entrance (and at later-round countdown start if needed), loops
only when the configured pregame interval outlasts it, and is stopped at the
countdown-to-dealer transition. Focused tests cover the swung onset grid,
transposed chord roots, register bounds, 157 emitted attacks, final-event
playback and natural tails. The arrangement and Golden Slumbers extension
received positive in-game listening approval before placement.

## Accepted arrangement: Golden Slumbers

`CasinoSongs.goldenSlumbers` covers all of the original Abbey Road
recording from its first piano attack through Golden Slumbers' own final
two-beat transition fill, stopping before the Carry That Weight downbeat at
about 1:31. The accepted opening remains in its original helper methods: 471
attacks through tick 985. The extension begins on the immediately following
scheduler tick, tick 986, with no artificial comparison silence. The complete
arrangement has 911 one-shot attacks and ends at tick 1853; the modeled but
deliberately omitted Carry That Weight downbeat is tick 1860.

The remainder is checkpointed as: chorus close (recording about 0:50-1:02,
VSE ticks 986-1237), quiet homeward return (about 1:02-1:20, ticks 1238-1586),
and final lullaby/cadence plus transition fill (about 1:20-1:31, ticks
1593-1853). The source key C major remains coherently transposed up a major
third to E major so the complete source vocal range fits vanilla flute without
octave folding.

Sources checked 2026-09-12: Alan W. Pollack's analysis at
`https://www.recmusicbeatles.com/public/files/awp/gs.html` for form, harmony,
piano figuration, orchestral entrances and dynamics; Patrick S. Gutman's UCLA
dissertation *Come Together: A Compositional Analysis of The Beatles' Abbey
Road Album* at `https://escholarship.org/uc/item/15t9f8kr` for the notated
vocal figures, 4/4 parsing, one-bar intro, 10.5-bar verse, 9.5-bar refrain,
instrument list and piano/bass/drum texture; Hooktheory's analysis at
`https://www.hooktheory.com/theorytab/view/the-beatles/golden-slumbers` for an
independent 81 BPM/C-major/4/4 check; and BitMidi's
`https://bitmidi.com/the-beatles-golden-slumbers-mid` as a non-authoritative
onset/voicing candidate cross-checked against the analyses. The downloaded
MIDI's SHA-256 was
`849EE12550BEDB64AC1EF0C6CB8FFD000B62B9A264334952D568B8FD204EF4E7` and it
was not retained in the repository. For the extension, the official 2009
remaster (`https://www.youtube.com/watch?v=AcQjM7gV6mI`) was the released-form
authority, and Aaron Krerowicz's form
chart (`https://www.aaronkrerowicz.com/beatles-blog/category/golden%20slumbers`)
independently fixed the chorus at about 0:34-1:02 and second verse at about
1:02-1:31. Pollack's Carry That Weight analysis and Gutman's transition
discussion establish that the two-beat fill completes Golden Slumbers' final
measure; inspection of the matching MIDI confirmed that its Golden Slumbers
tracks stop before beat 130 and the separate Carry That Weight tracks start
exactly on beat 130.

The 911 one-shot events use flute for the complete independent vocal melody,
harp plus guitar-register notes for the rocking piano, bass for George's line,
chime for strings shifted two additional octaves as a coherent section, low
didgeridoo for refrain brass weight, and bass-drum/snare/hat sounds for the
fill and refrain accents. Quiet renewals simulate only the longest vocal holds;
all events retain natural sample tails. The loud chorus continues directly
with the full ensemble, the second verse reduces to rocking
piano, sparse bass slides, soft cymbal downbeats and lighter orchestral
countermotion, and the final cadence rebuilds into the Golden Slumbers-side
two-beat drum fill. Any non-reel rainbow housing tile in the live Slots canvas
restarts this score; the temporary paytable trigger no longer exists.

Focused tests cover the unchanged 471-event prefix and tick-985 endpoint, the
immediately consecutive first extension event at tick 986, source vocal
onsets and peak, the beat-46 drop, all nine sound roles, 0.5..2.0 pitch bounds,
911 emitted attacks, no duplicate extension attacks, final tick 1853,
pre-Carry boundary, non-looping completion and natural tails. The continuous
join and complete score passed the user's final in-game smoke test.

## Retired experiment research notes

The following four arrangements were removed from executable source during the
three-song cleanup. Their research/arranging notes remain here intentionally as
local reference for possible future MIDI/NBS refinement; they are not shipped,
not reachable in any game UI, and not approved production scores.

### Retired experiment: Buckets of Rain

The removed `bucketsOfRain` factory covered Bob Dylan's released *Blood on the
Tracks* take from the first guitar attack through the tonic guitar resolution
after the first complete vocal verse, approximately 0:00-0:33.1. The vocal
enters around 0:17.45 and its last phrase ends around 0:32.17; the following
instrumental break and all later verses are excluded. The 48-quarter-beat score
uses the published quarter-note tempo of 87 and ends at tick 662.

Sources checked 2026-09-12: Bob Dylan's official-audio YouTube release and its
caption timing at `https://www.youtube.com/watch?v=jGsOmKZXDvo` for the released
performance and phrase boundary; Eyolf Ostrem's Dylanchords transcription at
`https://www.dylanchords.com/16_bott/buckets_of_rain` for open-D/E tuning,
voicings, the repeated 12-measure form and the ambiguity between A9 and Esus4
over the unplayed fifth string; the licensed Hal Leonard/Musicnotes preview at
`https://www.musicnotes.com/sheetmusic/bob-dylan/buckets-of-rain/MN0066142`
for the notated arpeggio, chord inversions and quarter=87; Cifra Club's reviewed
tab at `https://www.cifraclub.com.br/bob-dylan/buckets-of-rain/` as an independent
check on open E (E-B-E-G#-B-E), alternating thumb, slides and pull/hammer figure;
and Thomas Koozin's University of Houston analysis at
`https://uh.edu/~tkoozin/semiotics/Dylan-abstract.pdf` for independent confirmation
of the album take's open-E tuning and E-major key. No external MIDI was used.

The complete arrangement is transposed down a perfect fourth from E major to
B major. This puts the source's high B4 upper-guitar note at vanilla guitar's
F#4 ceiling while keeping the open low-E thumb as sounding B1 on the bass
sample. The guitar is deliberately split: bass sample carries the low thumb,
guitar carries the alternating inner string and all upper-string dyads,
slides, pull/hammer attacks and inversion colors. Sparse low harmonic anchors
represent the separate bass-guitar movement without thickening the recording's
two-instrument texture. Harp carries the complete lead-vocal contour one octave
above its transposed source register so it remains distinguishable from the
guitar; quiet renewals support only longer syllables. All 265 events are
one-shots and retain natural sample tails. Its former paytable trigger and
executable factory were removed in the three-song cleanup.

Reusable sparse-fingerstyle lesson: model the thumb, inner-string pulse and
upper melody as concurrent strands even when they come from one guitar. Assign
each strand to the nearest usable sample register before transposing the whole
texture; otherwise a block-chord reduction erases the alternating-bass feel,
and octave-folding individual high notes reverses the recognizable upper line.
Under vocals, reduce accompaniment attack volume rather than deleting the
pattern, and preserve moments where the upper guitar answers a vocal rest.

Focused tests cover the vocal and staggered guitar entrances, alternating thumb,
A/E bass color, 0.5..2.0 pitch bounds, 265 attacks, tick-662 completion, natural
tails, non-looping VSE playback and four collision-free top-row audition slots.
In-game listening remains required for harp-vocal intelligibility, thumb/inner
balance, the high B/E-to-A/E descent and the naturalness of the final cadence.

### Retired experiment: Yer Blues

The removed `yerBlues` factory covered the released Beatles recording from
Ringo's count-in through the E downbeat resolving the first complete vocal
blues form, approximately 0:00-0:31.35 (the analytical boundary is about
0:32). The following "In the morning" verse and every later faster middle,
verse and solo are excluded. The form is twelve slow 6/8 bars with an
eight-eighth-note bar ten: E7-E7-E7-E7, A7-A7, E7-E7, G, expanded B7, then
E-G-A-G / E-D-B and the next E downbeat.

Sources checked 2026-09-13: the official Beatles 2009 remaster at
`https://www.youtube.com/watch?v=HEQQ-1rd4A0` for the released version;
Aaron Krerowicz's formal chart at
`https://www.aaronkrerowicz.com/beatles-blog/formal-structure-in-beatles-music-139-yer-blues`
for the 0:00-0:03 pickup, 0:03-0:32 first verse and dotted-quarter=52 pulse;
Alan W. Pollack's analysis at
`https://sergegirard.be/Notes_on_Yer_Blues` for E blues, the 12-bar form,
extra beat in bar ten, altered chords and E-G-A-G / E-D-B turnaround; the
licensed Musicnotes lead sheet and guitar/vocal previews (MN0101961 and
MN0055340) for E major, dotted-quarter=51, vocal contour, two-guitar texture,
riff double-stops and full bends; the independent studio-recording guitar tab
at `https://www.gotabs.com/beatles/yer-blues-tab` for sounding fret pitches and
the descending B-A-G#-G-F# figure; and the reviewed Cifra Club plus independent
BigBassTabs transcriptions for the root pulses, IV/V approaches and turnaround
bass motion. No external MIDI was used. The Minecraft Wiki note-block table
was checked before adding `block.note_block.bit`: its square-wave range is
F#3..F#5, so pitch 1 is treated as F#4 / MIDI 66.

The complete pitched arrangement is transposed up a whole step from E blues
to F# blues. Guitar sample carries Lennon's source-register low E3, B3/D4
double-stops, G-to-A bends and the dense dominant/power voicings after that
coherent shift;
quiet Bit attacks represent the harder upper guitar responses and chromatic
fall. Bass sample follows its own E2/A2/G2/B2 roots, repeated pickups and
E-G-A-Bb-B / E-D-B turnaround movement. Low hat, kick and late snare attacks
retain the dragged 6/8 pocket, with extra turnaround accents. Flute carries
the complete independent vocal contour. The lead sheet's octave-raised
E4..A5 contour becomes F#4..B5 along with the band, clearing flute's lower
boundary; no individual note is independently shifted or octave-folded. Long
syllables get one quiet renewal.
Fractional pitches between G and A make bends read as scoops and releases,
and roughly one-tick guitar/backbeat offsets keep the dense attacks from
landing on a sterile common grid. The 381 one-shot events end at tick 627.

Reusable dense-blues lesson: separate distortion *functions*, not merely
nominal players. A midrange plucked sample can carry the recognizable body
while a much quieter square-wave voice supplies upper-edge attacks and bends;
giving both equal volume creates masking rather than useful distortion.
Represent a bend with a sparse attack contour (start, one or two fractional
MIDI intermediates, destination, release), reserving repeated attacks for
exposed responses. On a 20 TPS scheduler, a deliberate one-tick late backbeat
or response is audible; encode it as an absolute-time offset and keep bass and
the primary riff closer together so "loose" does not become incoherent.

Its former paytable trigger and executable factory were removed in the
three-song cleanup. Historical focused tests covered the altered bar-ten timing, independent vocal,
guitar and bass parts, 0.5..2.0 pitch bounds, 381 emitted attacks, tick-627
completion, natural tails and non-looping playback. In-game listening remains
required for the flute-vocal register, Bit edge, bend retriggers, dragged
backbeat, low-end congestion and final turnaround resolution.

### Retired experiment: Virtual Insanity

The removed `virtualInsanity` factory covered Jamiroquai's released official-video/
single arrangement from its first piano attack through the resolving Eb-minor
downbeat after the first complete chorus, approximately 0:00-1:08.8. The next
verse is not started. The score stays in the original sounding Eb minor at 92
BPM. Its verse harmony is encoded as a 14-quarter-note cycle (three 4/4 bars
and a 2/4 turnaround); the chorus is four two-bar cells and begins at tick 959
(about 0:47.95). The final resolution is tick 1376 (about 1:08.8).

Research used the official video (`https://www.youtube.com/watch?v=4JkIs37a2JE`)
as the performed-version and timing authority; the licensed Musicnotes singer
score MN0068259 for its Eb4-Db6 notated vocal span and 92 BPM marking; Bryan
Beller's Bass Player transcription (republished at
`https://doczz.net/doc/2950835/thumbs---nxtbook-media`) for the 92 BPM pulse,
Eb-minor harmony, late first-verse bass entrance and fingerstyle-to-slap change;
Guitar World's isolated bass/drum analysis for the three-bars-plus-2/4 verse
cycle and bass/kick relationship; Drumscore's published chart metadata for
4/4, secondary 2/4, 92 BPM and Derrick McKenzie's drum part; and independent
E-Chords plus Chords-and-Tabs charts for the extended chord names and the
0:48 chorus / 1:09 next-verse boundaries. A public MIDI candidate (BitMidi
62269, SHA-256
`BD17AAA7668D317F9535B4AD2B7A1391134093DE171E5DA005DEA2C8A12FED8A`)
provided the vocal onset/pitch detail after alignment to those recording
boundaries; it was cross-checked against the licensed range and was not treated
as the released recording.

The arrangement separates low piano answers (`guitar`) from extended upper
voicings (`harp`) and a sparse signature top line (`bit`). Bass uses a short
fingerstyle entrance before reproducing the chorus's slap/pop contour as a
separate voice. Flute carries 158 complete vocal attacks in the original
published register, with its chorus level raised independently. Quiet chime
triads enter only in the chorus. Straight sixteenth hats use alternating
dynamics; only the intervening hats and backbeats receive a deliberate one-tick
delay, leaving keyboard lows, bass and kick aligned. The score has 1,416
one-shot attacks. Its former paytable trigger and executable factory were
removed in the three-song cleanup.

### Reusable dense-funk timing and masking lesson

At 20 ticks per second, preserve the pocket by classifying attacks as anchors
or feel notes before adding offsets. Keep the kick, bass note and low keyboard
answer on the same absolute tick when they define the groove; delay selected
backbeats or intervening hi-hats by one tick, rather than shifting an entire
part. This makes the offset audible without weakening the rhythm section's
lock. For extended keyboard harmony, split functions by register and attack:
one low-mid note, a quieter upper extension voicing a few ticks later, then a
single anticipated color tone. During a denser chorus, add a quiet sustained-
sample color at harmonic boundaries instead of duplicating every piano attack.
Test peak same-tick density as well as total event count; masking is usually
caused by coincident attacks, not merely by a long score.

### Retired experiment: Devil in a New Dress

The removed `devilInANewDress` factory covered Kanye West's released album
recording from the opening through the complete first refrain and twelve-bar
first verse, ending on the refrain-return downbeat and short sample answer
(about 0:00-1:13.5). It deliberately stops before the next refrain vocal,
the second verse, Mike Dean's later guitar material and Rick Ross's verse.

The score retains the original 80 BPM, 4/4 pulse and G-sharp-minor/B-major
pitch collection. Its recurring four-bar cell uses Emaj9 for two bars,
D#m7/9 for two bars, then the recording's brief G#m color at the turnaround.
The sampled Smokey Robinson recording is independently charted near 75 BPM in
B-flat major; raising tape/sample speed to about 80 BPM also raises it about
1.1 semitones, matching the released beat's B-major collection. This supports
a coherent roughly one-semitone source-sample transposition rather than
unrelated per-note pitch changes.
Evidence was cross-checked among the released recording's published structure,
the contemporaneous GOOD Friday release, Kesh's piano tutorial/MIDI page,
independent GuitarTabsExplorer and Chordify chord charts, Songparts' part and
section analysis, BassTabz and GuitarTabCreator bass transcriptions, and
Reverb's Bink! production account. Reverb establishes that the beat combines
chops from the middle and end of Smokey Robinson's recording rather than one
unchanged loop.

The arrangement separates close-voiced `harp` sample harmony, lower `guitar`
keyboard answers, `bit` sample-vocal fragments, high quiet `chime` string
sheen, the transcribed moving `bass` contour, and grounded drums. The bass
keeps the E-C#-B-G# and D#-C#-B-G# motion plus the rising turnaround instead
of duplicating roots. Off-eighth hats and both backbeats receive a deliberate
one-tick delay; kick, bass and low harmony remain anchors on the absolute grid.

Kanye's opening ad-libs and all attacks in the first refrain and first verse
are a dedicated `flute` voice. The ad-libs and clear phrase-ending inflections
retain pitched contour; speech-like bars use narrow repeated-note centers,
syllabic entrances, accents, rests and falling cadence tones without inventing
a continuous melody. This voice is coherently displaced one octave upward to
fit flute's vanilla range; every other pitched part remains at source pitch.
The score had 1,123 one-shot attacks and ended at tick 1,470 (73.5 seconds).
Its former paytable trigger and executable factory were removed in the
three-song cleanup; the original standalone Legend card has been restored.

### Reusable rap-and-sample arranging lesson

For speech-like rap, transcribe attacks before pitches: divide the performance
at breaths/cadences, place syllabic attacks and rests, mark stresses, then use
a narrow repeated-note center with only clearly audible rises, falls and
phrase-ending tones. Keep sung hooks or ad-libs as separately pitched phrases.
If the chosen vanilla voice cannot reach the speaking register, displace that
entire voice by one octave rather than folding individual syllables.

For a lush sample loop, split functions by source behavior rather than making
one large chord: low harmonic attack, close common-tone upper voicing, chopped
melodic/vocal fragment and quiet long-decay sheen. Offset the upper chord only
when the source attack blooms behind the low note, omit redundant roots, and
thin the sample fragments under dense rap. This preserves richness while
leaving an audible center lane for the lead and avoids same-tick masking.

## Reusable lessons: staggered intros, drops, and validating a MIDI

Generally reusable technique notes, not a record of any particular song and not
a comparison between arrangements.

**A staggered-entrance intro is a layering plan, not a volume ramp.** When an
analysis names the order instruments join (guitar, then bass, then a second
guitar and a shaker, then the kit), encode that order literally: one loop over
the repeated cell with a guard per layer reads better than five hand-written
passes and cannot drift out of sync. Two points that are easy to get wrong:
an instrument that joins *in unison* cannot be represented by a second attack
of the same sample on the same tick, because that is only a volume increase --
move the added instrument to a different sample and register (a quiet
square-wave octave above a plucked riff reads as the extra guitar's edge), and
two percussion roles that would use the same noise sample (an overdubbed
shaker and the kit's closed hat) should be merged into one harder-played part
rather than stacked as two identical attacks on the same tick.

**Build a dynamic drop out of voice count, not gain.** If the source's
constant element (a piano figure, an ostinato) really does continue unchanged
through the entrance, leave it unchanged and let entirely new samples --
drums, a bass, brass stabs, a high sustained colour -- carry the contrast;
raising everything by a fixed factor sounds like a volume knob, not an
arrangement. Assert the contrast in tests as *distinct instrument names before
versus after* and as peak same-tick density, not only as a maximum volume.
Then look for redundant doublings the new layer creates: once a bass part
enters playing the same roots a keyboard left hand was holding, dropping the
keyboard's low octave and keeping only its upper one removes a same-pitch
duplicate that was costing headroom without adding information.

**Validate a MIDI structurally before trusting a single note of it.** A public
MIDI is a candidate transcription. Before using its onsets, check three things
that do not depend on the file itself: its bar-by-bar pitch-class content
against an independent chord chart; its repeated-phrase map (phrase A B C D |
E F C D | A B C D) against the released lyric form; and its melody register
against the singer's actual range. Also check the implied duration -- a
constant-tempo file's section length is a tempo cross-check against a
published tempo marking and against any timestamp the listener gave you. Two
files can look like independent sources and not be: identical note counts and
pitch ranges per part mean one is a re-export of the other. A re-export is
still useful, though -- when it writes the same melody an octave apart for a
different patch, the lower copy is usually the sounding vocal register and the
higher one an accommodation for a flute or lead patch. Retain the URL, hash,
PPQ, tempo events and how many empty lead-in bars you removed.

**Melody-track sixteenth runs are usually real.** Before dismissing a run of
equal short notes on one pitch as a sustain hack, check its note-off times: if
the run ends with a longer note and the group's syllable count matches a lyric
line, it is a patter figure, and flattening it into one held note destroys the
line's rhythm. A genuine sustain simulation instead repeats to the end of the
value with no closing long note.

## Deferred MIDI -> Note Block Studio -> NBS -> VSE batch pipeline

This pipeline is intentionally documented but deferred. The current release
focus is Slots, localization, and the Citizens extension; do not restart MIDI
collection or add more songs unless that scope is explicitly reopened.

### Purpose and boundary

The Rust MIDI collection is useful chiefly as a melody/countermelody/vocal
source. It should accelerate a recognizable first pass, not be treated as a
finished Minecraft arrangement. The target is a clean `.nbs` intermediate
whose melodic identity can be imported into Java/VSE, followed by deliberate
Minecraft-specific bass, harmony, percussion, dynamics, register, and ending
work. Keep the source MIDI, normalized NBS, generated Java, and human revision
as distinct artifacts so no lossy step is mistaken for the original.

### 1. Intake and inventory

For every candidate, create a manifest row containing: stable song ID; display
title; source MIDI path and SHA-256; known recording/version; intended excerpt;
MIDI PPQ; tempo/time-signature changes; track/channel names; program numbers;
note range per track; first/last onset; duration; and any known Rust-specific
assumptions. Do not batch anonymous files directly into production.

Before Note Block Studio, reject or flag malformed files, empty tracks, stuck
notes, obviously wrong tempo maps, enormous silent prefixes, duplicate tracks,
and percussion encoded as ordinary pitched notes. Classify each track as likely
melody, countermelody, vocal, bass, harmony, percussion, or unknown. Preserve a
copy of the input untouched.

### 2. Deterministic MIDI normalization

Normalize in a staging directory, never in place:

1. Resolve tempo-map timing rather than assuming one BPM. Preserve musical
   positions as absolute time or rational beats before quantization.
2. Trim only verified leading/trailing silence; never remove intentional rests.
3. Split overlapping voices where that makes later instrument assignment
   clearer, but retain a mapping back to source track/channel/note.
4. Quantize conservatively. Start at the smallest grid that preserves the hook
   and pickups; do not flatten triplets, swing, grace notes, or syncopation into
   straight eighths merely to reduce event count.
5. Apply one documented global transposition when needed. Octave-fold individual
   parts only after selecting their Minecraft instrument and reference pitch.
6. Cap or remap velocities into useful relative dynamics, while retaining the
   unmodified source velocity in the manifest for diagnosis.

The normalized MIDI and a machine-readable conversion report should be stable:
the same input plus options must produce byte-identical or semantically
identical output.

### 3. Note Block Studio import and batch strategy

Use Note Block Studio as the musical conversion/inspection layer. Import the
normalized MIDI with explicit choices for tempo, quantization, instrument
mapping, percussion handling, and out-of-range notes. Visually inspect at least
the melody entrance, one dense section, every tempo boundary, and the ending.
Audition it before export.

The desktop workflow did not expose a trusted one-click batch
import/transpose/export path during investigation. Do not automate mouse clicks
as the durable solution unless it is a short-lived bridge with screenshots,
per-file logs, failure detection, and resumability. Preferred automation order:

1. Use an existing supported NBS CLI/library if it reproduces Note Block
   Studio's import semantics.
2. If none exists, add a small batch command to a pinned fork of the open-source
   Note Block Studio importer/exporter. Keep it headless: input directory,
   output directory, options file, dry-run, overwrite policy, and JSON report.
3. Use GUI computer control only to validate a sample or unblock a tiny batch;
   never assume a click sequence succeeded without reopening/export validation.

A forked batch command should accept per-song overrides, continue after one
failure, write atomically via a temporary output, refuse accidental overwrite by
default, and report source hash, detected tempo map, selected tracks,
transposition, clipped/folded/dropped notes, assigned NBS instruments, event
counts, first/last tick, warnings, and output hash. Pin the upstream revision and
keep the patch narrow enough to rebase or upstream.

### 4. NBS export contract

Export one canonical `.nbs` per arrangement candidate. The file is the handoff,
not proof of final quality. Alongside it retain:

- the untouched and normalized MIDI;
- the manifest/conversion report and exact converter version/options;
- a short listening note identifying what is already convincing and what is
  absent (often bass, chords, drums, dynamics, articulation, or a real ending);
- section markers or named layers for melody, countermelody/vocal, bass,
  harmony, and percussion;
- the target recording timestamps and any deliberate structural cuts.

Validate by reopening the exported NBS and comparing tempo, duration, layer
count, instrument assignments, note count, pitch bounds, and several landmark
onsets against the conversion report. A successful file write is insufficient.

### 5. Agent NBS-to-VSE handoff

The implementation agent should first read this entire guide and inspect the
current `CasinoSongs`, `Note`, `Song`, `ActiveSong`, and `MultiChannelEngine`
behavior. Then it should parse the NBS format with a pinned reader rather than
screen-scraping Note Block Studio. The importer should emit an intermediate
event list before Java:

```text
absoluteTick, layer, sourceInstrument, minecraftSound,
sourceKey/MIDI, pitchMultiplier, velocity, stereoPan, provenance
```

Conversion rules must be explicit:

- integrate NBS tempo changes into 20-TPS absolute ticks and round absolute
  positions, not successive deltas, to avoid accumulated drift;
- map every pitched layer through the chosen sample's real reference MIDI and
  enforce VSE's safe `0.5..2.0` pitch range;
- use one-shot notes (`startTick == endTick`) unless an intentionally tested
  stop is required; NBS duration does not automatically become a safe VSE
  note-off;
- map velocity to restrained per-instrument volume, not raw 0..100 values;
- decide whether stereo pan is discarded, represented another way, or retained
  only as metadata—player-relative NBS stereo does not directly map to VSE;
- map custom/unsupported instruments deliberately and log every fallback;
- retain simultaneous notes as chords and retain silence as absent events;
- deduplicate only exact accidental duplicate attacks, never independent voices
  merely because their pitch/tick matches.

Generate a readable Java factory or data resource with named layers/sections and
source comments. Do not dump an opaque thousands-line literal without section
boundaries. The first generated pass should reproduce the NBS, then a separate
human/agent arranging pass may simplify density, rebalance volumes, add backing
parts/percussion, repair the ending, or shorten the excerpt. Record those edits
as arrangement decisions instead of silently attributing them to the MIDI.

### 6. Verification and listening gate

Automated checks should cover parser success, deterministic event count/hash,
first and final tick, landmark melody onsets, pitch bounds, finite playback,
loop boundary if applicable, no unintended duration stops, and all requested
layers/instruments. Run the focused score tests, relevant game lifecycle tests,
compile, and `shadowJar`.

Then audition in game with sound ON/OFF, replay/loop boundaries, inventory/page
changes, early timer termination, long configured timers, disconnect, table
teardown, two simultaneous players, and server lag/TPS observation. Compare the
recognizable melody and rhythm first; only then refine backing texture. Approval
of the NBS import is not approval of its VSE mix, and compile success is not an
in-game listening pass.

## Build, audition and deployment

From the checkout containing the changes, use PowerShell and Java 21:

```powershell
git status --short --branch
git diff --check
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat test --tests "org.nc.nccasino.games.Slots.CasinoSongsTest" --tests "org.nc.nccasino.games.Slots.SlotsControlLayoutRoutingTest" --tests "org.nc.nccasino.games.Slots.SlotsPaytableLayoutTest" --tests "org.nc.nccasino.games.Slots.SlotsOpeningColumnMotionTest" shadowJar
```

Add the new song's focused tests; broaden to `test` for shared gameplay or
lifecycle changes when proportionate. Day Tripper's original build and 59
tests across these four classes passed. These are historical results; rerun
for new code. `shadowJar` produces `build/libs/NCCasino-1.4.8.jar` at this
version, including VSE. `deploy.bat` runs `shadowJar`, not tests.
If sandbox restrictions block Gradle's external cache, use the prescribed
permission mechanism; do not confuse that with a source failure.

To re-audit the actual library without replacing it:

```powershell
Get-FileHash libs/lib-1.0-SNAPSHOT.jar -Algorithm SHA256
javap -classpath libs/lib-1.0-SNAPSHOT.jar -c -p org.nc.VSE.ActiveSong
javap -classpath libs/lib-1.0-SNAPSHOT.jar -c -p org.nc.VSE.Channel
javap -classpath libs/lib-1.0-SNAPSHOT.jar -c -p org.nc.VSE.MultiChannelEngine
```

Deployment is checkout-sensitive. The established script in
`C:\Users\nicho\minecwafffff\NCCasino` reads `config.txt` from its **current
working directory**, then builds `PROJECT_DIR`. It does not pick up edits in
another worktree merely because the repository or branch is related.
The implementation currently lives in
`C:\Users\nicho\orca\workspaces\NCCasino\soapfish`, branch
`NBorow/slots-overhaul-soapfish`. At this audit the score, tests and playbook
are untracked and the integration is uncommitted; a fresh clone will not
contain them. Keep this workspace or explicitly transfer/commit the changes
before expecting a new checkout to have them. No commit/push is implied.

For deployment from this workspace, the documented local `config.txt` is:

```ini
PROJECT_DIR=C:\Users\nicho\orca\workspaces\NCCasino\soapfish
SERVER_DIR=C:\Users\nicho\minecwafffff\slots-test
PLUGIN_NAME=NCCasino-1.4.8.jar
SERVER_JAR=spigot-1.21.11.jar
```

`config.txt` is Git-ignored and was absent here at audit; these are the
known target values, not a claim that setup/deployment has been performed.
Inspect actual paths/config again before use. Run from that configured
workspace, stop the test server first, then run `deploy.bat` when deployment
is requested. It deletes/replaces the plugin JAR and starts the server; it
does not wipe plugin data/config. On a new server where no old JAR exists,
its delete step may fail: inspect script output rather than claiming success.

After deployment, test the three placements in their actual games: Slots rainbow
housing, Blackjack entrance through dealer transition, and both Roulette betting
pages through bets closing. Include rapid replay where supported, page/view
changes, short and long configured timers, close/disconnect, sound OFF, table
teardown, and concurrent players. Confirm the existing Slots opening still
sounds normal. If edits seem absent, check PROJECT_DIR, rebuilt JAR time/hash
and the server's actual loaded artifact before changing music.
For silence, check sound preference/client mixer, selected slot/view, valid
namespaced sound IDs and VSE ticker/listener; for chopped chords, check note
durations and broad stop calls; for slowdown, check server TPS before BPM.

## NEW SONG HANDOFF

Copy this block into a fresh session attached to a checkout containing this work:

```text
Create ONE recognizable Minecraft/VSE arrangement of [song + version], section
[timestamps/measures], using [NBS path] as a candidate melodic handoff if given.
Read AGENTS.md and docs/music-composition/COMPOSITION_GUIDE.md completely. Inspect
CasinoSongs.java, the target game's playback/cleanup lifecycle, and
CasinoSongsTest.java at the paths in the playbook. Check git status first.
Research the actual section's pitches AND rhythm; record sources and uncertainty.
Decide BPM/unit, phrase length, instrument registers, transposition, essential
voices, dynamics and ending. Preserve recognizable intervals and syncopation.
If using NBS, preserve a parsed intermediate event report and distinguish imported
melody from agent-authored backing. Implement a new factory and wire it only to
the explicitly requested game event.
Preserve the approved Slots intro and gameplay. Use the existing playback and
cleanup patterns; define loop/pause/early-stop behavior explicitly and prevent
stacked replays. Respect localization rules for new text.
Run relevant score/playback/routing tests, compile and build shadowJar. Tell me
the exact audition location and what needs in-game listening. Check deployment
config/checkout if asked to deploy; do not assume the other worktree has edits.
Update the durable song record with decisions, tests and subsequent listening
feedback. Do not make another composition or commit/push unless asked.
```

## Model/workflow choice

Practical recommendation (not a measured music benchmark): start with GPT-5.6
Sol for source analysis and arrangement, then implementation/testing in the
same session. GPT-5.6 Terra is a lower-cost option for turning an already
specified note/beat plan into code and making bounded adjustments. Use Astra
only for a concrete unresolved transcription, rhythm or voicing problem, with
the relevant sources, event plan, attempted change and listening feedback.
Save its resolved decision back here and return routine work to the cheaper
model. Do not spend Astra sessions repeatedly rediscovering VSE.

The [official model comparison](https://developers.openai.com/api/docs/models/compare)
lists Sol and Terra below Astra in per-token API pricing (checked 2026-09-12).
That does not establish Codex plan billing or total cost per successful song;
retries matter. No cross-model test on this task establishes equal musical
quality. Try this workflow on one section and judge the audible result before
making cheaper models the default for all composition decisions.

## References and verification

- [VSE ActiveSong](https://github.com/NBorow/VSE/blob/main/lib/src/main/java/org/nc/VSE/ActiveSong.java)
- [VSE MultiChannelEngine](https://github.com/NBorow/VSE/blob/main/lib/src/main/java/org/nc/VSE/MultiChannelEngine.java)
- [Spigot Player sound API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Player.html)
- [Alan W. Pollack: Day Tripper](https://www.recmusicbeatles.com/public/files/awp/dt-1.html)
  supplies the two-bar rhythmic grid and pitches.
- [Ethan Hein: Day Tripper](https://www.ethanhein.com/wp/2016/musical-simples-day-tripper/)
  independently explains the ninth and anticipated beats.

`CasinoSongsTest` exercises the bundled VSE scheduler directly with a
mock listener: finite playback, all 84 events including the final chord,
no note-off cuts, timing/register checks and a non-overlapping paytable slot.
In-game listening remains necessary for balance, articulation, latency and
resource-pack tuning. Also check rapid replay, Back to Game, spin/demo,
inventory close, disconnect, two simultaneous players and sound OFF.
