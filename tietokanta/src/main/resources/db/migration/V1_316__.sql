-- Tee toteuman ulkoinen_id+luoja parista uniikki

DROP INDEX toteuma_ulkoinen_id_luoja;

CREATE UNIQUE INDEX toteuma_ulkoinen_id_luoja
    ON toteuma (ulkoinen_id, luoja)
    WHERE ulkoinen_id IS NOT NULL;
