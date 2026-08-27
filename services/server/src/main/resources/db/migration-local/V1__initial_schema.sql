create table projects (
    id uuid primary key,
    project_code varchar(100) not null unique,
    config_version integer not null check (config_version > 0),
    config_json clob not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table recommendation_events (
    event_id uuid primary key,
    project_code varchar(100) not null,
    kiosk_id varchar(100) not null,
    session_id uuid not null,
    emotion_code varchar(100) not null,
    item_id uuid not null,
    location_id uuid not null,
    source varchar(30) not null,
    policy_version varchar(100) not null,
    occurred_at timestamp with time zone not null,
    received_at timestamp with time zone not null,
    constraint recommendation_events_source_check check (source in ('LOCAL', 'REMOTE', 'LOCAL_FALLBACK'))
);

create index recommendation_events_project_time_idx on recommendation_events (project_code, occurred_at desc);
create index recommendation_events_location_time_idx on recommendation_events (location_id, occurred_at desc);
create index recommendation_events_kiosk_time_idx on recommendation_events (kiosk_id, occurred_at desc);
