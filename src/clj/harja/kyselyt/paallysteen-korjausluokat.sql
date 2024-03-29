-- name: tallenna-paallysteen-korjausluokka!
-- vie entryn paallysteen-korjausluokka -tauluun
INSERT INTO paallysteen_korjausluokka (tie, aosa, aet, losa, let, korjausluokka, geometria,  paivitetty)
VALUES (:tie, :aosa, :aet, :losa, :let, :korjausluokka,  ST_GeomFromText(:geometria), current_timestamp);

-- name: tuhoa-paallysteen-korjausluokat!
-- poistaa kaikki paallysteen-korjausluokat tietokannasta, jotta uudet voidaan ajaa sisään
DELETE FROM paallysteen_korjausluokka;
