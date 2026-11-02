-- DAU 집계용 일별 활동 기록

create table user_daily_activities (
    activity_date date not null,
    id bigint not null auto_increment,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;

alter table user_daily_activities
   add constraint uk_user_daily_activities_user_date unique (user_id, activity_date);
