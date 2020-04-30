-- name: hae-aliurakoitsija-nimella
SELECT id,
       nimi,
       ytunnus
FROM aliurakoitsija
WHERE nimi = :nimi;

-- name: hae-aliurakoitsijat
SELECT id, nimi, ytunnus
FROM aliurakoitsija;

-- name: tallenna-aliurakoitsija<!
insert into aliurakoitsija (nimi, ytunnus, luotu, luoja)
values (:nimi, :ytunnus, current_timestamp, :kayttaja);

-- name: luo-aliurakoitsija<!
INSERT INTO aliurakoitsija (nimi, luotu, luoja)
VALUES (:nimi, current_timestamp, :kayttaja);

-- name: paivita-aliurakoitsija<!
update aliurakoitsija
set nimi = :nimi,
    ytunnus = :ytunnus,
    muokattu = current_timestamp,
    muokkaaja = :kayttaja
where id = :id;