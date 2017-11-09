ALTER TABLE toteuma
DROP CONSTRAINT uniikki_ulkoinen_id_luoja;

ALTER TABLE toteuma
ADD CONSTRAINT uniikki_ulkoinen_id_luoja_urakka UNIQUE (ulkoinen_id, luoja, urakka);
