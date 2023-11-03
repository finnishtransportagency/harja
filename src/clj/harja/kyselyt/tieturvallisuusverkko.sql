-- name: tuhoa-tieturvallisuusverkko!
DELETE FROM tieturvallisuusverkko;

-- name: lisaa-tieturvallisuusverkko!
INSERT INTO tieturvallisuusverkko (tasoluokka, aosa, tie, let, losa, aet, tenluokka,
                                   geometria, ely, pituus, luonne, luotu)
VALUES (:tasoluokka, :aosa, :tie, :let, :losa, :aet, :tenluokka,
        ST_GeomFromText(:the_geom) :: GEOMETRY, :ely, :pituus, :luonne, NOW());

-- name: hae-tieturvallisuusgeometriat
SELECT tie, aosa, losa, aet, let, geometria FROM tieturvallisuusverkko;

-- name: hae-geometriaa-leikkaavat-tieturvallisuusgeometriat-tienumerolla
SELECT ST_Intersection(:saatugeometria::GEOMETRY, ulompi.u) AS leikkaus, ulompi.ids AS idt
FROM (SELECT ST_CollectionExtract(ST_Collect(sisempi.geom::geometry)) u, string_agg(sisempi.id::TEXT, ' ') ids
      FROM (SELECT geometria::geometry geom, id
            FROM tieturvallisuusverkko
            WHERE tie = :tie
              AND ST_Intersects(:saatugeometria::GEOMETRY, geometria::geometry)
           ) AS sisempi
     ) AS ulompi;
