-- Hoidon alueurakoiden geometrian reikien korjaus

CREATE OR REPLACE FUNCTION hoidon_alueurakan_geometria(nro varchar) RETURNS GEOMETRY AS $$
  SELECT ST_Collect(ST_MakePolygon(s.the_geom))
    FROM (SELECT urakka.urakkanro, ST_ExteriorRing((ST_Dump(urakka.alue)).geom) AS the_geom
            FROM urakka urakka
	   WHERE urakka.urakkanro = nro)  AS s
$$ LANGUAGE SQL IMMUTABLE;
