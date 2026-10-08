CREATE TABLE anonymous_installations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    proof_hash BYTEA NOT NULL CHECK (octet_length(proof_hash) = 32),
    access_token_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(access_token_hash) = 32),
    device_platform VARCHAR(8) NOT NULL CHECK (device_platform IN ('ANDROID','IOS','WEB')),
    app_version VARCHAR(32) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at)
);
CREATE INDEX anonymous_installations_owner ON anonymous_installations(user_id);
