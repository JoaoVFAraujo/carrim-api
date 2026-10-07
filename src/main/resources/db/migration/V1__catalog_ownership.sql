CREATE TABLE users (
    id UUID PRIMARY KEY,
    account_type VARCHAR(16) NOT NULL CHECK (account_type IN ('ANONYMOUS', 'ACCOUNT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120 AND name ~ '[^[:space:]]'),
    barcode VARCHAR(13) CHECK (barcode IS NULL OR barcode ~ '^([0-9]{8}|[0-9]{12}|[0-9]{13})$'),
    measurement_type VARCHAR(8) NOT NULL CHECK (measurement_type IN ('UNIT', 'WEIGHT')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (user_id, barcode)
);

CREATE INDEX products_owner ON products(user_id);

CREATE TABLE supermarkets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120 AND name ~ '[^[:space:]]'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE INDEX supermarkets_owner ON supermarkets(user_id);
