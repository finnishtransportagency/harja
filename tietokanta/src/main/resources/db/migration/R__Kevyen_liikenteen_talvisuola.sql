CREATE OR REPLACE FUNCTION siirra_talvisuola_kevlilta(urakka_ INTEGER, alku TIMESTAMP, loppu TIMESTAMP)
    RETURNS INTEGER AS
$$
DECLARE
    uusi_trp record;
    maara    int := 0;
BEGIN
    FOR uusi_trp IN (SELECT tr.toteuma,
                            tr.luotu,
                            tr.reittipisteet
                     FROM toteuma t
                              INNER JOIN toteuman_reittipisteet tr on tr.toteuma = t.id
                     WHERE t.alkanut BETWEEN alku::DATE AND (loppu::DATE + interval '1 day')::DATE
                       AND (urakka_ IS NULL OR t.urakka = urakka_)
                       AND t.poistettu IS false
                       AND array_length(tr.reittipisteet, 1) > 0)
        LOOP
            IF EXISTS(SELECT * FROM unnest(uusi_trp.reittipisteet) WHERE talvihoitoluokka IN (9, 10, 11)) THEN
                UPDATE toteuman_reittipisteet trp
                SET reittipisteet = (SELECT ARRAY_AGG((rp.aika, rp.sijainti,
                                                       (CASE
                                                            WHEN rp.talvihoitoluokka IN (9, 10, 11)
                                                                THEN hoitoluokka_pisteelle(rp.sijainti::GEOMETRY,
                                                                                           'talvihoito',
                                                                                           250, '{9,10,11}')
                                                            ELSE
                                                                rp.talvihoitoluokka
                                                           END),
                                                       rp.soratiehoitoluokka, rp.tehtavat,
                                                       rp.materiaalit)::reittipistedata)
                                     FROM unnest(uusi_trp.reittipisteet) rp)::reittipistedata[]
                WHERE trp.toteuma = uusi_trp.toteuma;
                maara := maara + count(uusi_trp.reittipisteet);
            END IF;
        END LOOP;


    PERFORM paivita_urakan_materiaalin_kaytto_hoitoluokittain(
            urakka_::INTEGER,
            alku::DATE,
            loppu::DATE);

    return maara;
END
$$ LANGUAGE plpgsql;
