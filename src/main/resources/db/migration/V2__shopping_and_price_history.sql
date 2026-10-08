ALTER TABLE products ADD CONSTRAINT products_owner_id UNIQUE (user_id, id);
ALTER TABLE supermarkets ADD CONSTRAINT supermarkets_owner_id UNIQUE (user_id, id);

CREATE TABLE shopping_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    supermarket_id UUID NOT NULL,
    budget_cents BIGINT CHECK (budget_cents IS NULL OR budget_cents BETWEEN 1 AND 100000000),
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELED')),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    checkout_total_cents BIGINT CHECK (checkout_total_cents IS NULL OR checkout_total_cents >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (user_id, id),
    FOREIGN KEY (user_id, supermarket_id) REFERENCES supermarkets(user_id, id),
    CHECK ((status = 'ACTIVE' AND finished_at IS NULL AND checkout_total_cents IS NULL)
        OR (status IN ('COMPLETED', 'CANCELED') AND finished_at IS NOT NULL AND finished_at >= started_at)),
    CHECK (status <> 'CANCELED' OR checkout_total_cents IS NULL)
);
CREATE UNIQUE INDEX one_active_session_per_owner ON shopping_sessions(user_id) WHERE status = 'ACTIVE';

CREATE TABLE shopping_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    product_id UUID,
    product_name_snapshot VARCHAR(120) NOT NULL CHECK (product_name_snapshot ~ '[^[:space:]]'),
    measurement_type VARCHAR(8) NOT NULL CHECK (measurement_type IN ('UNIT', 'WEIGHT')),
    pricing_type VARCHAR(8) NOT NULL CHECK (pricing_type IN ('REGULAR', 'BUNDLE')),
    reference_price_cents BIGINT NOT NULL CHECK (reference_price_cents BETWEEN 1 AND 100000000),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 9999),
    weight_grams INTEGER,
    bundle_quantity INTEGER,
    position INTEGER NOT NULL CHECK (position >= 0),
    subtotal_cents BIGINT GENERATED ALWAYS AS (
        CASE WHEN measurement_type = 'WEIGHT' THEN (reference_price_cents * weight_grams + 500) / 1000
             WHEN pricing_type = 'BUNDLE' THEN reference_price_cents * (quantity / bundle_quantity)
             ELSE reference_price_cents * quantity END
    ) STORED,
    UNIQUE (user_id, session_id, id),
    FOREIGN KEY (user_id, session_id) REFERENCES shopping_sessions(user_id, id),
    FOREIGN KEY (user_id, product_id) REFERENCES products(user_id, id),
    CHECK ((measurement_type = 'UNIT' AND pricing_type = 'REGULAR' AND weight_grams IS NULL AND bundle_quantity IS NULL)
        OR (measurement_type = 'WEIGHT' AND pricing_type = 'REGULAR' AND quantity = 1
            AND weight_grams IS NOT NULL AND weight_grams BETWEEN 1 AND 9999999 AND bundle_quantity IS NULL)
        OR (measurement_type = 'UNIT' AND pricing_type = 'BUNDLE' AND weight_grams IS NULL
            AND bundle_quantity IS NOT NULL AND bundle_quantity BETWEEN 2 AND 9999 AND quantity % bundle_quantity = 0))
);
CREATE INDEX shopping_items_session ON shopping_items(user_id, session_id, position);

CREATE TABLE price_observations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    supermarket_id UUID NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    comparison_basis VARCHAR(4) NOT NULL CHECK (comparison_basis IN ('UNIT', 'KG')),
    normalized_price_cents BIGINT NOT NULL CHECK (normalized_price_cents >= 0),
    FOREIGN KEY (user_id, session_id, id) REFERENCES shopping_items(user_id, session_id, id),
    FOREIGN KEY (user_id, supermarket_id) REFERENCES supermarkets(user_id, id)
);
CREATE INDEX price_observations_owner_market_date ON price_observations(user_id, supermarket_id, observed_at DESC);

CREATE FUNCTION protect_shopping_session() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, carrim, pg_temp AS $$
BEGIN
    IF OLD.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Closed shopping session is immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.id IS DISTINCT FROM OLD.id OR NEW.user_id IS DISTINCT FROM OLD.user_id
        OR NEW.supermarket_id IS DISTINCT FROM OLD.supermarket_id OR NEW.started_at IS DISTINCT FROM OLD.started_at
        OR NEW.version <> OLD.version + 1) THEN
        RAISE EXCEPTION 'Invalid session identity or version change' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER protect_shopping_session BEFORE UPDATE OR DELETE ON shopping_sessions
    FOR EACH ROW EXECUTE FUNCTION protect_shopping_session();

CREATE FUNCTION protect_shopping_item() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, carrim, pg_temp AS $$
DECLARE parent_id UUID; parent_status VARCHAR(10);
BEGIN
    parent_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.session_id ELSE NEW.session_id END;
    SELECT status INTO parent_status FROM shopping_sessions WHERE id = parent_id FOR UPDATE;
    IF parent_status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'Items require an active session' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.id IS DISTINCT FROM OLD.id OR NEW.session_id IS DISTINCT FROM OLD.session_id
        OR NEW.user_id IS DISTINCT FROM OLD.user_id) THEN
        RAISE EXCEPTION 'Item identity cannot change' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER protect_shopping_item BEFORE INSERT OR UPDATE OR DELETE ON shopping_items
    FOR EACH ROW EXECUTE FUNCTION protect_shopping_item();

CREATE FUNCTION derive_price_observation() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, carrim, pg_temp AS $$
DECLARE parent shopping_sessions; line shopping_items;
BEGIN
    SELECT * INTO parent FROM shopping_sessions WHERE id = NEW.session_id;
    IF parent.status IS DISTINCT FROM 'COMPLETED' OR parent.user_id IS DISTINCT FROM NEW.user_id THEN
        RAISE EXCEPTION 'Price history requires a completed owned session' USING ERRCODE = '23514';
    END IF;
    SELECT * INTO line FROM shopping_items WHERE id = NEW.id AND user_id = NEW.user_id AND session_id = NEW.session_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Observation line does not belong to session' USING ERRCODE = '23514';
    END IF;
    NEW.supermarket_id := parent.supermarket_id;
    NEW.observed_at := parent.finished_at;
    NEW.comparison_basis := CASE WHEN line.measurement_type = 'WEIGHT' THEN 'KG' ELSE 'UNIT' END;
    NEW.normalized_price_cents := CASE WHEN line.pricing_type = 'BUNDLE'
        THEN round(line.reference_price_cents::numeric / line.bundle_quantity)::bigint ELSE line.reference_price_cents END;
    RETURN NEW;
END $$;
CREATE TRIGGER derive_price_observation BEFORE INSERT ON price_observations
    FOR EACH ROW EXECUTE FUNCTION derive_price_observation();

CREATE FUNCTION protect_price_observation() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, carrim, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'Price history is immutable' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER protect_price_observation BEFORE UPDATE OR DELETE ON price_observations
    FOR EACH ROW EXECUTE FUNCTION protect_price_observation();

CREATE FUNCTION require_complete_history() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, carrim, pg_temp AS $$
DECLARE current_status VARCHAR(10); item_count BIGINT; history_count BIGINT;
BEGIN
    SELECT status INTO current_status FROM shopping_sessions WHERE id = NEW.id;
    IF current_status = 'COMPLETED' THEN
        SELECT count(*) INTO item_count FROM shopping_items WHERE session_id = NEW.id;
        SELECT count(*) INTO history_count FROM price_observations WHERE session_id = NEW.id;
        IF item_count = 0 OR item_count <> history_count THEN
            RAISE EXCEPTION 'Completion requires items and complete price history' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER require_complete_history AFTER INSERT OR UPDATE ON shopping_sessions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_complete_history();
