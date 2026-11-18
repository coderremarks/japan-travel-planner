-- Itineraries are private to their owner. Every query filters by (id, owner_id).
CREATE TABLE itinerary (
    id                  uuid         PRIMARY KEY,
    owner_id            uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title               varchar(120) NOT NULL,
    start_date          date         NOT NULL,
    end_date            date         NOT NULL,
    day_start_minute    integer      NOT NULL,
    day_end_minute      integer      NOT NULL,
    budget_yen          integer      NOT NULL,
    max_visits_per_day  integer      NOT NULL,
    base_location_id    uuid         NOT NULL REFERENCES base_location (id),
    include_suggestions boolean      NOT NULL DEFAULT false,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_itinerary_dates CHECK (end_date >= start_date AND end_date - start_date <= 13),
    CONSTRAINT ck_itinerary_day_window CHECK (day_start_minute >= 0
        AND day_end_minute <= 1439
        AND day_end_minute - day_start_minute >= 60),
    CONSTRAINT ck_itinerary_budget CHECK (budget_yen BETWEEN 0 AND 10000000),
    CONSTRAINT ck_itinerary_visits CHECK (max_visits_per_day BETWEEN 1 AND 12)
);

CREATE INDEX ix_itinerary_owner_updated ON itinerary (owner_id, updated_at DESC);

CREATE TABLE itinerary_interest (
    itinerary_id uuid        NOT NULL REFERENCES itinerary (id) ON DELETE CASCADE,
    interest     varchar(24) NOT NULL,
    CONSTRAINT pk_itinerary_interest PRIMARY KEY (itinerary_id, interest),
    CONSTRAINT ck_itinerary_interest CHECK (interest IN ('NATURE', 'FOOD', 'SHOPPING', 'MUSEUM', 'CULTURE', 'ENTERTAINMENT'))
);

CREATE TABLE itinerary_travel_mode (
    itinerary_id uuid        NOT NULL REFERENCES itinerary (id) ON DELETE CASCADE,
    travel_mode  varchar(16) NOT NULL,
    CONSTRAINT pk_itinerary_travel_mode PRIMARY KEY (itinerary_id, travel_mode),
    CONSTRAINT ck_itinerary_travel_mode CHECK (travel_mode IN ('WALK', 'TRANSIT', 'TAXI'))
);

-- The traveller's list of places, in their chosen order.
-- (itinerary_id, place_id) uniqueness is deferred to commit so that re-ordering inside one
-- transaction cannot trip it on an intermediate state.
CREATE TABLE itinerary_stop (
    itinerary_id uuid     NOT NULL REFERENCES itinerary (id) ON DELETE CASCADE,
    position     integer  NOT NULL,
    place_id     uuid     NOT NULL REFERENCES place (id),
    mandatory    boolean  NOT NULL,
    CONSTRAINT pk_itinerary_stop PRIMARY KEY (itinerary_id, position),
    CONSTRAINT uq_itinerary_stop_place UNIQUE (itinerary_id, place_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_itinerary_stop_position CHECK (position BETWEEN 0 AND 49)
);

-- Latest generated plan: an immutable JSON snapshot plus a fingerprint of the inputs it was built from.
CREATE TABLE itinerary_plan (
    itinerary_id      uuid        PRIMARY KEY REFERENCES itinerary (id) ON DELETE CASCADE,
    status            varchar(16) NOT NULL,
    strategy          varchar(16) NOT NULL,
    input_fingerprint varchar(64) NOT NULL,
    algorithm_version varchar(32) NOT NULL,
    plan_json         jsonb       NOT NULL,
    generated_at      timestamptz NOT NULL,
    CONSTRAINT ck_itinerary_plan_status CHECK (status IN ('COMPLETE', 'PARTIAL', 'INFEASIBLE')),
    CONSTRAINT ck_itinerary_plan_strategy CHECK (strategy IN ('OPTIMIZE', 'KEEP_ORDER'))
);
