alter table recommendation_events
    add column consent_status varchar(30) not null default 'NOT_ASKED';
alter table recommendation_events
    add column stress_score integer not null default 0;

alter table recommendation_events
    add constraint recommendation_events_consent_check
        check (consent_status in ('CONSENTED', 'DECLINED', 'NOT_ASKED'));
alter table recommendation_events
    add constraint recommendation_events_stress_check
        check (stress_score between 0 and 100);

create index recommendation_events_consent_time_idx
    on recommendation_events (consent_status, occurred_at desc);
