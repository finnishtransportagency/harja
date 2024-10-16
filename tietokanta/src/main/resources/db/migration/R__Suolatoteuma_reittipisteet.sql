-- Tällä funktiolla voi ajaa uusiksi suolatoteuma_reittipisteet-taulun täyttämisen yhdelle urakalle.
-- Käytä ajankohta parametreja varovasti. Yhden vuoden toteumien läpikäynti voi viedä esim 30min.

CREATE OR REPLACE FUNCTION korjaa_suolatoteuma_reittipiste(alkaa DATE, loppuu DATE, urakkaid INTEGER)
    RETURNS VOID AS
$$
DECLARE
    loydetyt_toteuman_reittipisteet RECORD;
    m                               reittipiste_materiaali;
    rp                              reittipistedata;
    suolamateriaalikoodit           INTEGER[];
    edellinen_rp                    reittipistedata;
    pvo                             pohjavesialueen_osuus;
    ra                              rajoitusalueen_osuus;
BEGIN

    -- Kaikki materiaalikoodit, joita käytetään suolamateriaaleissa - eli ne materiaalit joita lisätään suolatoteuma_reittipiste -tauluun
    SELECT ARRAY_AGG(id) FROM materiaalikoodi WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti') INTO suolamateriaalikoodit;

    -- Poista kaikki suolatoteuma_reittipisteet, jotka ovat päivitysparametrien piirissä
    DELETE FROM suolatoteuma_reittipiste
     WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = urakkaid AND luotu BETWEEN alkaa AND loppuu);

    -- Haetaan toteumat ja niiden reittipisteet urakan ja aikavälin puitteissa. Käsitellään vain ei poistettuja
    FOR loydetyt_toteuman_reittipisteet IN
        SELECT tr.reittipisteet, t.id as toteuma
          FROM toteuma t
                   JOIN toteuman_reittipisteet tr ON t.id = tr.toteuma
         WHERE t.luotu BETWEEN alkaa AND loppuu
           and t.poistettu IS FALSE
           AND t.urakka = urakkaid
         ORDER BY t.luotu ASC

        LOOP
        -- Loopataan löydetyt reittipisteet läpi
        -- Ja jokaiselle löydetylle reittipiste arraylle pyöritetään oma looppi, jossa itse lisääminen suolatoteuma_reittpiste - tauluun tapahtuu
            FOREACH rp IN ARRAY loydetyt_toteuman_reittipisteet.reittipisteet
                LOOP
                    FOREACH m IN ARRAY rp.materiaalit
                        LOOP
                            IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                                IF edellinen_rp IS DISTINCT FROM NULL THEN
                                    SELECT tunnus, SUM(osuus)
                                      FROM pistevalin_pohjavesialueet(edellinen_rp.sijainti, rp.sijainti)
                                     GROUP BY tunnus
                                      INTO pvo;
                                    SELECT rajoitusalue, SUM(osuus)
                                      FROM pistevalin_rajoitusalueet(edellinen_rp.sijainti, rp.sijainti,urakkaid)
                                     GROUP BY rajoitusalue
                                      INTO ra;

                                    IF pvo.tunnus IS DISTINCT FROM NULL OR ra.rajoitusalue IS DISTINCT FROM NULL THEN
                                        FOR pvo IN (SELECT tunnus, SUM(osuus)
                                                      FROM pistevalin_pohjavesialueet(edellinen_rp.sijainti, rp.sijainti)
                                                     GROUP BY tunnus)
                                            LOOP
                                                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                      materiaalikoodi,
                                                                                      maara, pohjavesialue,
                                                                                      rajoitusalue_id)
                                                VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                        m.maara * pvo.osuus, pvo.tunnus,
                                                        NULL);
                                            END LOOP;

                                        FOR ra IN (SELECT rajoitusalue, SUM(osuus)
                                                     FROM pistevalin_rajoitusalueet(edellinen_rp.sijainti, rp.sijainti,urakkaid)
                                                    GROUP BY rajoitusalue)
                                            LOOP
                                                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                      materiaalikoodi,
                                                                                      maara, pohjavesialue,
                                                                                      rajoitusalue_id)
                                                VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                        m.maara * ra.osuus, NULL,
                                                        ra.rajoitusalue);
                                            END LOOP;
                                    ELSE
                                        -- Jos rajoitusalueita tai pohjavesialueita ei löydy, niin lisätään tauluun rivi ilman niitä
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara, pohjavesialue, rajoitusalue_id)
                                        VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara, NULL,
                                                NULL);
                                    END IF;
                                END IF;
                            END IF;
                        END LOOP;
                    edellinen_rp := rp;
                END LOOP;
        END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Tässä pari esimerkkiä, miten funktiota voi käyttää
--select korjaa_suolatoteuma_reittipiste('2024-03-01'::DATE, '2024-03-31'::DATE, 577); -- nilsiä
--select korjaa_suolatoteuma_reittipiste('2023-10-01'::DATE, '2024-10-01'::DATE, 516); -- oulu mhu
