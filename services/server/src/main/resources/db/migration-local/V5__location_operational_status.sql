create table location_operational_status (
    project_code varchar(100) not null,
    location_code varchar(100) not null,
    enabled boolean not null,
    updated_at timestamp with time zone not null,
    updated_by varchar(100) not null,
    primary key (project_code, location_code)
);

create index location_operational_status_project_idx
    on location_operational_status (project_code, updated_at desc);
