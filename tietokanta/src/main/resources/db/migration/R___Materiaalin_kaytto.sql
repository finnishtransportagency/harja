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
        AND urakka = u;

  -- Päivitä materiaalin käyttö ko. pvm:lle ja urakalle
  FOR rivi IN SELECT t.urakka, rp.talvihoitoluokka AS talvihoitoluokka, mat.materiaalikoodi,
                               sum(mat.maara) as summa,
                rp.aika::DATE
              FROM toteuma t
                JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
                JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                JOIN LATERAL unnest(rp.materiaalit) mat ON true
              WHERE t.alkanut BETWEEN alkupvm::DATE AND (loppupvm + interval '1 day')::DATE
                    AND t.urakka = u AND t.poistettu IS FALSE
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
         AND t.urakka = u AND t.poistettu IS FALSE
   GROUP BY t.urakka, talvihoitoluokka, tm.materiaalikoodi, t.alkanut::DATE
  LOOP
    RAISE NOTICE 'INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain  rivi: %', rivi;
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


-- Päivitä kaikki materiaalin käyttö päivämäärälle
CREATE OR REPLACE FUNCTION paivita_materiaalin_kaytto_hoitoluokittain_paivalle(pvm_ DATE)
  RETURNS VOID AS $$
DECLARE
  rivi RECORD;
BEGIN
  DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE pvm = pvm_;
  FOR rivi IN SELECT t.urakka, rp.talvihoitoluokka, mat.materiaalikoodi,
                     sum(mat.maara) as maara
      	        FROM toteuma t
                     JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
                     JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                     JOIN LATERAL unnest(rp.materiaalit) mat ON true
               WHERE t.alkanut BETWEEN pvm_ AND (pvm_ + interval '1 day')::DATE
                 AND t.poistettu IS FALSE
            GROUP BY t.urakka, rp.talvihoitoluokka, mat.materiaalikoodi
                     -- Tässä otetaan talteen erikoiskäsittelyllä kaikki käsin syötetyt toteumat, ja annetaan niille
                     -- hoitoluokaksi 100 eli 'ei tiedossa', ks. esim VHAR-4204
   UNION
  SELECT t.urakka, 100 AS talvihoitoluokka, tm.materiaalikoodi,
         sum(tm.maara) as maara
    FROM toteuma t
             JOIN toteuma_materiaali tm ON tm.toteuma = t.id and tm.poistettu IS FALSE
   WHERE t.lahde = 'harja-ui' and
         t.alkanut BETWEEN pvm_ AND (pvm_ + interval '1 day')::DATE
     AND t.poistettu IS FALSE
   GROUP BY t.urakka, talvihoitoluokka, tm.materiaalikoodi, t.alkanut::DATE
  LOOP
    INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
                (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
         VALUES (pvm_,
	         rivi.materiaalikoodi,
		 COALESCE(rivi.talvihoitoluokka, 100),
		 rivi.urakka,
		 rivi.maara)
    ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
    UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara;
  END LOOP;
  RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_materiaalin_kaytto_hoitoluokittain_aikavalille(alku DATE, loppu DATE)
  RETURNS VOID AS $$
DECLARE
  pvm DATE;
BEGIN
  pvm := alku;
  LOOP
    IF pvm > loppu THEN
      EXIT;
    END IF;
    -- RAISE NOTICE 'Päivitetään materiaalin käyttö hoitoluokittain: %', pvm;
    PERFORM paivita_materiaalin_kaytto_hoitoluokittain_paivalle(pvm);
    pvm := pvm + 1;
 END LOOP;
 RETURN;
END;
$$ LANGUAGE plpgsql;
