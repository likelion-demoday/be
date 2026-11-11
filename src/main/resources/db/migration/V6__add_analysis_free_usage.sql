-- 소셜 가입 계정의 분석 1회 무료. 무료로 쓴 사용 건을 기록하고, 비어 있으면 기회가 남아 있다 (character_free_usage_id와 같은 방식)

alter table credit_wallets
   add column analysis_free_usage_id bigint;
