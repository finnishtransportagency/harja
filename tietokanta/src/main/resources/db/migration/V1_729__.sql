ALTER TYPE reittipistedata ADD ATTRIBUTE pohjavesialue INT CASCADE;

CREATE OR REPLACE FUNCTION paivita_pohjavesialue_reittipistedataan(d reittipistedata[], threshold INT) RETURNS reittipistedata[] AS $$
DECLARE
    i INT;
    tmp reittipistedata;
BEGIN
    FOR i IN 1 .. array_upper(d,1) LOOP
    	tmp := d[i];
	SELECT kandidaatit.id FROM
	   (SELECT id, ST_Distance(alue, tmp.sijainti::geometry) AS etaisyys
	      FROM pohjavesialue
	      ORDER BY 2
	      LIMIT 1) AS kandidaatit
	   WHERE kandidaatit.etaisyys<threshold
	   INTO tmp.pohjavesialue;
	d[i] := tmp;
    END LOOP;
    RETURN d;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX pohjavesialue_alue_idx ON pohjavesialue USING GIST (alue);
