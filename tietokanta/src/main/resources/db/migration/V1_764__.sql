ALTER TABLE pohjavesialue_talvisuola ADD tie INTEGER NOT NULL DEFAULT 0;

DROP MATERIALIZED VIEW pohjavesialue_kooste;
CREATE MATERIALIZED VIEW pohjavesialue_kooste AS (SELECT nimi, tunnus, alue, pituus, tie, alkuosa, alkuet, loppuosa, loppuet FROM (SELECT array_agg(id) AS id, 
       (array_agg(nimi))[1] AS nimi, 
       (array_agg(tunnus))[1] AS tunnus, 
       st_linemerge(st_collectionextract(st_collect(alue),2)) AS alue,
       st_length(st_collect(alue)) AS pituus,
       min(tr_numero) AS tie,
       min(tr_alkuosa) AS alkuosa,
       min(tr_alkuetaisyys) AS alkuet,
       max(tr_loppuosa) AS loppuosa,
       max(tr_loppuetaisyys) AS loppuet
 FROM pohjavesialue
 GROUP BY tr_numero, tr_alkuosa) AS q);

CREATE TYPE pisteen_pohjavesialue_tie AS (tunnus VARCHAR(32), tie INTEGER);
ALTER TYPE pisteen_pohjavesialue_tie ADD ATTRIBUTE alkuosa INTEGER;
ALTER TYPE pisteen_pohjavesialue_tie ADD ATTRIBUTE alkuet INTEGER;
ALTER TYPE pisteen_pohjavesialue_tie ADD ATTRIBUTE loppuosa INTEGER;
ALTER TYPE pisteen_pohjavesialue_tie ADD ATTRIBUTE loppuet INTEGER;

ALTER TABLE suolatoteuma_reittipiste ADD tie INTEGER;

ALTER TABLE suolatoteuma_reittipiste ADD alkuosa INTEGER;
ALTER TABLE suolatoteuma_reittipiste ADD alkuet INTEGER;
ALTER TABLE suolatoteuma_reittipiste ADD loppuosa INTEGER;
ALTER TABLE suolatoteuma_reittipiste ADD loppuet INTEGER;

CREATE OR REPLACE FUNCTION pisteen_pohjavesialue_ja_tie(piste POINT, threshold INTEGER) RETURNS pisteen_pohjavesialue_tie AS $$
DECLARE
  tulos pohjavesialue_kooste%ROWTYPE;
BEGIN
  SELECT INTO tulos * FROM pohjavesialue_kooste
   WHERE ST_DWithin(alue, piste::geometry, threshold)
   ORDER BY ST_Distance(alue, piste::geometry)
   LIMIT 1;
   RETURN ROW(tulos.tunnus, tulos.tie, tulos.alkuosa, tulos.alkuet, tulos.loppuosa, tulos.loppuet)::pisteen_pohjavesialue_tie;
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
        pohjavesialue_tie := pisteen_pohjavesialue_ja_tie(r.sijainti, threshold);
        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, tie, alkuosa, alkuet, loppuosa, loppuet) 
             VALUES (r.toteuma,
	     	    r.aika,
		    r.sijainti,
		    m.materiaalikoodi,
		    m.maara,
		    pohjavesialue_tie.tunnus,
		    pohjavesialue_tie.tie,
		    pohjavesialue_tie.alkuosa,
		    pohjavesialue_tie.alkuet,
		    pohjavesialue_tie.loppuosa,
		    pohjavesialue_tie.loppuet);
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
        pohjavesialue_tie := pisteen_pohjavesialue_ja_tie(rp.sijainti, 50);
        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, tie, alkuosa, alkuet, loppuosa, loppuet)
            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tie.tunnus, pohjavesialue_tie.tie,
		    pohjavesialue_tie.alkuosa,
		    pohjavesialue_tie.alkuet,
		    pohjavesialue_tie.loppuosa,
		    pohjavesialue_tie.loppuet);
      END IF;
    END LOOP;
  END LOOP;
    
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;
