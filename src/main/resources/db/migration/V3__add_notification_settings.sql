-- 알림 받을 이메일과 알림 설정 (분석 완료 · 서비스). 마케팅 수신은 users.marketing_agreed_at을 쓴다

create table notification_settings (
    analysis_enabled bit not null,
    service_enabled bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    user_id bigint not null,
    email varchar(100),
    primary key (id)
) engine=InnoDB;

alter table notification_settings
   add constraint uk_notification_settings_user_id unique (user_id);
