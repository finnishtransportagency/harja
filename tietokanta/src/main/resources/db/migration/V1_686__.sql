DROP TRIGGER IF EXISTS tg_paivita_urakan_materiaalin_kaytto_hoitoluokittain ON toteuma;

-- Vähentää toteuman poistetuksi (HUOM! UPDATE, ei DELETE) merkitsevän transaktion lopuksi materiaalit
CREATE OR REPLACE FUNCTION vahenna_urakan_materiaalin_kayttoa_hoitoluokittain ()
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
      SET maara = maara - rivi.summa
      WHERE pvm = rivi.aika AND
            materiaalikoodi = rivi.materiaalikoodi AND
            talvihoitoluokka = rivi.talvihoitoluokka AND
            urakka = u;
    END IF;
  END LOOP;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- Toteuman luontitransaktion lopuksi päivitetään materiaalin käyttö
CREATE CONSTRAINT TRIGGER tg_vahenna_urakan_materiaalin_kayttoa_hoitoluokittain
AFTER UPDATE
  ON toteuma
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
WHEN (NEW.lahde = 'harja-api')
EXECUTE PROCEDURE vahenna_urakan_materiaalin_kayttoa_hoitoluokittain();
