-- Päivittää (delete + upsert) reittitoteuman kirjauksen yhteydessä hoitoluokittaiset määrät
CREATE OR REPLACE FUNCTION paivita_urakan_materiaalin_kaytto_hoitoluokittain (
  urakka_id INTEGER, alkupvm DATE, loppupvm DATE)
  RETURNS void AS $$
DECLARE
  rivi RECORD;
  u INTEGER;
BEGIN
  u := urakka_id;

  -- Poista hoitoluokittainen materiaalicache kaikille reittipisteiden pvm:ille tässä urakassa
  DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain
  WHERE pvm BETWEEN alkupvm AND (loppupvm + interval '1 day')::DATE
        AND (u IS NULL or urakka = u);

  -- Päivitä materiaalin käyttö ko. pvm:lle ja urakalle
  FOR rivi IN SELECT t.urakka, rp.talvihoitoluokka AS talvihoitoluokka, mat.materiaalikoodi,
                               sum(mat.maara) as summa,
                rp.aika::DATE
              FROM toteuma t
                JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
                JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                JOIN LATERAL unnest(rp.materiaalit) mat ON true
              WHERE t.alkanut BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE
                    AND (u IS NULL OR t.urakka = u) AND t.poistettu IS FALSE
              GROUP BY t.urakka, rp.talvihoitoluokka, mat.materiaalikoodi, rp.aika::DATE
  -- Tässä otetaan talteen erikoiskäsittelyllä kaikki käsin syötetyt toteumat, ja annetaan niille
  -- hoitoluokaksi 100 eli 'ei tiedossa', ks. esim VHAR-4204
  UNION
  SELECT t.urakka, 100 AS talvihoitoluokka, tm.materiaalikoodi,
         sum(tm.maara) as summa,
         t.alkanut::DATE as aika
    FROM toteuma t
             JOIN toteuma_materiaali tm ON tm.toteuma = t.id and tm.poistettu IS FALSE
   WHERE t.lahde = 'harja-ui' and
         t.alkanut BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE
         AND (u IS NULL OR t.urakka = u) AND t.poistettu IS FALSE
   GROUP BY t.urakka, talvihoitoluokka, tm.materiaalikoodi, t.alkanut::DATE
  LOOP
    INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
    (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
    VALUES (rivi.aika,
            rivi.materiaalikoodi,
            COALESCE(rivi.talvihoitoluokka, 100),
            rivi.urakka,
            rivi.summa)
    ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
    UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara;
  END LOOP;

END;
$$ LANGUAGE plpgsql;


-- Päivitä kaikki materiaalin käyttö päivämäärävälille. Huom! Tätä ei tällä hetkellä käytetä sovelluslogiikassa,
-- mutta tämä on voimakas työkalu jos tarvii päivitellä tuotannossa datoja. Käytä harkiten.
CREATE OR REPLACE FUNCTION paivita_materiaalin_kaytto_hoitoluokittain_aikavalille(alku DATE, loppu DATE)
  RETURNS VOID AS $$
DECLARE
BEGIN
    -- Tässä passataan urakka-parametriksi NULL, jolloin päivitetään kaikki urakat ko. aikaväliltä
    PERFORM paivita_urakan_materiaalin_kaytto_hoitoluokittain(NULL, alku, loppu);
  RETURN;
END;
$$ LANGUAGE plpgsql;
