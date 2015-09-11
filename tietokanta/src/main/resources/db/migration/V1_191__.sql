-- Nimi: toteuman ulkoinen id stringiksi
ALTER TABLE toteuma RENAME ulkoinen_id TO ulkoinen_id_vanha;
ALTER TABLE toteuma ADD COLUMN ulkoinen_id VARCHAR(1024) UNIQUE;
UPDATE toteuma SET ulkoinen_id = toteuma.ulkoinen_id_vanha;
ALTER TABLE toteuma DROP COLUMN ulkoinen_id_vanha;