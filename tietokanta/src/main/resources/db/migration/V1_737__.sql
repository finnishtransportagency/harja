CREATE INDEX pohjavesialue_alue_idx ON pohjavesialue USING GIST (alue);

CREATE TABLE suolatoteuma_reittipiste (
       toteuma INTEGER,
       aika TIMESTAMP DEFAULT NOW(),
       pohjavesialue VARCHAR(16),
       sijainti POINT,
       materiaalikoodi INTEGER,
       maara NUMERIC
);

CREATE INDEX suolatoteuma_toteuma_idx ON suolatoteuma_reittipiste (toteuma);
CREATE INDEX suolatoteuma_reittipiste_idx ON suolatoteuma_reittipiste USING GIST(sijainti);
CREATE INDEX suolatoteuma_pohjavesialue_idx ON suolatoteuma_reittipiste (pohjavesialue);
CREATE INDEX suolatoteuma_pohjavesialue_aika ON suolatoteuma_reittipiste (aika);

CREATE OR REPLACE FUNCTION pisteen_pohjavesialue(piste POINT, threshold INTEGER) RETURNS VARCHAR AS $$
DECLARE
  pohjavesialue VARCHAR;
BEGIN
  SELECT tunnus FROM pohjavesialue
   WHERE ST_DWithin(alue, piste::geometry, threshold)
   ORDER BY ST_Distance(alue, piste::geometry)
   LIMIT 1
   INTO pohjavesialue;
  RETURN pohjavesialue;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION konvertoi_urakan_suolatoteumat(urakkaid INTEGER, threshold INTEGER) RETURNS VOID AS $$
DECLARE
  cur CURSOR FOR SELECT rp.toteuma,
  	     	 	rp.luotu,
			(unnest(rp.reittipisteet)).*
		FROM toteuman_reittipisteet rp
  		LEFT JOIN toteuma tot ON tot.id = rp.toteuma
  		WHERE tot.urakka = urakkaid;
  tmp INTEGER[];
  suolamateriaalikoodit INTEGER[];
  m reittipiste_materiaali;
BEGIN
  SELECT array_agg(id) FROM materiaalikoodi
   WHERE materiaalityyppi='talvisuola' INTO suolamateriaalikoodit;
  
  FOR r IN cur LOOP
    FOREACH m IN ARRAY r.materiaalit LOOP
      IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue) 
             VALUES (r.toteuma,
	     	    r.aika,
		    r.sijainti,
		    m.materiaalikoodi,
		    m.maara,
		    pisteen_pohjavesialue(r.sijainti, threshold));
      END IF;
    END LOOP;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION tr_valin_suolatoteumat(urakkaid INTEGER, tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER, threshold INTEGER, alkuaika TIMESTAMP, loppuaika TIMESTAMP) RETURNS TABLE (
       rivinumero BIGINT,
       materiaali_id INTEGER,
       materiaali_nimi VARCHAR,
       pvm TIMESTAMP,
       maara NUMERIC,
       lukumaara INTEGER,
       toteumaidt INTEGER[],
       koneellinen BOOLEAN) AS $$
DECLARE
  g geometry;
BEGIN
  SELECT tierekisteriosoitteelle_viiva(tie_, aosa_, aet_, losa_, let_) INTO g;
  
  RETURN QUERY SELECT row_number() OVER ()            AS rivinumero,
  	       	      mk.id                           AS materiaali_id,
  	       	      mk.nimi                         AS materiaali_nimi,
		      date_trunc('day', tot.alkanut)  AS pvm,
  	       	      SUM(rp.maara)                   AS maara,
		      count(rp.maara)::integer        AS lukumaara,
		      array_agg(tot.id)               AS toteumaidt,
		      TRUE                            AS koneellinen
    FROM suolatoteuma_reittipiste AS rp
      JOIN toteuma tot ON (tot.id = rp.toteuma AND tot.poistettu IS NOT TRUE)
      JOIN materiaalikoodi mk ON rp.materiaalikoodi = mk.id
      LEFT JOIN kayttaja k ON tot.luoja = k.id
    WHERE tot.urakka = urakkaid
      AND ST_DWithin(g, rp.sijainti::geometry, threshold)
      AND aika BETWEEN alkuaika AND loppuaika
    GROUP BY mk.id, tot.alkanut;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS $$
DECLARE
  m reittipiste_materiaali;
  rp reittipistedata;
  suolamateriaalikoodit INTEGER[];
BEGIN
  SELECT array_agg(id) FROM materiaalikoodi
   WHERE materiaalityyppi='talvisuola' INTO suolamateriaalikoodit;

  IF (TG_OP = 'UPDATE') THEN
    DELETE FROM suolatoteuma_reittipiste WHERE toteuma=NEW.toteuma;
  END IF;
  
  FOREACH rp IN ARRAY NEW.reittipisteet LOOP
    FOREACH m IN ARRAY rp.materiaalit LOOP
      IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue)
            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pisteen_pohjavesialue(rp.sijainti, 50));
      END IF;
    END LOOP;
  END LOOP;
    
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER toteuman_reittipisteet_trigger AFTER INSERT OR UPDATE ON toteuman_reittipisteet
   FOR EACH ROW EXECUTE PROCEDURE toteuman_reittipisteet_trigger_fn();
