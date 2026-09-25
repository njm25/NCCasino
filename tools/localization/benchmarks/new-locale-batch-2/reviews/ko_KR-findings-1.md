# ko_KR review -- packet-1 (592 records), isolated reviewer 1

Reviewed all 592 KEY/EN/VAL records against the rubric; no other file opened.
Mechanical pass (ordered placeholders, & codes, \n, /ncc claim, protected
literals, -1/off, placeholder+letter joins, English residue): no Tier 0.
Only placeholder+letter joins are `{multiplier}x`, identical in EN.

Tier 0: none. Tier 1: none. Tier 2: none.

Tier 3:
- coin-flip.awaiting-wager-lore: "면" (coin face) does not match the
  "왼쪽"/"오른쪽" pick buttons; EN "side" is generic.
  SUGGEST "&7&o그다음 어느 쪽을 고를지 신중하게 정하세요"
- coin-flip.max-pot-hit / rock-paper-scissors.max-pot-hit: double 이/가
  subject reads clumsily. SUGGEST "&6당첨금이 이 게임에서 한 번에 지급할 수
  있는 최대 금액에 도달했습니다. 최고 당첨을 축하합니다!"
- slots.auto-rule-loss: "초과가 되기 전에" unnatural.
  SUGGEST "&7손실이 &c{amount} &7한도를 넘기 전에 멈춥니다."
- slots.auto-loss-limit-set: same. SUGGEST "&a이번 자동 스핀에서 손실이
  {amount} 한도를 넘기 전에 멈춥니다."
- slots.payline-shape-diagonal-down / -up: adverb before noun.
  SUGGEST "하향 대각선" / "상향 대각선"
- slots.prompt-invalid-spin-limit: clause/label hybrid.
  SUGGEST "&c스핀 횟수를 정수로 입력하세요. 제한 없음: {unlimited}"
- slots-settings.house-edge-updated: "이제" before a colon label.
  SUGGEST "&a하우스 엣지 설정: {edge}. 현재 기계 환수율: {rtp}."
- slots.rail-profiles-global: "프로필은 내 것이며" literal/odd.
  SUGGEST "&7프로필은 개인별로 저장되며, 모든 슬롯 기계에서 사용할 수 있습니다."
- test-game.server-lost: telegraphic without object particle.
  SUGGEST "패배했습니다! 잃은 금액: {amount} {currency}"
- errors.currency-unavailable: general inability vs this payout failed.
  SUGGEST "&c화폐가 설정되지 않아 당첨금을 지급하지 못했습니다."

Unscored, needs code check: coin-flip.chain-win / rock-paper-scissors.chain-win
"한 번 더 이기면: &e{amount}" commits {amount} to being the next win's payout;
Tier 1 if the code passes the current pot instead.

Terminology: 재베팅, 캐시아웃, 연승, 팟, 배당표, 하우스 엣지, 환수율, 변동성,
보관 중인 당첨금, 기계, 스핀 제한, 큰 당첨, 목표 수익, 손실 한도, 화폐 consistent;
뱅커 vs 딜러 kept distinct.

Counts: Tier 0 = 0, Tier 1 = 0, Tier 2 = 0, Tier 3 = 12.
