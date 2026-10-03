CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ============ USERS ============
CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    full_name        VARCHAR(150) NOT NULL,
    phone            VARCHAR(30),
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('CLIENT','OWNER','STAFF','ADMIN')),
    whatsapp_opt_in  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============ VENUES (complexes) ============
CREATE TABLE venue (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id),
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    address     VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    phone       VARCHAR(30),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ACTIVE','SUSPENDED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_venue_owner ON venue(owner_id);
CREATE INDEX idx_venue_city ON venue(city);

-- ============ FIELDS (terrains) ============
CREATE TABLE field (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id       UUID NOT NULL REFERENCES venue(id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    format         VARCHAR(10)  NOT NULL CHECK (format IN ('FIVE','SEVEN','ELEVEN')),
    surface        VARCHAR(20)  NOT NULL CHECK (surface IN ('SYNTHETIC','NATURAL','INDOOR')),
    slot_minutes   INT NOT NULL DEFAULT 60 CHECK (slot_minutes > 0),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_field_venue ON field(venue_id);

CREATE TABLE field_photo (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id  UUID NOT NULL REFERENCES field(id) ON DELETE CASCADE,
    url       VARCHAR(500) NOT NULL,
    position  INT NOT NULL DEFAULT 0
);

-- ============ OPENING HOURS / CLOSED DAYS ============
CREATE TABLE opening_hours (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id    UUID NOT NULL REFERENCES field(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1 = lundi
    open_time   TIME NOT NULL,
    close_time  TIME NOT NULL,
    CHECK (close_time > open_time)
);

CREATE TABLE closed_day (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id  UUID NOT NULL REFERENCES field(id) ON DELETE CASCADE,
    day       DATE NOT NULL,
    reason    VARCHAR(255),
    UNIQUE (field_id, day)
);

-- ============ PRICING ============
CREATE TABLE pricing_rule (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id     UUID NOT NULL REFERENCES field(id) ON DELETE CASCADE,
    day_of_week  SMALLINT CHECK (day_of_week BETWEEN 1 AND 7), -- NULL = tous les jours
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    currency     CHAR(3) NOT NULL DEFAULT 'XOF',
    CHECK (end_time > start_time)
);

-- ============ BOOKINGS ============
CREATE TABLE booking (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id       UUID NOT NULL REFERENCES field(id),
    user_id        UUID REFERENCES users(id),           -- NULL si blocage manuel
    start_time     TIMESTAMPTZ NOT NULL,
    end_time       TIMESTAMPTZ NOT NULL,
    status         VARCHAR(20) NOT NULL
                   CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
    source         VARCHAR(20) NOT NULL DEFAULT 'ONLINE'
                   CHECK (source IN ('ONLINE','MANUAL','BLOCK')),
    total_price    NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency       CHAR(3) NOT NULL DEFAULT 'XOF',
    expires_at     TIMESTAMPTZ,                          -- fin du hold PENDING
    note           VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time > start_time),
    -- Interdit tout chevauchement sur un meme terrain (reservations actives)
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (
        field_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status IN ('PENDING','CONFIRMED'))
);
CREATE INDEX idx_booking_field_time ON booking(field_id, start_time);
CREATE INDEX idx_booking_user ON booking(user_id);
CREATE INDEX idx_booking_pending_expiry ON booking(expires_at) WHERE status = 'PENDING';

-- ============ PAYMENTS ============
CREATE TABLE payment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id    UUID NOT NULL REFERENCES booking(id),
    provider      VARCHAR(30) NOT NULL,
    provider_ref  VARCHAR(100),
    amount        NUMERIC(12,2) NOT NULL,
    currency      CHAR(3) NOT NULL DEFAULT 'XOF',
    status        VARCHAR(20) NOT NULL
                  CHECK (status IN ('INITIATED','SUCCEEDED','FAILED','REFUNDED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_ref)
);

-- ============ NOTIFICATION OUTBOX ============
CREATE TABLE notification (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    channel     VARCHAR(20) NOT NULL CHECK (channel IN ('WHATSAPP','SMS','EMAIL','PUSH')),
    template    VARCHAR(60) NOT NULL,
    payload     JSONB NOT NULL DEFAULT '{}',
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts    INT NOT NULL DEFAULT 0,
    send_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_pending ON notification(send_at) WHERE status = 'PENDING';
