CREATE TABLE johto_ja_hallintokorvaus_ennen_urakkaa
(
    id     SERIAL PRIMARY KEY,
    "kk-v" NUMERIC NOT NULL
);

-- Kopioidaan johto_ja_hallintokorvaus taulu temppi tauluun, koska kaikki sen data poistetaan tässä
-- Tän taulukon voi myöhemmpin poistaa
CREATE TABLE johto_ja_hallintokorvaus_TEMP AS
SELECT *
FROM johto_ja_hallintokorvaus;

-- Halutaan tiputtaa 'hoitokausi' sarake pois ja luoda sen tilalle 'vuosi' ja 'kuukausi' sarakkeet
ALTER TABLE johto_ja_hallintokorvaus
    DROP CONSTRAINT "johto_ja_hallintokorvaus_urakka-id_toimenkuva-id_maksukausi_key",
    ADD COLUMN vuosi INTEGER,
    ADD COLUMN kuukausi INTEGER,
    ADD COLUMN "ennen-urakkaa-id" INTEGER REFERENCES johto_ja_hallintokorvaus_ennen_urakkaa (id);

DO
$$
    DECLARE
        jh_korvaus         johto_ja_hallintokorvaus%ROWTYPE;
        integraatio        INTEGER;
        kuukausi_          INTEGER;
        vuosi_             INTEGER;
        vuodet             INTEGER[];
        kuukausi_vali      RECORD;
        kuukaudet          INTEGER[];
        kaytavat_kuukaudet INTEGER[];
        ennen_urakkaa_id   INTEGER;
        tunnit_            NUMERIC;
    BEGIN
        integraatio = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');

        FOR jh_korvaus IN (SELECT * FROM johto_ja_hallintokorvaus)
            LOOP
                IF jh_korvaus.hoitokausi = 0
                THEN
                    vuodet = (SELECT ARRAY [2019]::INTEGER[]);
                    INSERT INTO johto_ja_hallintokorvaus_ennen_urakkaa ("kk-v")
                    VALUES (4.5) RETURNING id
                        INTO ennen_urakkaa_id;
                    tunnit_ = jh_korvaus.tunnit * 4.5;
                ELSE
                    vuodet = (SELECT ARRAY [(SELECT 2018 + jh_korvaus.hoitokausi),
                                             (SELECT 2019 + jh_korvaus.hoitokausi)]::INTEGER[]);
                    ennen_urakkaa_id = NULL;
                    tunnit_ = jh_korvaus.tunnit;
                END IF;
                RAISE NOTICE 'toimenkuva-id: % , maksukausi: % , hoitokausi: %', jh_korvaus."toimenkuva-id", jh_korvaus.maksukausi, jh_korvaus.hoitokausi;
                SELECT CASE
                           WHEN toimenkuva = 'harjoittelija' THEN 5
                           WHEN toimenkuva = 'viherhoidosta vastaava henkilö' THEN 4
                           WHEN toimenkuva = 'hankintavastaava' AND jh_korvaus.hoitokausi = 0 THEN 10
                           WHEN jh_korvaus.maksukausi::maksukausi = 'talvi'::maksukausi THEN 10
                           WHEN jh_korvaus.maksukausi::maksukausi = 'kesa'::maksukausi THEN 5
                           ELSE 1
                           END AS aloitus_kuukausi,
                       CASE
                           WHEN toimenkuva = 'harjoittelija' THEN 8
                           WHEN toimenkuva = 'viherhoidosta vastaava henkilö' THEN 8
                           WHEN toimenkuva = 'hankintavastaava' AND jh_korvaus.hoitokausi = 0 THEN 10
                           WHEN jh_korvaus.maksukausi::maksukausi = 'talvi'::maksukausi THEN 4
                           WHEN jh_korvaus.maksukausi::maksukausi = 'kesa'::maksukausi THEN 9
                           ELSE 12
                           END AS lopetus_kuukausi
                INTO kuukausi_vali
                FROM johto_ja_hallintokorvaus_toimenkuva
                WHERE id = jh_korvaus."toimenkuva-id";

                IF kuukausi_vali.aloitus_kuukausi > kuukausi_vali.lopetus_kuukausi
                THEN
                    kuukaudet = (SELECT (SELECT array_agg(kk)
                                         FROM generate_series(kuukausi_vali.aloitus_kuukausi, 12) AS kk) ||
                                        (SELECT array_agg(kk)
                                         FROM generate_series(1, kuukausi_vali.lopetus_kuukausi) AS kk));
                ELSE
                    kuukaudet = (SELECT array_agg(kk)
                                 FROM generate_series(kuukausi_vali.aloitus_kuukausi,
                                                      kuukausi_vali.lopetus_kuukausi) AS kk);
                END IF;
                FOREACH vuosi_ IN ARRAY vuodet
                    LOOP
                        IF vuosi_ = vuodet[1]
                        THEN
                            kaytavat_kuukaudet = (SELECT array(
                                                                 SELECT unnest(kuukaudet)
                                                                 INTERSECT
                                                                 SELECT unnest(ARRAY [10, 11, 12])
                                                             ));
                        ELSE
                            kaytavat_kuukaudet = (SELECT array(
                                                                 SELECT unnest(kuukaudet)
                                                                 INTERSECT
                                                                 SELECT unnest((SELECT array_agg(kk) FROM generate_series(1, 9) AS kk))
                                                             ));
                        END IF;
                        FOREACH kuukausi_ IN ARRAY kaytavat_kuukaudet
                            LOOP
                                INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka,
                                                                      vuosi, kuukausi,
                                                                      "ennen-urakkaa-id", luotu, luoja, muokattu,
                                                                      muokkaaja)
                                VALUES (jh_korvaus."urakka-id", jh_korvaus."toimenkuva-id", tunnit_,
                                        jh_korvaus.tuntipalkka, vuosi_, kuukausi_,
                                        ennen_urakkaa_id, jh_korvaus.luotu, jh_korvaus.luoja, NOW(), integraatio);
                            END LOOP;
                    END LOOP;
            END LOOP;
        -- Poistetaan vanhat rivit
        DELETE
        FROM johto_ja_hallintokorvaus
        WHERE vuosi IS NULL;
    END
$$;

ALTER TABLE johto_ja_hallintokorvaus
    DROP COLUMN "kk-v",
    DROP COLUMN maksukausi,
    DROP COLUMN hoitokausi,
    ALTER COLUMN vuosi SET NOT NULL,
    ALTER COLUMN kuukausi SET NOT NULL,
    ADD CONSTRAINT uniikki_johto_ja_hallintokorvaus UNIQUE ("urakka-id", "toimenkuva-id", vuosi, kuukausi, "ennen-urakkaa-id");