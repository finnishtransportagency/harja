ALTER TABLE toteuman_reittipisteet ADD COLUMN IF NOT EXISTS reittipisteiden_kopio reittipistedata[];
COMMENT ON COLUMN toteuman_reittipisteet.reittipisteiden_kopio IS 'Reittipisteiden varmuuskopio, kun ollaan siirretty talvisuolia pois kevyen liikenteen väyliltä.';

CREATE OR REPLACE FUNCTION siirra_talvisuola_kelvilta(urakka_ INTEGER, alku TIMESTAMP, loppu TIMESTAMP)
    RETURNS INTEGER AS
$$
DECLARE
    uusi_trp                 RECORD;
    maara                    INT   := 0;
    suolaus_tpk              INT   := (SELECT id
                                       FROM toimenpidekoodi
                                       WHERE nimi = 'Suolaus');
    suolaus_materiaalikoodit INT[] := (SELECT ARRAY_AGG(id)
                                       FROM materiaalikoodi
                                       WHERE materiaalityyppi IN ('talvisuola', 'formiaatti'));
    -- Niiden kelvien talvihoitoluokat, joita ei talvisuolata.
    kelvien_talvihoitoluokat INT[] := '{9, 10, 11}';
BEGIN
    FOR uusi_trp IN (SELECT tr.toteuma,
                            tr.luotu,
                            tr.reittipisteet
                     FROM toteuma t
                              INNER JOIN toteuman_reittipisteet tr on tr.toteuma = t.id
                     WHERE t.alkanut BETWEEN alku::DATE AND (loppu::DATE + interval '1 day')::DATE
                       AND (urakka_ IS NULL OR t.urakka = urakka_)
                       AND t.poistettu IS FALSE
                       AND array_length(tr.reittipisteet, 1) > 0
                       -- Vain talvisuola ja formiaatti-materiaalit ja suolaus-toimenpide.
                       AND (EXISTS(SELECT DISTINCT m.materiaalikoodi
                                   FROM unnest(tr.reittipisteet) trp
                                            JOIN LATERAL unnest(trp.materiaalit) m ON TRUE
                                   WHERE materiaalikoodi = ANY(suolaus_materiaalikoodit))
                         OR EXISTS(SELECT DISTINCT t.toimenpidekoodi
                                   FROM unnest(tr.reittipisteet) trp
                                            JOIN LATERAL unnest(trp.tehtavat) t ON TRUE
                                   WHERE t.toimenpidekoodi = suolaus_tpk))
                       -- Vain toteumat, joiden reittipisteet osuvat kelveille. 
                       AND EXISTS(SELECT DISTINCT trp.talvihoitoluokka
                                  FROM UNNEST(tr.reittipisteet) trp
                                  WHERE trp.talvihoitoluokka = ANY(kelvien_talvihoitoluokat)))
        LOOP
            UPDATE toteuman_reittipisteet trp
            SET reittipisteiden_kopio = trp.reittipisteet,
                reittipisteet = (SELECT ARRAY_AGG((rp.aika, rp.sijainti,
                                                   (CASE
                                                        WHEN rp.talvihoitoluokka = ANY (kelvien_talvihoitoluokat)
                                                            THEN hoitoluokka_pisteelle(rp.sijainti::GEOMETRY,
                                                                                       'talvihoito',
                                                                                       250, kelvien_talvihoitoluokat)
                                                        ELSE
                                                            rp.talvihoitoluokka
                                                       END),
                                                   rp.soratiehoitoluokka, rp.tehtavat,
                                                   rp.materiaalit)::reittipistedata)
                                 FROM unnest(uusi_trp.reittipisteet) rp)::reittipistedata[]
            WHERE trp.toteuma = uusi_trp.toteuma;
            maara := maara + count(uusi_trp.reittipisteet);
        END LOOP;

    PERFORM paivita_urakan_materiaalin_kaytto_hoitoluokittain(
            urakka_::INTEGER,
            alku::DATE,
            loppu::DATE);

    return maara;
END
$$ LANGUAGE plpgsql;