-- name: tallenna-paallysteen-korjausluokka!
-- vie entryn paallysteen-korjausluokka -tauluun
INSERT INTO paallysteen_korjausluokka (tie, aosa, aet, losa, let, korjausluokka, geometria,  paivitetty)
VALUES (:tie, :aosa, :aet, :losa, :let, :korjausluokka,  ST_GeomFromText(:geometria), current_timestamp);

-- name: tuhoa-paallysteen-korjausluokat!
-- poistaa kaikki paallysteen-korjausluokat tietokannasta, jotta uudet voidaan ajaa sisään
DELETE FROM paallysteen_korjausluokka;


-- name: hae-yllapitokohteen-geometriat
-- hakee yllapitokohteen geometriat
SELECT yosa.sijainti as geometria
FROM yllapitokohde yk
     JOIN yllapitokohdeosa yosa ON yosa.yllapitokohde = yk.id
WHERE yk.id = :id;

-- name: hae-paallysteen-korjausluokkageometriat
-- hakee paallysteen korjausluokkageometriat
SELECT st_buffer(ST_ReducePrecision(pk.geometria,25),25) as geometria
FROM paallysteen_korjausluokka pk,
organisaatio o
WHERE pk.korjausluokka = :korjausluokka
  AND o.elynumero = :elynumero
  AND st_intersects(o.alue, pk.geometria);;
