-- 첫 스키마 (2026-10-28 develop 6892839 기준 엔티티 13개)
-- Hibernate가 MySQL 8.4에서 ddl-auto=create로 만드는 DDL을 그대로 옮겼다 (유니크 제약 · 인덱스 포함)
--
-- 한 번 운영에 적용된 파일은 고치지 않는다 (Flyway가 체크섬으로 막는다).
-- 엔티티를 바꾸면 V2__설명.sql 처럼 다음 번호의 파일을 새로 만든다. 확인은 FlywayMigrationTest

create table analysis_results (
    analysis_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    prompt_version varchar(50) not null,
    schema_version varchar(50) not null,
    model_name varchar(100) not null,
    result_json longtext not null,
    primary key (id)
) engine=InnoDB;

create table character_images (
    analysis_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    prompt_version varchar(50),
    failure_code varchar(100),
    media_type varchar(100),
    model_name varchar(100),
    object_key varchar(500),
    speaker_role varchar(20) not null,
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table conversation_analyses (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    recording_id bigint not null,
    updated_at datetime(6) not null,
    failure_reason varchar(50),
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table credit_ledger (
    amount integer not null,
    balance_after integer not null,
    created_at datetime(6) not null,
    created_by bigint,
    id bigint not null auto_increment,
    payment_id bigint,
    recording_id bigint,
    updated_at datetime(6) not null,
    usage_id bigint,
    user_id bigint not null,
    unique_key varchar(50),
    memo varchar(200),
    purpose varchar(20),
    type varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table credit_usages (
    amount integer not null,
    free_use bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    recording_id bigint not null,
    refunded_at datetime(6),
    updated_at datetime(6) not null,
    user_id bigint not null,
    active_key varchar(50),
    refund_reason varchar(100),
    purpose varchar(20) not null,
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table credit_wallets (
    balance integer not null,
    character_free_usage_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    primary key (user_id)
) engine=InnoDB;

create table payment_cancels (
    amount integer not null,
    credit_amount integer not null,
    cancelled_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    payment_id bigint not null,
    requested_by bigint,
    updated_at datetime(6) not null,
    user_id bigint not null,
    fail_code varchar(30),
    cancel_order_id varchar(64),
    fail_message varchar(200),
    reason varchar(200),
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table payments (
    amount integer not null,
    credit_amount integer not null,
    approval_requested_at datetime(6),
    cancelled_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    paid_at datetime(6),
    updated_at datetime(6) not null,
    user_id bigint not null,
    fail_code varchar(30),
    product_code varchar(30) not null,
    tid varchar(30),
    auth_token_hash varchar(64),
    order_id varchar(64) not null,
    fail_message varchar(200),
    receipt_url varchar(200),
    result_origin varchar(200) not null,
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table recordings (
    duration_seconds integer not null,
    audio_deleted_at datetime(6),
    completed_at datetime(6),
    created_at datetime(6) not null,
    deleted_at datetime(6),
    failed_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    user_id bigint not null,
    self_speaker_label varchar(10),
    partner_nickname varchar(20),
    audio_file_path varchar(255) not null,
    title varchar(255),
    failure_reason varchar(40),
    parent_child_role varchar(10),
    relationship_type varchar(30),
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table refresh_tokens (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    user_id bigint not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB;

create table transcript_segments (
    segment_no integer not null,
    created_at datetime(6) not null,
    end_ms bigint not null,
    id bigint not null auto_increment,
    recording_id bigint not null,
    start_ms bigint not null,
    updated_at datetime(6) not null,
    speaker_label varchar(10) not null,
    content text not null,
    primary key (id)
) engine=InnoDB;

create table transcriptions (
    completed_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    recording_id bigint not null,
    updated_at datetime(6) not null,
    callback_secret varchar(36) not null,
    job_token varchar(255),
    provider varchar(20) not null,
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table users (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    nickname varchar(50) not null,
    email varchar(100),
    provider_id varchar(100),
    password varchar(255),
    provider varchar(20) not null,
    role varchar(20) not null,
    primary key (id)
) engine=InnoDB;

alter table analysis_results
   add constraint uk_analysis_results_analysis_id unique (analysis_id);

alter table character_images
   add constraint uk_character_images_analysis_speaker unique (analysis_id, speaker_role);

alter table conversation_analyses
   add constraint uk_conversation_analyses_recording_id unique (recording_id);

create index idx_credit_ledger_user_id_id
   on credit_ledger (user_id, id);

alter table credit_ledger
   add constraint uk_credit_ledger_unique_key unique (unique_key);

create index idx_credit_usages_user_id
   on credit_usages (user_id);

create index idx_credit_usages_recording_id
   on credit_usages (recording_id);

alter table credit_usages
   add constraint uk_credit_usages_active_key unique (active_key);

create index idx_payment_cancels_payment_id
   on payment_cancels (payment_id);

create index idx_payment_cancels_status_id
   on payment_cancels (status, id);

alter table payment_cancels
   add constraint uk_payment_cancels_cancel_order_id unique (cancel_order_id);

create index idx_payments_user_id_id
   on payments (user_id, id);

create index idx_payments_status_id
   on payments (status, id);

alter table payments
   add constraint uk_payments_order_id unique (order_id);

alter table payments
   add constraint uk_payments_tid unique (tid);

alter table payments
   add constraint uk_payments_auth_token_hash unique (auth_token_hash);

create index idx_refresh_tokens_user_id
   on refresh_tokens (user_id);

alter table refresh_tokens
   add constraint uk_refresh_tokens_token_hash unique (token_hash);

create index idx_transcript_segments_recording_id
   on transcript_segments (recording_id);

alter table transcript_segments
   add constraint uk_transcript_segments_recording_id_segment_no unique (recording_id, segment_no);

alter table transcriptions
   add constraint uk_transcriptions_recording_id unique (recording_id);

alter table transcriptions
   add constraint uk_transcriptions_callback_secret unique (callback_secret);

alter table users
   add constraint uk_users_email unique (email);

alter table users
   add constraint uk_users_provider_provider_id unique (provider, provider_id);
