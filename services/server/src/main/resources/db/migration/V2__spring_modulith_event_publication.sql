-- Spring Modulith's JPA event publication registry is part of the application schema.
-- Keep it under Flyway control because Hibernate is configured for validation only.
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(255)
);

