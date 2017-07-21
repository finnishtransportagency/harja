-- Liite-tauluun pakolliset arvot
ALTER TABLE toteuma_liite ALTER COLUMN toteuma SET NOT NULL;
ALTER TABLE toteuma_liite ALTER COLUMN liite SET NOT NULL;