-- Tämän ajaminen tuotannossa tulee aiheuttamaan lukkoja.
-- Taulujen nimien muutos ei vie kauaa, mutta kun se on käynnissä, niin taulut ovat lukossa.

-- Poistetaan vanhaan tauluun viittaava materialized view
DROP MATERIALIZED VIEW IF EXISTS raportti_toteutuneet_materiaalit;

-- Vanhan taulun korvaaminen - Tässä taulu menee lukkoon.
-- Eli mikään haku ei toimi. Tässä ei tosin mene kovin kauaa
BEGIN;
    ALTER TABLE toteuma_tehtava
        RENAME TO toteuma_tehtava_vanha;

    ALTER TABLE toteuma_tehtava_part
        RENAME TO toteuma_tehtava;

    -- Sequence-korjaus rename-vaiheen jälkeen
    SELECT setval('toteuma_tehtava_id_seq', (SELECT MAX(id) FROM toteuma_tehtava));
COMMIT;


-- Vanhan taulun korvaaminen - downtime-ikkuna
-- Eli taulu on lukossa tämän ajan, mutta tässä ei mene kovin kauaa
BEGIN;
    ALTER TABLE toteuma_materiaali
        RENAME TO toteuma_materiaali_vanha;
    ALTER TABLE toteuma_materiaali_part
        RENAME TO toteuma_materiaali;

    -- Sequence-korjaus rename-vaiheen jälkeen
    SELECT setval('toteuma_materiaali_id_seq', (SELECT MAX(id) FROM toteuma_materiaali));
COMMIT;

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


-- Vanhat taulut voidaan poistaa jossain vaiheessa. Nämä täällä esimerkkinä.
-- DROP TABLE toteuma_tehtava_vanha;
-- DROP TABLE toteuma_materiaali_vanha;
