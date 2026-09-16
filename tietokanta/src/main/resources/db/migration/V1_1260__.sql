-- Nopeutetaan toteumahakuja lisäämällä hoitokauden alkuvuosi toteuma_tehtava- ja toteuma_materiaali-tauluihin, jotta voidaan hyödyntää indeksejä paremmin.
-- Mikäli tosielämässä tämäkään ei riitä, niin partitioidaan taulut hoitokauden alkavuoden mukaan, mutta toistaiseksi kokeillaan tätä kevyempää ratkaisua.
ALTER TABLE toteuma_tehtava
 ADD COLUMN hoitokauden_alkuvuosi INTEGER;

ALTER TABLE toteuma_materiaali
    ADD COLUMN hoitokauden_alkuvuosi INTEGER;


CREATE INDEX idx_toteuma_tehtava_urakka_hk
    ON toteuma_tehtava (urakka_id, hoitokauden_alkuvuosi, toimenpidekoodi)
    INCLUDE (maara, toteuma)
    WHERE poistettu = FALSE;

CREATE INDEX idx_toteuma_materiaali_urakka_hk
    ON toteuma_materiaali (materiaalikoodi, urakka_id, hoitokauden_alkuvuosi)
    INCLUDE (maara, toteuma)
    WHERE poistettu = FALSE;

-- Viedään tuotantoon valmiksii stored proceduuri, jolla päivitetään hoitokauden alkavuosi toteuma_tehtava- ja toteuma_materiaali-tauluihin.
-- Tämä voidaan laittaa esim screenissä pyörimään
CREATE OR REPLACE PROCEDURE paivita_hoitokauden_alkuvuosi_toteumittain(
    erakoko      INTEGER DEFAULT 50000,
    aloitus_id   BIGINT  DEFAULT NULL,
    lopetus_id   BIGINT  DEFAULT NULL
)
    LANGUAGE plpgsql
AS
$$
DECLARE
    min_id                         BIGINT;
    max_id                         BIGINT;
    nykyinen_id                    BIGINT;
    seuraava_id                    BIGINT;
    todellinen_max                 BIGINT;
    kokonaismaara                  BIGINT;
    aloitusaika                    TIMESTAMP;
    kulunut_sekunteina             NUMERIC;
    arvio_jaljella_s               NUMERIC;
    prosentin_osuus                NUMERIC;
    kasitelty_id_maara             BIGINT;
    koko_id_alue                   BIGINT;
    eranumero                      INTEGER := 0;
    paivitetyt_tehtavat_era        INTEGER;
    paivitetyt_materiaalit_era     INTEGER;
    paivitetyt_tehtavat_yhteensa   BIGINT := 0;
    paivitetyt_materiaalit_yhteensa BIGINT := 0;
BEGIN
    SELECT MIN(id), MAX(id), COUNT(*)
    INTO min_id, max_id, kokonaismaara
    FROM toteuma;

    IF min_id IS NULL OR max_id IS NULL THEN
        RAISE NOTICE 'Taulu toteuma on tyhjä, ei päivitettävää.';
        RETURN;
    END IF;

    nykyinen_id    := COALESCE(aloitus_id, min_id);
    todellinen_max := COALESCE(lopetus_id, max_id);
    aloitusaika    := clock_timestamp();

    IF nykyinen_id > todellinen_max THEN
        RAISE NOTICE 'Aloitus-id (%) on suurempi kuin lopetus-id (%), ei päivitettävää.',
            nykyinen_id, todellinen_max;
        RETURN;
    END IF;

    koko_id_alue := todellinen_max - nykyinen_id + 1;

    RAISE NOTICE '=== Aloitetaan hoitokauden_alkuvuosi-päivitys toteumittain ===';
    RAISE NOTICE 'Toteuma-id alue taulussa: % - %, ajettava alue: % - %, eräkoko: %, toteumia yhteensä: %',
        min_id, max_id, nykyinen_id, todellinen_max, erakoko, kokonaismaara;

    LOOP
        eranumero := eranumero + 1;
        seuraava_id := LEAST(nykyinen_id + erakoko, todellinen_max + 1);

        WITH toteumat_erassa AS (
            SELECT
                t.id,
                CASE
                    WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                        THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
                    ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
                    END AS hoitokauden_alkuvuosi
            FROM toteuma t
            WHERE t.id >= nykyinen_id
              AND t.id <  seuraava_id
        ),
             paivitetyt_tehtavat AS (
                 UPDATE toteuma_tehtava tt
                     SET hoitokauden_alkuvuosi = te.hoitokauden_alkuvuosi
                     FROM toteumat_erassa te
                     WHERE tt.toteuma = te.id
                         AND tt.hoitokauden_alkuvuosi IS DISTINCT FROM te.hoitokauden_alkuvuosi
                     RETURNING 1
             ),
             paivitetyt_materiaalit AS (
                 UPDATE toteuma_materiaali tm
                     SET hoitokauden_alkuvuosi = te.hoitokauden_alkuvuosi
                     FROM toteumat_erassa te
                     WHERE tm.toteuma = te.id
                         AND tm.hoitokauden_alkuvuosi IS DISTINCT FROM te.hoitokauden_alkuvuosi
                     RETURNING 1
             )
        SELECT
            (SELECT COUNT(*) FROM paivitetyt_tehtavat),
            (SELECT COUNT(*) FROM paivitetyt_materiaalit)
        INTO paivitetyt_tehtavat_era, paivitetyt_materiaalit_era;

        paivitetyt_tehtavat_yhteensa    := paivitetyt_tehtavat_yhteensa + paivitetyt_tehtavat_era;
        paivitetyt_materiaalit_yhteensa := paivitetyt_materiaalit_yhteensa + paivitetyt_materiaalit_era;

        kulunut_sekunteina := EXTRACT(EPOCH FROM clock_timestamp() - aloitusaika);
        kasitelty_id_maara := seuraava_id - COALESCE(aloitus_id, min_id);

        IF koko_id_alue > 0 THEN
            prosentin_osuus := ROUND((kasitelty_id_maara::NUMERIC / koko_id_alue::NUMERIC) * 100, 2);
        ELSE
            prosentin_osuus := 100;
        END IF;

        IF kasitelty_id_maara > 0 AND kulunut_sekunteina > 0 AND prosentin_osuus < 100 THEN
            arvio_jaljella_s := ROUND(
                (kulunut_sekunteina / kasitelty_id_maara::NUMERIC)
                    * (todellinen_max - seuraava_id + 1),
                0
                                );
        ELSE
            arvio_jaljella_s := 0;
        END IF;

        RAISE NOTICE 'Erä #% | toteuma-id % - % | tehtäviä päivitetty % (yht. %) | materiaaleja päivitetty % (yht. %) | %.2f %% | kulunut % s | arvio jäljellä % s',
            eranumero,
            nykyinen_id,
            seuraava_id - 1,
            paivitetyt_tehtavat_era,
            paivitetyt_tehtavat_yhteensa,
            paivitetyt_materiaalit_era,
            paivitetyt_materiaalit_yhteensa,
            prosentin_osuus,
            ROUND(kulunut_sekunteina),
            arvio_jaljella_s;

        COMMIT;

        nykyinen_id := seuraava_id;

        EXIT WHEN nykyinen_id > todellinen_max;
    END LOOP;

    RAISE NOTICE '=== Päivitys valmis === tehtäviä päivitetty yhteensä % | materiaaleja päivitetty yhteensä % | kokonaisaika % s',
        paivitetyt_tehtavat_yhteensa,
        paivitetyt_materiaalit_yhteensa,
        ROUND(EXTRACT(EPOCH FROM clock_timestamp() - aloitusaika));
END;
$$;
