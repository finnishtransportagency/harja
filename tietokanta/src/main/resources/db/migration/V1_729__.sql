ALTER TYPE reittipistedata ADD ATTRIBUTE pohjavesialue INT CASCADE;

CREATE OR REPLACE FUNCTION paivita_pohjavesialue_reittipistedataan(d reittipistedata[]) RETURNS reittipistedata[] AS $$
DECLARE
    i INT;
    tmp reittipistedata;
BEGIN
    FOR i IN 1 .. array_upper(d,1) LOOP
    	tmp := d[i];
	SELECT id FROM pohjavesialue WHERE ST_Within(tmp.sijainti::geometry, alue) INTO tmp.pohjavesialue;
	d[i] := tmp;
    END LOOP;
    RETURN d;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX pohjavesialue_alue_idx ON pohjavesialue USING GIST (alue);
