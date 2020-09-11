CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
    RETURNS TRIGGER AS $$
BEGIN
    EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
    INSERT INTO toteuma VALUES (NEW.*);
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
