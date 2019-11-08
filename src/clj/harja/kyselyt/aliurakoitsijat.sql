-- name: hae-aliurakoitsija-nimella
SELECT id,
       nimi,
       ytunnus
FROM aliurakoitsija
WHERE nimi = :nimi;

-- name: hae-aliurakoitsijat
SELECT id, nimi, ytunnus
FROM aliurakoitsija;

-- name: luo-aliurakoitsija<!
INSERT INTO aliurakoitsija (nimi, luotu, luoja)
VALUES (:nimi, current_timestamp, :kayttaja);

