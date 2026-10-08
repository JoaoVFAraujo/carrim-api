CREATE TABLE completion_receipts (
    user_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    session_id UUID NOT NULL,
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash)=32),
    PRIMARY KEY (user_id,operation_id),
    FOREIGN KEY (user_id,session_id) REFERENCES shopping_sessions(user_id,id)
);
