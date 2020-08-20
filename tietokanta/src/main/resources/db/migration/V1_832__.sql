-- Refactoroi ylläpitokohteen aikataulutiedot omaan tauluun
CREATE TABLE toteuman_reitti (
    toteuma INTEGER PRIMARY KEY REFERENCES toteuma (id),
    reitti geometry
);


-- Migratoi olemassa oleva data uuteen tauluun
CREATE OR REPLACE FUNCTION migratoi_toteumien_reitit() RETURNS VOID AS
$BODY$
DECLARE
    rivi RECORD;
BEGIN
    FOR rivi IN SELECT * FROM toteuma
        LOOP
            INSERT INTO toteuman_reitti (toteuma, reitti)
            VALUES (rivi.id,
                    rivi.reitti);
        END LOOP;
    RETURN;
END
$BODY$
    LANGUAGE 'plpgsql';

SELECT * FROM migratoi_toteumien_reitit();
DROP FUNCTION migratoi_toteumien_reitit(); -- Ei tarvi tehdä kuin kerran

-- Poista reittisarake
ALTER TABLE harja.public.toteuma DROP COLUMN reitti;