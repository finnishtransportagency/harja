
-- Tämä ottaa kokonaisuudessaan n. 2min per miljoona riviä. Ja rivejä on n. 116m, eli 220min = 3h 40min.
CALL siirra_toteuma_tehtava_partitioihin(100000, 1, 100000);
--CALL siirra_toteuma_tehtava_partitioihin(200000, 100001, 1000000); -- tuotannossa
--CALL siirra_toteuma_tehtava_partitioihin(200000, 1000001, 5000000);
--CALL siirra_toteuma_tehtava_partitioihin(200000, 48600001, 116280000);

-- Indeksit (luodaan jokaiselle partitiolle automaattisesti)
CREATE INDEX ON toteuma_tehtava_part (urakka_id, poistettu);
CREATE INDEX ON toteuma_tehtava_part (toimenpidekoodi);
CREATE INDEX ON toteuma_tehtava_part (toteuma);

-- Poistetaan vanhaan tauluun viittaava materialized view
DROP MATERIALIZED VIEW IF EXISTS raportti_toteutuneet_materiaalit;

-- Vanhan taulun korvaaminen - Tässä taulu menee lukkoon.
-- Eli mikään haku ei toimi. Tässä ei tosin mene kovin kauaa
BEGIN;
ALTER TABLE toteuma_tehtava
    RENAME TO toteuma_tehtava_vanha;
ALTER TABLE toteuma_tehtava_part
    RENAME TO toteuma_tehtava;
-- Sequence-korjaus
SELECT setval('toteuma_tehtava_id_seq', (SELECT MAX(id) FROM toteuma_tehtava));
COMMIT;




-- Siirrossa menee muutama tunti
CALL siirra_toteuma_materiaali_partitioihin(100000, 1, 100000);
--CALL siirra_toteuma_materiaali_partitioihin(200000, 100001, 1000000); -- tuotannossa
--CALL siirra_toteuma_materiaali_partitioihin(200000, 1000001, 5000000);
--CALL siirra_toteuma_materiaali_partitioihin(200000, 5000001, 112000000);

-- Indeksit
CREATE INDEX ON toteuma_materiaali_part (urakka_id, poistettu);
CREATE INDEX ON toteuma_materiaali_part (materiaalikoodi);
CREATE INDEX ON toteuma_materiaali_part (toteuma);
CREATE INDEX toteuma_materiaali_urakka_hk_idx
    ON toteuma_materiaali_part (urakka_id, hoitokauden_alkuvuosi)
    WHERE poistettu = FALSE;

-- Vanhan taulun korvaaminen - downtime-ikkuna
-- Eli taulu on lukossa tämän ajan, mutta tässä ei mene kovin kauaa
BEGIN;
ALTER TABLE toteuma_materiaali
    RENAME TO toteuma_materiaali_vanha;
ALTER TABLE toteuma_materiaali_part
    RENAME TO toteuma_materiaali;
COMMIT;

-- Sequence-korjaus rename-vaiheen jälkeen
SELECT setval('toteuma_materiaali_id_seq', (SELECT MAX(id) FROM toteuma_materiaali));

-- Luodaan aiemmin poistettu materialized view uudestaan.
CREATE MATERIALIZED VIEW raportti_toteutuneet_materiaalit AS
SELECT SUM(tm.maara)                AS kokonaismaara,
       t.urakka                     AS "urakka-id",
       mk.id                        AS "materiaali-id",
       date_trunc('day', t.alkanut) AS paiva
FROM toteuma_materiaali tm
         JOIN toteuma t ON t.id = tm.toteuma AND t.poistettu IS NOT TRUE
         LEFT JOIN materiaalikoodi mk ON mk.id = tm.materiaalikoodi
WHERE tm.poistettu = FALSE
GROUP BY "urakka-id", paiva, "materiaali-id";

CREATE OR REPLACE FUNCTION paivita_raportti_toteutuneet_materiaalit()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
    RETURN;
END;
$$ LANGUAGE plpgsql;


-- Vanha poistetaan myöhemmin kun varmistettu toimivuus
DROP TABLE toteuma_tehtava_vanha;
DROP TABLE toteuma_materiaali_vanha;
