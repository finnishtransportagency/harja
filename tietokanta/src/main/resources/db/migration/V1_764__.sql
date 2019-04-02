CREATE TYPE pisteen_pohjavesialue_tie AS (tunnus VARCHAR(32), tie INTEGER);

ALTER TABLE suolatoteuma_reittipiste ADD tie INTEGER;

CREATE OR REPLACE FUNCTION pisteen_pohjavesialue_ja_tie(piste POINT, threshold INTEGER) RETURNS pisteen_pohjavesialue_tie AS $$
DECLARE
  result pisteen_pohjavesialue_tie;
BEGIN
  SELECT tunnus, tie FROM pohjavesialue_kooste
   WHERE ST_DWithin(alue, piste::geometry, threshold)
   ORDER BY ST_Distance(alue, piste::geometry)
   LIMIT 1
   INTO result;
  RETURN result;
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
  pohjavesialue_tie pisteen_pohjavesialue_tie;
BEGIN
  SELECT array_agg(id) FROM materiaalikoodi
   WHERE materiaalityyppi='talvisuola' INTO suolamateriaalikoodit;
  
  FOR r IN cur LOOP
    FOREACH m IN ARRAY r.materiaalit LOOP
      IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
        SELECT pisteen_pohjavesialue_ja_tie(r.sijainti, threshold) INTO pohjavesialue_tie;
        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, tie) 
             VALUES (r.toteuma,
	     	    r.aika,
		    r.sijainti,
		    m.materiaalikoodi,
		    m.maara,
		    pohjavesialue_tie.tunnus,
		    pohjavesialue_tie.tie);
      END IF;
    END LOOP;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS $$
DECLARE
  m reittipiste_materiaali;
  rp reittipistedata;
  suolamateriaalikoodit INTEGER[];
  pohjavesialue_tie pisteen_pohjavesialue_tie;
BEGIN
  SELECT array_agg(id) FROM materiaalikoodi
   WHERE materiaalityyppi='talvisuola' INTO suolamateriaalikoodit;

  IF (TG_OP = 'UPDATE') THEN
    DELETE FROM suolatoteuma_reittipiste WHERE toteuma=NEW.toteuma;
  END IF;
  
  FOREACH rp IN ARRAY NEW.reittipisteet LOOP
    FOREACH m IN ARRAY rp.materiaalit LOOP
      IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
        SELECT pisteen_pohjavesialue_ja_tie(rp.sijainti, 50) INTO pohjavesialue_tie;
        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, tie)
            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tie.tunnus, pohjavesialue_tie.tie);
      END IF;
    END LOOP;
  END LOOP;
    
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;
