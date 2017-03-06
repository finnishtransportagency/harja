-- Hoidon alueurakoiden geometrian reikien korjaus

CREATE OR REPLACE FUNCTION hoidon_alueurakan_geometria(nro varchar) RETURNS GEOMETRY AS $$
  SELECT ST_Collect(ST_MakePolygon(s.the_geom))
    FROM (SELECT au.alueurakkanro, ST_ExteriorRing((ST_Dump(au.alue)).geom) AS the_geom
            FROM alueurakka au
	   WHERE au.alueurakkanro = nro)  AS s
$$ LANGUAGE SQL IMMUTABLE;
