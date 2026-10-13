CREATE TABLE sync_heads (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    sequence_number BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE sync_changes (
    user_id UUID NOT NULL REFERENCES users(id),
    sequence_number BIGINT NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id UUID NOT NULL,
    PRIMARY KEY(user_id, sequence_number)
);
CREATE FUNCTION record_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE seq BIGINT;
BEGIN
    INSERT INTO carrim.sync_heads(user_id, sequence_number) VALUES (NEW.user_id, 1)
      ON CONFLICT(user_id) DO UPDATE SET sequence_number = carrim.sync_heads.sequence_number + 1
      RETURNING sequence_number INTO seq;
    INSERT INTO carrim.sync_changes(user_id,sequence_number,aggregate_type,aggregate_id)
      VALUES (NEW.user_id, seq, TG_ARGV[0], NEW.id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER products_sync AFTER INSERT OR UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION record_sync_change('PRODUCT');
CREATE TRIGGER supermarkets_sync AFTER INSERT OR UPDATE ON supermarkets
    FOR EACH ROW EXECUTE FUNCTION record_sync_change('SUPERMARKET');
CREATE TRIGGER shopping_sync AFTER INSERT OR UPDATE ON shopping_sessions
    FOR EACH ROW EXECUTE FUNCTION record_sync_change('SHOPPING_SESSION');
-- Seed existing rows without changing their data or versions.
WITH existing AS (
    SELECT user_id,'PRODUCT' AS aggregate_type,id FROM products
    UNION ALL SELECT user_id,'SUPERMARKET',id FROM supermarkets
    UNION ALL SELECT user_id,'SHOPPING_SESSION',id FROM shopping_sessions
), numbered AS (
    SELECT user_id,row_number() OVER(PARTITION BY user_id ORDER BY aggregate_type,id) AS seq,aggregate_type,id FROM existing
)
INSERT INTO sync_changes(user_id,sequence_number,aggregate_type,aggregate_id)
SELECT user_id,seq,aggregate_type,id FROM numbered;
INSERT INTO sync_heads(user_id,sequence_number)
SELECT user_id,max(sequence_number) FROM sync_changes GROUP BY user_id;
