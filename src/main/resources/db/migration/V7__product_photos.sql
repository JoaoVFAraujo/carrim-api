CREATE TABLE product_photos (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 barcode VARCHAR(13) CHECK (barcode IS NULL OR barcode ~ '^([0-9]{8}|[0-9]{12}|[0-9]{13})$'),
 item_id UUID,
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
 source VARCHAR(20) NOT NULL CHECK (source IN ('USER','OPEN_FOOD_FACTS')),
 status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','DELETED','EXTERNAL')),
 content BYTEA CHECK (content IS NULL OR octet_length(content) BETWEEN 1 AND 512000),
 input_hash VARCHAR(64) NOT NULL,
 content_hash VARCHAR(64),
 source_url VARCHAR(1024),
 license_url VARCHAR(256),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at TIMESTAMPTZ,
 review_reason VARCHAR(200),
 CHECK (barcode IS NOT NULL OR item_id IS NOT NULL),
 CHECK ((status='DELETED' AND content IS NULL) OR (status<>'DELETED' AND content IS NOT NULL)),
 CHECK ((source='USER' AND status<>'EXTERNAL') OR (source='OPEN_FOOD_FACTS' AND status IN ('EXTERNAL','DELETED')))
);
CREATE INDEX product_photos_owner ON product_photos(user_id,updated_at DESC,id);
CREATE INDEX product_photos_approved_barcode ON product_photos(barcode,reviewed_at DESC,id) WHERE status='APPROVED';
CREATE TABLE photo_preferences (
 user_id UUID NOT NULL REFERENCES users(id),
 preference_key VARCHAR(64) NOT NULL,
 barcode VARCHAR(13),
 item_id UUID,
 photo_id UUID REFERENCES product_photos(id),
 hidden BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (user_id,preference_key),
 CHECK ((barcode IS NOT NULL AND item_id IS NULL AND preference_key='barcode:'||barcode)
 OR (barcode IS NULL AND item_id IS NOT NULL AND preference_key='item:'||item_id::text)),
 CHECK (NOT hidden OR photo_id IS NULL)
);
CREATE TABLE photo_review_audit (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 photo_id UUID NOT NULL REFERENCES product_photos(id),
 decision VARCHAR(8) NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
 previous_version BIGINT NOT NULL,
 reason VARCHAR(200),
 decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);