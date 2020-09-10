CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE NOTICE 'update_toteuma_check_partition start for toteuma : %', NEW;
    RAISE NOTICE 'update_toteuma_check_partition start for toteuma id: %', NEW.id;
    RAISE NOTICE 'TG_TABLE_NAME %', TG_TABLE_NAME;
    RAISE NOTICE 'format %', format('DELETE FROM %I.%I WHERE id = %L::INTEGER;',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);

    EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
    INSERT INTO toteuma VALUES (NEW.*);
    RAISE NOTICE 'Siirto uuteen partitioon OK';
    RETURN NULL;

END;
$$
    LANGUAGE plpgsql;


CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_010101_200701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_200701_210101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_210101_210701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_210701_220101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_220101_220701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_220701_230101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_230101_230701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_230701_240101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_240101_240701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_240701_250101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_250101_991231
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();
