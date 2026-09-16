alter table location_operational_status
    add column priority_share integer,
    add column priority_until timestamptz;

alter table location_operational_status
    add constraint location_priority_share_check
    check (priority_share is null or priority_share between 1 and 100);
