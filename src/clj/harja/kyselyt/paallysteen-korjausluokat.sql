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
SELECT st_buffer(geometria,50) as geometria
FROM paallysteen_korjausluokka
WHERE tie = 79 OR tie = 21;
