-- name: luo-sopimus<!
-- Luo uuden sopimukset.
INSERT INTO sopimus (sampoid, nimi, alkupvm, loppupvm, urakka_sampoid, urakoitsija_sampoid, paasopimus)
VALUES (:sampoid, :nimi, :alkupvm, :loppupvm, :urakka_sampoid, :urakoitsija_sampoid,
        (SELECT id
         FROM sopimus
         WHERE urakka_sampoid = :urakka_sampoid AND
               paasopimus IS NULL));

-- name: paivita-sopimus!
-- Paivittaa sopimukset.
UPDATE sopimus
SET nimi              = :nimi,
  alkupvm             = :alkupvm,
  loppupvm            = :loppupvm,
  urakka_sampoid      = :urakka_sampoid,
  urakoitsija_sampoid = :urakoitsija_sampoid,
  paasopimus = (SELECT id
       FROM sopimus
       WHERE urakka_sampoid = :urakka_sampoid
             AND id != :id
             AND paasopimus IS NULL)
WHERE id = :id;

-- name: hae-id-sampoidlla
-- Hakee sopimuksen id:n sampo id:llä
SELECT id
FROM sopimus
WHERE sampoid = :sampoid;

-- name: paivita-urakka-sampoidlla!
-- Päivittää sopimukselle urakan id:n urakan sampo id:llä
UPDATE sopimus
SET urakka = (SELECT id
              FROM urakka
              WHERE sampoid = :urakka_sampo_id)
WHERE urakka_sampoid = :urakka_sampo_id;

-- name: onko-olemassa
-- Tarkistaa onko id:n mukaista urakkaa olemassa tietokannassa
SELECT EXISTS(SELECT id
              FROM sopimus
              WHERE urakka = :urakka_id AND id = :sopimus_id);

-- name: hae-urakan-paasopimus
SELECT id
FROM sopimus
WHERE urakka = :urakka AND paasopimus IS NULL;

-- name: hae-harjassa-luodut-sopimukset
SELECT
  s.id,
  s.nimi,
  s.alkupvm,
  s.loppupvm,
  s.paasopimus as "paasopimus-id",
  u.nimi AS urakka_nimi,
  u.id AS urakka_id
FROM sopimus s
  LEFT JOIN urakka u ON s.urakka = u.id
WHERE s.harjassa_luotu IS TRUE
ORDER BY s.alkupvm DESC, s.nimi;

-- name: hae-harjassa-luodut-sopimukset-voimassa
SELECT
  s.id,
  s.nimi,
  s.alkupvm,
  s.loppupvm,
  s.paasopimus as "paasopimus-id",
  u.nimi AS urakka_nimi,
  u.id AS urakka_id
FROM sopimus s
  LEFT JOIN urakka u ON s.urakka = u.id
WHERE s.harjassa_luotu IS TRUE AND s.loppupvm > NOW() 
ORDER BY s.alkupvm DESC, s.nimi;

-- name: liita-sopimukset-urakkaan!
UPDATE sopimus s SET urakka=:urakka
WHERE id IN (:sopimukset)
AND s.harjassa_luotu IS TRUE;

-- name: poista-kaikki-sopimukset-urakasta!
UPDATE sopimus s SET urakka=NULL, paasopimus=NULL
WHERE urakka=:urakka
AND s.harjassa_luotu IS TRUE;

-- name: aseta-sopimus-paasopimukseksi!
UPDATE sopimus s SET paasopimus=NULL
WHERE id = :sopimus
AND s.harjassa_luotu IS TRUE;

-- name: aseta-sopimuksien-paasopimus!
UPDATE sopimus s SET paasopimus=:paasopimus
WHERE id IN (:sopimukset)
AND s.harjassa_luotu IS TRUE;

-- name: luo-harjassa-luotu-sopimus<!
-- Luo uuden sopimukset.
INSERT INTO sopimus (nimi, alkupvm, loppupvm, paasopimus, luoja, luotu, harjassa_luotu)
VALUES (:nimi, :alkupvm, :loppupvm, :paasopimus, :kayttaja, now(), TRUE);

-- name: paivita-harjassa-luotu-sopimus<!
-- Paivittaa sopimukset.
UPDATE sopimus s
SET nimi              = :nimi,
  alkupvm             = :alkupvm,
  loppupvm            = :loppupvm,
  paasopimus = :paasopimus,
  muokkaaja = :kayttaja,
  muokattu = NOW()
WHERE id = :id
AND s.harjassa_luotu IS TRUE;

-- name: hae-sopimusten-reimari-diaarinumerot
-- Hakee diaarinumerot
SELECT "harja-sopimus-id", "reimari-diaarinro"
FROM reimari_sopimuslinkki;

-- name: hae-sopimuksen-reimari-diaarinumero
-- Hakee diaarinumeron
SELECT "reimari-diaarinro"
FROM reimari_sopimuslinkki
WHERE "harja-sopimus-id" = :harja-sopimus-id

-- name: luo-reimari-diaarinumero-linkki<!
-- Luo uuden diaarinumeron sopimukselle
INSERT INTO reimari_sopimuslinkki ("harja-sopimus-id", "reimari-diaarinro")
VALUES (:harja-sopimus-id, :reimari-diaarinro);

-- name: paivita-reimari-diaarinumero-linkki<!
-- Paivittaa diaarinumeron sopimukselle
UPDATE reimari_sopimuslinkki
SET "reimari-diaarinro" = :reimari-diaarinro
WHERE "harja-sopimus-id" = :harja-sopimus-id;

-- name: hae-urakan-sopimus-idt
SELECT id
  FROM sopimus
 WHERE urakka = :urakka_id;
