-- 분석 보고서 공유 링크. 토큰 원문은 저장하지 않고 SHA-256 해시만 보관한다.
-- 녹음당 활성 링크 하나만 유지하며, 토큰은 발급 당시의 분석에도 고정한다.
-- 재발급 시 같은 행의 분석 ID, 해시와 만료 시각을 교체한다.

create table analysis_shares (
    analysis_id bigint not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    recording_id bigint not null,
    updated_at datetime(6) not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB;

alter table analysis_shares
   add constraint uk_analysis_shares_recording_id unique (recording_id);

alter table analysis_shares
   add constraint uk_analysis_shares_analysis_id unique (analysis_id);

alter table analysis_shares
   add constraint uk_analysis_shares_token_hash unique (token_hash);
