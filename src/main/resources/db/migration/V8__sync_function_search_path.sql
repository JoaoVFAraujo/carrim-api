-- The trigger uses fully qualified application tables; resolve built-ins only.
ALTER FUNCTION carrim.record_sync_change() SET search_path = pg_catalog;
