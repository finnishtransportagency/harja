-- FIXME: muuta tämän nimi, placeholder


-- Reittipiste on jo käytetty nimi

CREATE TYPE reittipiste_materiaali AS (
  materiaalikoodi INTEGER,
  maara NUMERIC
);

CREATE TYPE reittipiste_tehtava AS (
  toimenpidekoodi INTEGER,
  maara NUMERIC
);

CREATE TYPE reittipistedata AS (
  aika TIMESTAMP,
  sijainti POINT,
  talvihoitoluokka INTEGER,
  soratiehoitoluokka INTEGER,
  tehtavat reittipiste_tehtava[],
  materiaalit reittipiste_materiaali[]
);

CREATE TABLE toteuman_reittipisteet (
  toteuma INTEGER PRIMARY KEY REFERENCES toteuma (id),
  luotu timestamp DEFAULT NOW(),
  reittipisteet reittipistedata[]
);

CREATE FUNCTION siirra_reittipisteet(toteumaid INTEGER) RETURNS INTEGER AS $$
DECLARE
  pist reittipistedata[];
  teht reittipiste_tehtava[];
  mat reittipiste_materiaali[];
  p reittipistedata;
  t reittipiste_tehtava;
  m reittipiste_materiaali;
  lkm INTEGER;
  rp RECORD;
  rpt RECORD;
  rpm RECORD;
BEGIN
  lkm := 0;
  pist := ARRAY[]::reittipistedata[];
  FOR rp IN SELECT * FROM reittipiste WHERE reittipiste.toteuma = toteumaid
  LOOP
    teht := ARRAY[]::reittipiste_tehtava[];
    mat := ARRAY[]::reittipiste_materiaali[];
    -- Hae pisteen tehtävät
    FOR rpt IN SELECT * FROM reitti_tehtava WHERE reitti_tehtava.reittipiste = rp.id
    LOOP
      teht := teht || (rpt.toimenpidekoodi, rpt.maara)::reittipiste_tehtava;
    END LOOP;
    -- Hae pisteen materiaalit
    FOR rpm IN SELECT * FROM reitti_materiaali WHERE reitti_materiaali.reittipiste = rp.id
    LOOP
      mat := mat || (rpm.materiaalikoodi, rpm.maara)::reittipiste_materiaali;
    END LOOP;
    -- Lisätään piste taulukkoon
    pist := pist || (rp.aika, rp.sijainti,
                     rp.talvihoitoluokka, rp.soratiehoitoluokka,
		     teht, mat)::reittipistedata;
    lkm := lkm + 1;
  END LOOP;
  INSERT INTO toteuman_reittipisteet (toteuma,reittipisteet) VALUES (toteumaid, pist);
  RETURN lkm;
END;
$$ LANGUAGE plpgsql;

-- FIXME: toteumassa oleva hoitoluokittainen triggeri pitää poistaa ja siirtää
-- tähän toteuman_reittipisteet tauluun.
