CREATE TABLE mhu_suolasakko_talvisuolaraja_backup
(
    id                    SERIAL PRIMARY KEY,
    urakka                INT REFERENCES urakka,
    hoitokauden_alkuvuosi smallint,
    talvisuolaraja        numeric
);

COMMENT ON TABLE mhu_suolasakko_talvisuolaraja_backup IS 'Väliaikainen varmuuskopio-taulu, voidaan poistaa kun ollaan varmistettu, että talvisuolan suunniteltu määrä ei näy tuplana MH-Urakoilla';

INSERT INTO mhu_suolasakko_talvisuolaraja_backup
SELECT ss.id, urakka, hoitokauden_alkuvuosi, talvisuolaraja
FROM suolasakko ss
         JOIN urakka u ON u.id = ss.urakka
WHERE u.tyyppi = 'teiden-hoito'
  AND talvisuolaraja IS NOT NULL;

UPDATE suolasakko s
SET talvisuolaraja = null
FROM urakka u
WHERE s.urakka = u.id
  AND u.tyyppi = 'teiden-hoito';