-- name: hae-jarjestelmatunnukset
SELECT k.id, k.kayttajanimi, k.kuvaus, k.luotu,
       o.nimi as organisaatio_nimi,
       o.id as organisaatio_id,
      (SELECT array_agg(u.nimi)
         FROM urakka u
        WHERE u.urakoitsija = o.id AND
	      u.alkupvm <= current_date AND
	      u.loppupvm >= current_date) as urakat
  FROM kayttaja k
       JOIN organisaatio o ON k.organisaatio = o.id
 WHERE jarjestelma = true AND
       k.poistettu = false
ORDER BY organisaatio_nimi, k.kayttajanimi;

-- name: hae-jarjestelmatunnuksen-lisaoikeudet
SELECT
  klu.id,
  u.id as "urakka-id",
  u.nimi AS "urakka-nimi",
  kayttaja
FROM kayttajan_lisaoikeudet_urakkaan klu
  JOIN urakka u ON klu.urakka = u.id
WHERE kayttaja = :kayttaja
ORDER BY kayttaja;

-- name: hae-urakat-lisaoikeusvalintaan
SELECT
  u.id,
  u.nimi
FROM urakka u ORDER BY u.nimi;

-- name: poista-jarjestelmatunnus!
UPDATE kayttaja
   SET poistettu = true
 WHERE id = :id AND
       jarjestelma = true;

-- name: paivita-jarjestelmatunnus!
UPDATE kayttaja
SET kayttajanimi = :kayttajanimi,
  organisaatio = :organisaatio,
  kuvaus = :kuvaus
WHERE id = :id;

-- name: paivita-jarjestelmatunnuksen-lisaoikeus-urakkaan!
UPDATE kayttajan_lisaoikeudet_urakkaan SET
  urakka = :urakka
WHERE id = :id;

-- name: poista-jarjestelmatunnuksen-lisaoikeus-urakkaan!
DELETE FROM kayttajan_lisaoikeudet_urakkaan WHERE
id = :id;

-- name: luo-jarjestelmatunnukselle-lisaoikeus-urakkaan<!
INSERT INTO kayttajan_lisaoikeudet_urakkaan (urakka, kayttaja)
    VALUES (:urakka, :kayttaja);

-- name: luo-jarjestelmatunnus<!
INSERT
   INTO kayttaja (kayttajanimi, kuvaus, organisaatio, luotu, jarjestelma, poistettu)
   VALUES (:kayttajanimi, :kuvaus, :organisaatio, NOW(), true, false)
