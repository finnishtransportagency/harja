CREATE OR REPLACE FUNCTION paivita_urakan_materiaalin_kaytto_hoitoluokittain (urakka_id integer, alkupvm date, loppupvm date) RETURNS void
AS $$
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
                    AND t.urakka = u AND t.poistettu IS NOT TRUE
              GROUP BY t.urakka, rp.talvihoitoluokka, mat.materiaalikoodi, rp.aika::DATE
  LOOP
    RAISE NOTICE 'INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain  rivi: %', rivi;
    INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
    (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara, muokattu)
    VALUES (rivi.aika,
            rivi.materiaalikoodi,
            COALESCE(rivi.talvihoitoluokka, 100),
            rivi.urakka,
            rivi.summa,
            CURRENT_TIMESTAMP)
    ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
    UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara,
    muokattu = CURRENT_TIMESTAMP;
  END LOOP;

END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_urakan_materiaalin_kaytto_hoitoluokittain () RETURNS trigger
AS $$
DECLARE
  rivi RECORD;
  rivi2 RECORD;
  u INTEGER;
BEGIN
  -- Jos toteuma on luotu tässä transaktiossa, ei käsitellä uudelleen päivitystä
  IF TG_OP = 'UPDATE' AND NEW.luotu = current_timestamp THEN
    RETURN NEW;
  END IF;
  --
  u := NEW.urakka;
  FOR rivi IN SELECT SUM(rm.maara) AS summa,
                     rm.materiaalikoodi,
		     rp.aika::DATE,
		     COALESCE(rp.talvihoitoluokka, 100) AS talvihoitoluokka
                FROM toteuman_reittipisteet tr
		     JOIN LATERAL unnest(tr.reittipisteet) rp ON true
		     JOIN LATERAL unnest(rp.materiaalit) rm ON true
	       WHERE tr.toteuma = NEW.id
	       GROUP BY rm.materiaalikoodi, rp.aika::DATE, rp.talvihoitoluokka
  LOOP
    IF NEW.poistettu IS TRUE THEN
      RAISE NOTICE 'poistetaan toteuma, joten vähennettään materiaalia % määrä %', rivi.materiaalikoodi, rivi.summa;
      -- Toteuma on merkitty poistetuksi, vähennetään määrää
      UPDATE urakan_materiaalin_kaytto_hoitoluokittain
         SET maara = maara - rivi.summa,
         muokattu = CURRENT_TIMESTAMP
       WHERE pvm = rivi.aika AND
             materiaalikoodi = rivi.materiaalikoodi AND
 	     talvihoitoluokka = rivi.talvihoitoluokka AND
	     urakka = u;
    END IF;
  END LOOP;

  -- Poista hoitoluokittainen materiaalicache kaikille reittipisteiden pvm:ille tässä urakassa
    DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain
    WHERE pvm BETWEEN NEW.alkanut::DATE AND (NEW.paattynyt + interval '1 day')::DATE
          AND urakka = u;

    -- Päivitä materiaalin käyttö ko. pvm:lle ja urakalle
    FOR rivi2 IN SELECT t.urakka, rp.talvihoitoluokka AS talvihoitoluokka, mat.materiaalikoodi,
                   sum(mat.maara) as summa,
                   rp.aika::DATE
                 FROM toteuma t
                   JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
                   JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                   JOIN LATERAL unnest(rp.materiaalit) mat ON true
                 WHERE t.alkanut BETWEEN NEW.alkanut::DATE AND (NEW.paattynyt + interval '1 day')::DATE
                       AND t.urakka = u AND t.poistettu IS NOT TRUE
                 GROUP BY t.urakka, rp.talvihoitoluokka, mat.materiaalikoodi, rp.aika::DATE
    LOOP
      -- RAISE NOTICE 'INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain  rivi2: %', rivi2;
      INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
      (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara, muokattu)
      VALUES (rivi2.aika,
              rivi2.materiaalikoodi,
              COALESCE(rivi2.talvihoitoluokka, 100),
              rivi2.urakka,
              rivi2.summa,
              CURRENT_TIMESTAMP)
      ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
      UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara,
      muokattu = CURRENT_TIMESTAMP;
    END LOOP;
  RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_materiaalin_kaytto_hoitoluokittain_paivalle (pvm_ date) RETURNS void
AS $$
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
               WHERE t.alkanut::date = pvm_
            GROUP BY t.urakka, rp.talvihoitoluokka, mat.materiaalikoodi
  LOOP
    INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
                (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara, muokattu)
         VALUES (pvm_,
	         rivi.materiaalikoodi,
		 COALESCE(rivi.talvihoitoluokka, 0),
		 rivi.urakka,
		 rivi.maara,
		 CURRENT_TIMESTAMP);
  END LOOP;
  RETURN;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_koko_sopimuksen_materiaalin_kaytto (sopimus integer) RETURNS void
AS $$
DECLARE
  mat RECORD;
  sop INTEGER;
BEGIN
  sop := sopimus;
  DELETE FROM sopimuksen_kaytetty_materiaali skm WHERE skm.sopimus = sop;
  FOR mat IN SELECT date_trunc('day',t.alkanut) as alkupvm, tm.materiaalikoodi,
                    SUM(CASE
                          WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
                          THEN tm.maara
                          ELSE 0
                        END) as maara
               FROM toteuma t
                    JOIN toteuma_materiaali tm ON tm.toteuma = t.id
              WHERE t.sopimus = sop
              GROUP BY date_trunc('day',t.alkanut), tm.materiaalikoodi
  LOOP
    INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
    VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara, CURRENT_TIMESTAMP);
  END LOOP;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_kaikki_sopimuksen_kaytetty_materiaali () RETURNS void
AS $$
BEGIN
  -- Poistetaan kaikki
  DELETE FROM sopimuksen_kaytetty_materiaali;

  -- Luodaan uudet haun perusteella
  INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
      SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara), NOW()
        FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
       WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
	GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION vahenna_urakan_materiaalin_kayttoa_hoitoluokittain () RETURNS trigger
AS $$
DECLARE
  rivi RECORD;
  u INTEGER;
BEGIN
  -- Jos toteuma on luotu tässä transaktiossa, ei käsitellä uudelleen päivitystä
  IF TG_OP = 'UPDATE' AND NEW.luotu = current_timestamp THEN
    RETURN NEW;
  END IF;
  --
  u := NEW.urakka;
  FOR rivi IN SELECT SUM(rm.maara) AS summa,
                rm.materiaalikoodi,
                rp.aika::DATE,
                     COALESCE(rp.talvihoitoluokka, 100) AS talvihoitoluokka
              FROM toteuman_reittipisteet tr
                JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                JOIN LATERAL unnest(rp.materiaalit) rm ON true
              WHERE tr.toteuma = NEW.id
              GROUP BY rm.materiaalikoodi, rp.aika::DATE, rp.talvihoitoluokka
  LOOP
    IF NEW.poistettu IS TRUE THEN
      RAISE NOTICE 'poistetaan toteuma, joten vähennettään materiaalia % määrä %', rivi.materiaalikoodi, rivi.summa;
      -- Toteuma on merkitty poistetuksi, vähennetään määrää
      UPDATE urakan_materiaalin_kaytto_hoitoluokittain
      SET maara = maara - rivi.summa,
      muokattu = CURRENT_TIMESTAMP
      WHERE pvm = rivi.aika AND
            materiaalikoodi = rivi.materiaalikoodi AND
            talvihoitoluokka = rivi.talvihoitoluokka AND
            urakka = u;
    END IF;
  END LOOP;

  RETURN NEW;
END;
$$
LANGUAGE plpgsql;
