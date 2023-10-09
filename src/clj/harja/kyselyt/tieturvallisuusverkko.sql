-- name: tuhoa-tieturvallisuusverkko!
DELETE FROM pohjavesialue geometriapaivitys;

-- name: lisaa-tieturvallisuusverkko!
INSERT INTO tieturvallisuusverkko (tasoluokka, aosa, tie, let, losa, aet, tenluokka,
                                   geometria, ely, pituus, luonne, luotu)
VALUES (:tasoluokka, :aosa, :tie, :let, :losa, :aet, :tenluokka,
        ST_GeomFromText(:the_geom) :: GEOMETRY, :ely, :pituus, :luonne, NOW());

-- name: hae-tieturvallisuusgeometriat
SELECT tie, aosa, losa, aet, let, geometria FROM tieturvallisuusverkko;
