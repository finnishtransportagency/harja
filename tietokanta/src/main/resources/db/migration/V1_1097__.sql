UPDATE geometriapaivitys
SET paikallinen = FALSE
WHERE nimi = 'tieturvallisuusverkko';

ALTER TABLE tieturvallisuusverkko
    DROP COLUMN tasoluokka,
    DROP COLUMN ely,
    DROP COLUMN luonne,
    ADD COLUMN paavaylan_luonne TEXT,
    ADD COLUMN vaylan_luonne TEXT;
