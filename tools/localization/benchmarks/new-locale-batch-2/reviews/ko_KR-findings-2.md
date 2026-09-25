# ko_KR review -- packet-2 (592 records), isolated reviewer 2

Checked every record against the rubric plus a scripted comparison of
ordered placeholders, & codes, \n, /ncc tokens, protected literals and -1,
and placeholder+letter joins. No Tier 0 or Tier 1 defects. Structural
notes, none defects: `admin.vault-missing`/`install-vault` swap the Vault /
NCCasino literal order (Korean word order; literals intact);
`{multiplier}x` joins match EN; `-1` hit on edit-stand-17 is "Stand-on-17".
Known `*.updated-detailed` template defect not scored.

Tier 2:
- blackjack-settings.turn-timer-timeout-desc-1: 행동 vs the 동작 used by the
  "동작 타이머" button and related strings.
  SUGGEST "&7자동으로 스탠드하기 전까지 플레이어가 동작을 고를 수 있는 시간(초)입니다."

Tier 3:
- roulette.refund-exit: "및/또는" legalistic. SUGGEST "환불/나가기"
- blackjack.not-your-turn: "아직" implies the turn is coming.
  SUGGEST "&c지금은 차례가 아닙니다."
- blackjack-settings.edit-timer-desc-1: 놓이다 too literal.
  SUGGEST "&7첫 베팅이 들어오면 시작됩니다."
- blackjack-settings.turn-timer-timeout-desc-2: hard to parse.
  SUGGEST "&7메뉴를 닫았거나 접속이 끊긴 플레이어의 차례도 이 시간이 지나면
  처리되며, 그 자리의 베팅은 그대로 결과까지 진행됩니다."
- commands.no-dealers: identical to "could not find dealer" keys.
  SUGGEST "&c딜러가 없습니다."
- mines.rebet-broke: slang lost. SUGGEST "&c빈털터리라 재베팅 불가."
- mob-selection.age-set: does not say age changed.
  SUGGEST "&a{mob} 나이 설정: &e{age}&a."

Terminology notes: 동작 vs 행동 (above). Blackjack bet spot uses both
베팅 서클 and 베팅 자리/자리 (EN itself uses three terms; 자리 is also the
chair/seat term) -- noted, not scored. Everything else consistent.

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 1, Tier 3 = 7.
