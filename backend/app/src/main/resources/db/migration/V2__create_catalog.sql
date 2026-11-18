-- Reference data: areas, base locations (where travellers stay), places and their opening rules.
-- Times are minutes since local midnight (0..1440) so that "open until midnight" (1440) is representable.

CREATE TABLE area (
    code       varchar(32)      PRIMARY KEY,
    name_en    varchar(100)     NOT NULL,
    name_ja    varchar(100)     NOT NULL,
    center_lat double precision NOT NULL,
    center_lng double precision NOT NULL,
    sort_order integer          NOT NULL
);

CREATE TABLE base_location (
    id        uuid             PRIMARY KEY DEFAULT gen_random_uuid(),
    slug      varchar(80)      NOT NULL,
    area_code varchar(32)      NOT NULL REFERENCES area (code),
    name_en   varchar(120)     NOT NULL,
    name_ja   varchar(120)     NOT NULL,
    latitude  double precision NOT NULL,
    longitude double precision NOT NULL,
    CONSTRAINT uq_base_location_slug UNIQUE (slug),
    CONSTRAINT ck_base_location_lat CHECK (latitude BETWEEN 20 AND 46),
    CONSTRAINT ck_base_location_lng CHECK (longitude BETWEEN 122 AND 154)
);

CREATE TABLE place (
    id             uuid             PRIMARY KEY DEFAULT gen_random_uuid(),
    slug           varchar(80)      NOT NULL,
    area_code      varchar(32)      NOT NULL REFERENCES area (code),
    category       varchar(24)      NOT NULL,
    name_en        varchar(120)     NOT NULL,
    name_ja        varchar(120)     NOT NULL,
    summary_en     varchar(500)     NOT NULL,
    summary_ja     varchar(500)     NOT NULL,
    latitude       double precision NOT NULL,
    longitude      double precision NOT NULL,
    visit_minutes  integer          NOT NULL,
    admission_yen  integer          NOT NULL,
    holiday_policy varchar(40)      NOT NULL DEFAULT 'SAME_AS_WEEKDAY',
    data_source    varchar(32)      NOT NULL,
    verified_on    date,
    CONSTRAINT uq_place_slug UNIQUE (slug),
    CONSTRAINT ck_place_category CHECK (category IN ('NATURE', 'FOOD', 'SHOPPING', 'MUSEUM', 'CULTURE', 'ENTERTAINMENT')),
    CONSTRAINT ck_place_visit CHECK (visit_minutes BETWEEN 10 AND 720),
    CONSTRAINT ck_place_admission CHECK (admission_yen BETWEEN 0 AND 100000),
    CONSTRAINT ck_place_holiday_policy CHECK (holiday_policy IN ('SAME_AS_WEEKDAY', 'OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY')),
    CONSTRAINT ck_place_source CHECK (data_source IN ('SAMPLE_UNVERIFIED', 'OFFICIAL', 'OPEN_DATA')),
    CONSTRAINT ck_place_lat CHECK (latitude BETWEEN 20 AND 46),
    CONSTRAINT ck_place_lng CHECK (longitude BETWEEN 122 AND 154)
);

CREATE INDEX ix_place_area_category ON place (area_code, category);

-- Weekly opening hours; several rows per day model lunch breaks. ISO-8601 day of week: 1 = Monday.
CREATE TABLE place_opening_hours (
    place_id      uuid     NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    day_of_week   integer  NOT NULL,
    opens_minute  integer  NOT NULL,
    closes_minute integer  NOT NULL,
    CONSTRAINT pk_place_opening_hours PRIMARY KEY (place_id, day_of_week, opens_minute),
    CONSTRAINT ck_poh_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_poh_minutes CHECK (opens_minute >= 0 AND opens_minute < closes_minute AND closes_minute <= 1440)
);

-- One-off closures (year-end holidays, maintenance).
CREATE TABLE place_closure (
    place_id  uuid         NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    closed_on date         NOT NULL,
    note      varchar(200),
    CONSTRAINT pk_place_closure PRIMARY KEY (place_id, closed_on)
);

-- Japanese national holidays (official open data, Cabinet Office of Japan).
CREATE TABLE japanese_holiday (
    holiday_date date         PRIMARY KEY,
    name_ja      varchar(60)  NOT NULL,
    name_en      varchar(80)  NOT NULL
);
