-- Urakan käytetty materiaali hoitoluokittain
CREATE TABLE urakan_materiaalin_kaytto_hoitoluokittain (
  pvm DATE,
  materiaalikoodi INTEGER REFERENCES materiaalikoodi (id),
  talvihoitoluokka INTEGER,
  urakka INTEGER REFERENCES urakka (id),
  maara NUMERIC
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
CREATE PROCEDURE paivita_urakan_materiaalin_kaytto_hoitoluokittain ()
  RETURNS TRIGGER AS $$
DECLARE
  rivi RECORD;
  u INTEGER;
BEGIN
  u := NEW.urakka;
  FOR rivi IN SELECT SUM(rm.maara) AS maara,
                     rm.materiaalikoodi,
		     rp.aika::DATE,
		     rp.talvihoitoluokka
                FROM reittipiste rp
	             JOIN reitti_materiaali rm ON rm.reittipiste = rp.id
	       WHERE rp.toteuma = NEW.id
	       GROUP BY rm.materiaalikoodi, rp.aika::DATE, rp.talvihoitoluokka
  LOOP
    -- Upsertataan uusi materiaalin määrä
    INSERT
      INTO urakan_materiaalin_kaytto_hoitoluokittain
           (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
    VALUES (rivi.aika, rivi.materiaalikoodi, rivi.talvihoitoluokka, u, rivi.maara)
           ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
           UPDATE SET maara = maara + rivi.maara;
  END LOOP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- Toteuman luontitransaktion lopuksi päivitetään materiaalin käyttö
CREATE CONSTRAINT TRIGGER tg_paivita_urakan_materiaalin_kaytto_hoitoluokittain
 AFTER INSERT
 ON toteuma
 DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW
 EXECUTE PROCEDURE paivita_urakan_materiaalin_kaytto_hoitoluokittain();

-- FIXME: mitä jos toteuma poistetaan?
-- tee triggeri, joka poistaa tai modaa ylempää tekemään määrää vähentävä
-- update poiston tapauksessa
