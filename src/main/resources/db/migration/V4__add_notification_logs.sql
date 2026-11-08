-- 알림 메일 발송 기록. 같은 종류 · 같은 대상(녹음 등)으로 두 번 보내지 않도록 유니크 제약을 둔다.
-- 받는 사람 주소와 메일 내용은 남기지 않는다

create table notification_logs (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    target_id bigint,
    updated_at datetime(6) not null,
    user_id bigint not null,
    status varchar(20) not null,
    type varchar(30) not null,
    failure_reason varchar(100),
    provider_message_id varchar(100),
    primary key (id)
) engine=InnoDB;

alter table notification_logs
   add constraint uk_notification_logs_type_target unique (type, target_id);
