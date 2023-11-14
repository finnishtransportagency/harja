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
SELECT ST_Intersection(:saatugeometria::GEOMETRY, ulompi.u) AS leikkaus
FROM (SELECT ST_Union(sisempi.geom) u
      FROM (SELECT geometria::geometry geom
            FROM tieturvallisuusverkko
            WHERE tie = :tie
              AND ST_Intersects(:saatugeometria::GEOMETRY, geometria::geometry)
           ) AS sisempi
     ) AS ulompi;

-- name: hae-urakan-tieturvallisuusverkko-kartalle
SELECT
    ST_INTERSECTION(geometria, u.alue) AS sijainti
FROM tieturvallisuusverkko
         LEFT JOIN urakka u ON u.id = :urakka
WHERE
    ST_Intersects(geometria,
                  (SELECT alue FROM urakka WHERE id = :urakka));
