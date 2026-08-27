alter table recommendation_events add column participant_name varchar(50);
alter table recommendation_events add column participant_phone varchar(11);
alter table recommendation_events add column participant_birth_date varchar(8);
alter table recommendation_events add column participant_gender varchar(20);

create index recommendation_events_participant_time_idx
    on recommendation_events (project_code, consent_status, occurred_at desc);
