-- name: hae-kirjautumistiedot
-- Hakee annetulle KOKA käyttäjätunnukselle kirjautumistiedot
SELECT
  k.id,
  k.kayttajanimi,
  k.etunimi,
  k.sukunimi,
  k.sahkoposti,
  k.puhelin,
  o.id     AS organisaatio_id,
  o.nimi   AS organisaatio_nimi,
  o.tyyppi AS organisaatio_tyyppi,
  (SELECT array_agg(u.id)
     FROM urakka u
    WHERE u.urakoitsija = o.id OR u.hallintayksikko = o.id) as "organisaation-urakat"
FROM kayttaja k
     LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.kayttajanimi = :koka
      AND k.poistettu = FALSE

-- name: varmista-kayttaja
-- single?: true
-- Varmistaa että KOKA käyttäjä on tietokannassa
INSERT
  INTO kayttaja (kayttajanimi, etunimi, sukunimi, sahkoposti, puhelin, organisaatio, luotu)
  VALUES (:kayttajanimi, :etunimi, :sukunimi, :sahkoposti, :puhelin, :organisaatio, NOW())
ON CONFLICT ON CONSTRAINT uniikki_kayttajanimi DO
  UPDATE SET etunimi = :etunimi, sukunimi = :sukunimi,
             sahkoposti = :sahkoposti, puhelin = :puhelin,
             organisaatio = :organisaatio, muokattu = NOW()
RETURNING id

-- name: hae-ely-numerolla
-- Hakee ELY-keskuksen organisaation ELY numeron perusteella
SELECT id,nimi,tyyppi FROM organisaatio
 WHERE tyyppi = 'hallintayksikko' AND elynumero = :elynumero

-- name: hae-organisaation-urakat
-- Palauttaa organisaation (hallintayksikkö tai urakoitsija) omien urakoiden id:t
SELECT u.id
  FROM urakka u
 WHERE u.urakoitsija = :org OR u.hallintayksikko = :org

-- name: poista-kayttaja!
-- Merkitsee annetun käyttäjän poistetuksi
UPDATE kayttaja
SET poistettu = TRUE, muokkaaja = :muokkaaja, muokattu = NOW()
WHERE id = :kayttaja

-- name: hae-organisaatio-nimella
-- Hakee nimetyn organisaation. Tämä kysely on FIM käyttäjän tietojen yhdistämistä varten.
-- Ei tee käyttäjätarkistusta.
SELECT
  o.id     AS id,
  o.nimi   AS nimi,
  o.tyyppi AS tyyppi
FROM organisaatio o
WHERE lower(o.nimi) = lower(:nimi)

-- name: hae-organisaatio-idlla
-- Hakee organisaation id:n, nimen ja tyypin id:n perusteella.
SELECT id,nimi,tyyppi FROM organisaatio WHERE id = :id

-- name: hae-organisaatioita
-- Käyttäjän organisaatiohaku nimen osalla.
SELECT
  o.id     AS id,
  o.nimi   AS nimi,
  o.tyyppi AS tyyppi
FROM organisaatio o
WHERE o.nimi ILIKE :haku


-- name: luo-kayttaja<!
-- Luo uuden käyttäjän FIM tietojen pohjalta
INSERT
INTO kayttaja
(kayttajanimi, etunimi, sukunimi, sahkoposti, puhelin, organisaatio)
VALUES (:kayttajanimi, :etunimi, :sukunimi, :sahkoposti, :puhelin, :organisaatio)

-- name: hae-kayttajien-tunnistetiedot
-- Hakee käyttäjistä ydintiedot tekstihaulla.
SELECT
  k.id,
  k.kayttajanimi,
  k.etunimi,
  k.sukunimi,
  k.jarjestelma AS jarjestelmasta,
  o.id     AS org_id,
  o.nimi   AS org_nimi,
  o.tyyppi AS org_tyyppi
FROM kayttaja k LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE (k.kayttajanimi ILIKE :hakutermi
       OR k.etunimi ILIKE  :hakutermi
       OR k.sukunimi ILIKE  :hakutermi
      OR (CONCAT(k.etunimi, ' ' , k.sukunimi) ILIKE :hakutermi))
      AND k.poistettu = FALSE
LIMIT 11;

-- name: hae-kayttaja
-- Hakee yhden käyttäjän id:llä
SELECT
  k.id,
  k.kayttajanimi,
  k.etunimi,
  k.sukunimi,
  k.sahkoposti,
  k.puhelin,
  o.id           AS org_id,
  o.nimi         AS org_nimi,
  o.tyyppi       AS org_tyyppi,
  k.jarjestelma
FROM kayttaja k
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.poistettu = FALSE
      AND k.id = :id

-- name: hae-kayttaja-kayttajanimella
-- Hakee käyttäjän käyttäjänimellä
SELECT
  k.id,
  k.kayttajanimi,
  k.etunimi,
  k.sukunimi,
  k.sahkoposti,
  k.puhelin,
  o.id           AS org_id,
  o.nimi         AS org_nimi,
  o.tyyppi       AS org_tyyppi,
  k.jarjestelma
FROM kayttaja k
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.poistettu = FALSE
      AND k.kayttajanimi = :kaytajanimi;

-- name: onko-kayttaja-urakan-organisaatiossa
-- Tarkistaa onko käyttäjä urakan urakoitsijaorganisaation jäsen
SELECT exists(
    SELECT u.id
    FROM urakka u
      JOIN kayttaja k ON k.organisaatio = u.urakoitsija
    WHERE u.id = :urakka_id AND
          k.id = :kayttaja_id);

-- name: onko-kayttaja-organisaatiossa
-- Tarkistaa onko käyttäjä organisaatiossa
SELECT exists(
    SELECT o.id
    FROM organisaatio o
      JOIN kayttaja k ON k.organisaatio = o.id
      AND o.ytunnus = :ytunnus
      AND k.id = :kayttaja_id);

-- name: onko-kayttaja-nimella-urakan-organisaatiossa
(SELECT EXISTS(SELECT id
               FROM kayttaja
               WHERE organisaatio = (SELECT urakoitsija
                                     FROM urakka
                                     WHERE id = :urakka)
                     AND etunimi = :etunimi
                     AND sukunimi = :sukunimi));


-- name: hae-urakan-id-sampo-idlla
-- single?: true
-- Hae urakan id Sampo ID:llä, sähke oikeuksien hakua varten
SELECT id FROM urakka WHERE sampoid = :sampoid

-- name: hae-urakoitsijan-id-ytunnuksella
-- single?: true
SELECT id FROM organisaatio WHERE tyyppi='urakoitsija' AND ytunnus=:ytunnus

-- name: hae-kayttajan-yleisin-urakkatyyppi
-- single?: true
SELECT tyyppi FROM urakka WHERE id IN (:idt) GROUP BY tyyppi ORDER BY count(id) DESC LIMIT 1;

-- name: onko-jarjestelma?
-- single?: true
SELECT jarjestelma
FROM kayttaja
WHERE kayttajanimi = :kayttajanimi;
