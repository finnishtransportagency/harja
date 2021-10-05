-- Hoidon alueurakoiden geometrian reikien korjaus

CREATE OR REPLACE FUNCTION hoidon_alueurakan_geometria(nro varchar) RETURNS GEOMETRY AS $$
  SELECT ST_Collect(ST_MakePolygon(s.the_geom))
    FROM (SELECT urakka.urakkanro, ST_ExteriorRing((ST_Dump(urakka.alue)).geom) AS the_geom
            FROM urakka urakka
	   WHERE urakka.urakkanro = nro AND urakka.tyyppi IN ('hoito', 'teiden-hoito'))  AS s
$$ LANGUAGE SQL IMMUTABLE;


CREATE OR REPLACE FUNCTION hoidon_paaurakan_geometria(uid INTEGER) RETURNS GEOMETRY AS $$
    SELECT ST_Collect(ARRAY(
              (SELECT ST_MakePolygon(
                    ST_ExteriorRing((ST_Dump(u.alue)).geom)) AS geom FROM urakka u WHERE id = uid)));
$$ LANGUAGE SQL IMMUTABLE;
