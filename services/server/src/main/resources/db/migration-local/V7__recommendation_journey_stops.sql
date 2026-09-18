create table recommendation_journey_stops (
    recommendation_id uuid not null references recommendation_events(event_id) on delete cascade,
    stop_order smallint not null check (stop_order between 1 and 3),
    sense_code varchar(100) not null,
    item_id uuid not null,
    location_id uuid not null,
    primary key (recommendation_id, stop_order)
);

create index recommendation_journey_stops_location_idx
    on recommendation_journey_stops (location_id);
