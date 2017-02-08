-- Urakan käytetty materiaali hoitoluokittain
CREATE TABLE urakan_materiaalin_kaytto_hoitoluokittain (
  pvm DATE,
  materiaalikoodi INTEGER REFERENCES materiaalikoodi (id),
  talvihoitoluokka INTEGER,
  urakka INTEGER REFERENCES urakka (id),
  maara NUMERIC,
  CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain
      UNIQUE (pvm, materiaalikoodi, talvihoitoluokka, urakka)
);

-- Ajetaan alkutilanne sisään (tämä voi kestää muutaman minuutin)

INSERT
  INTO urakan_materiaalin_kaytto_hoitoluokittain
      (pvm, materiaalikoodi,talvihoitoluokka,urakka,maara)
SELECT rp.aika::date AS pvm,
       mk.id AS materiaalikoodi,
       rp.talvihoitoluokka,
       t.urakka,
       SUM(rm.maara) AS maara
  FROM reitti_materiaali rm
       JOIN reittipiste rp ON rm.reittipiste = rp.id
       JOIN toteuma t ON rp.toteuma = t.id
       JOIN materiaalikoodi mk ON rm.materiaalikoodi = mk.id
 GROUP BY rp.aika::date, mk.id, rp.talvihoitoluokka, t.urakka;


-- Päivitetään toteuman luovan transaktion lopuksi materiaalit
CREATE OR REPLACE FUNCTION paivita_urakan_materiaalin_kaytto_hoitoluokittain ()
  RETURNS TRIGGER AS $$
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
		     COALESCE(rp.talvihoitoluokka, 0) AS talvihoitoluokka
                FROM reittipiste rp
	             JOIN reitti_materiaali rm ON rm.reittipiste = rp.id
	       WHERE rp.toteuma = NEW.id
	       GROUP BY rm.materiaalikoodi, rp.aika::DATE, rp.talvihoitoluokka
  LOOP
    IF NEW.poistettu IS TRUE THEN
      RAISE NOTICE 'poistetaan toteuma, joten vähennettään materiaalia % määrä %', rivi.materiaalikoodi, rivi.summa;
      -- Toteuma on merkitty poistetuksi, vähennetään määrää
      UPDATE urakan_materiaalin_kaytto_hoitoluokittain
         SET maara = maara - rivi.summa
       WHERE pvm = rivi.aika AND
             materiaalikoodi = rivi.materiaalikoodi AND
 	     talvihoitoluokka = rivi.talvihoitoluokka AND
	     urakka = u;
    ELSE
      -- Upsertataan uusi materiaalin määrä
      INSERT
        INTO urakan_materiaalin_kaytto_hoitoluokittain
             (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
      VALUES (rivi.aika, rivi.materiaalikoodi, rivi.talvihoitoluokka, u, rivi.summa)
             ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
             UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara;
    END IF;
  END LOOP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- Toteuman luontitransaktion lopuksi päivitetään materiaalin käyttö
CREATE CONSTRAINT TRIGGER tg_paivita_urakan_materiaalin_kaytto_hoitoluokittain
 AFTER INSERT OR UPDATE
 ON toteuma
 DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW
 WHEN (NEW.lahde = 'harja-api')
 EXECUTE PROCEDURE paivita_urakan_materiaalin_kaytto_hoitoluokittain();
