-- Talvisuola ei kirjautunut APIn kautta Talvisuola Rakeinen materiaalin alle.
-- Tällä funktiolla voidaan korjata tilanne ajamalla sitä kuukauden jaksoissa

CREATE OR REPLACE FUNCTION korjaa_toteuma_reittipiste_materiaalikoodi(alkaa DATE, loppuu DATE)
    RETURNS INTEGER AS
$$
DECLARE
    tot_reittipiste_rivi RECORD;
    rr                   reittipistedata[];
    maara                INT := 0;
BEGIN

    FOR tot_reittipiste_rivi IN
        SELECT DISTINCT ON (tr.toteuma) tr.toteuma AS toteuma_id, tr.luotu, tr.reittipisteet
        FROM toteuman_reittipisteet tr
                 JOIN toteuma_materiaali tm ON tr.toteuma = tm.toteuma and tm.materiaalikoodi = 7
                 JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                 JOIN LATERAL unnest (rp.materiaalit) rpmat ON true and rpmat.materiaalikoodi is null
        WHERE tr.luotu between alkaa and (loppuu::DATE + interval '1 day')
        ORDER BY tr.toteuma asc

        LOOP
            -- Tallenna reittipisteet erilliseen muuttujaan, jotta päivityslauseesta saadaan helpommin luettava
            rr :=
                (SELECT ARRAY_AGG((rp.aika, rp.sijainti, rp.talvihoitoluokka, rp.soratiehoitoluokka, rp.tehtavat,
                    -- Varmista, että null tyyppiset materiaalit säilyvät samanlaisina null tyyppisinä myös päivityksen jälkeen
                                   CASE
                                       WHEN rp.materiaalit = '{}' THEN '{}'
                                       ELSE
                                           (SELECT ARRAY_AGG((CASE
                                                                  -- Aseta null materiaalikoodi -> 7. Tämä on se varsinainen korjaus
                                                                  WHEN mat.materiaalikoodi IS NULL THEN 7
                                                                  ELSE mat.materiaalikoodi END,
                                                              mat.maara)::reittipiste_materiaali)
                                            FROM unnest(rp.materiaalit) mat)::reittipiste_materiaali[]
                                       END
                    )::reittipistedata)
                 FROM unnest(tot_reittipiste_rivi.reittipisteet) rp)::reittipistedata[];

            -- Päivitä toteuman reittipisteet korjatuilla tiedoilla
            UPDATE toteuman_reittipisteet trp SET reittipisteet = rr WHERE trp.toteuma = tot_reittipiste_rivi.toteuma_id;

            -- Päivitetään myös toteuma taulun muokattu kenttä, jotta analytiikalle saadaan uusimmat data
            UPDATE toteuma SET muokattu = current_timestamp WHERE id = tot_reittipiste_rivi.toteuma_id;

            maara := maara + count(tot_reittipiste_rivi.toteuma_id);
        end loop;
    return maara;
END;
$$ LANGUAGE plpgsql;

-- Voit korjata kannan näin
--select korjaa_toteuma_reittipiste_materiaalikoodi('2022-05-01'::DATE,'2022-05-31'::DATE); -- 2601 - 20 sek
--select korjaa_toteuma_reittipiste_materiaalikoodi('2022-06-01'::DATE,'2022-06-30'::DATE); -- 14316 - 50sek
--select korjaa_toteuma_reittipiste_materiaalikoodi('2022-07-01'::DATE,'2022-09-30'::DATE); -- 43 - 30sek
--select korjaa_toteuma_reittipiste_materiaalikoodi('2022-10-01'::DATE,'2022-10-31'::DATE); -- 12490 - 48 sek
--select korjaa_toteuma_reittipiste_materiaalikoodi('2022-11-01'::DATE,'2022-11-30'::DATE); -- 129983
--select korjaa_toteuma_reittipiste_materiaalikoodi('2022-12-01'::DATE,'2022-12-31'::DATE); -- 252328 5min 15sek
--select korjaa_toteuma_reittipiste_materiaalikoodi('2023-01-01'::DATE,'2023-01-31'::DATE); -- 342225 6min 13 sek
--select korjaa_toteuma_reittipiste_materiaalikoodi('2023-02-01'::DATE,'2023-02-28'::DATE); -- 224550 - 4m 8s
-- ja raporti
--select paivita_materiaalin_kaytto_hoitoluokittain_aikavalille('2022-05-01'::DATE, '2023-02-28'::DATE); -- 3m 22s