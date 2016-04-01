-- Toteuman ulkoinen_id+luoja constraint

DROP INDEX toteuma_ulkoinen_id_luoja;

ALTER TABLE toteuma
ADD CONSTRAINT uniikki_ulkoinen_id_luoja UNIQUE (ulkoinen_id, luoja);
