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


-- name: hae-kayttajat
-- Hakee käyttäjiä käyttäjähallinnan listausta varten.
-- Haun suorittava käyttäjä annetaan parametrina ja vain käyttäjät, jotka hän saa nähdä palautetaan.
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
  array_cat(
      (SELECT
  array_agg(rooli)
       FROM
         kayttaja_rooli
       WHERE
         kayttaja
         = k.id
         AND
         poistettu
         = FALSE),
      (SELECT
  array_agg(rooli)
       FROM
         kayttaja_urakka_rooli
       WHERE
         kayttaja
         = k.id
         AND
         poistettu
         =
         FALSE)) AS roolit
FROM kayttaja k
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.poistettu = FALSE
      AND
      -- tarkistetaan käyttöoikeus: pääkäyttäjä näkee kaikki, muuten oman organisaation
      ((SELECT COUNT(*)
        FROM kayttaja_rooli
        WHERE kayttaja = :hakija AND rooli = 'jarjestelmavastuuhenkilo' AND poistettu = FALSE) > 0
       OR
       k.organisaatio IN (SELECT kor.organisaatio
                          FROM kayttaja_organisaatio_rooli kor
                          WHERE kor.kayttaja = :hakija
                                AND kor.rooli = 'urakoitsijan paakayttaja'
       ))
      -- tarkistetaan hakuehto
      AND (:haku = '' OR (k.kayttajanimi LIKE :haku OR k.etunimi LIKE :haku OR k.sukunimi LIKE :haku))
OFFSET :alku
LIMIT :maara

-- name: hae-kayttajat-lkm
-- Hakee lukumäärän käyttäjälukumäärälle, jonka hae-kayttajat palauttaisi ilman LIMIT/OFFSET määritystä.
SELECT COUNT(k.id) AS lkm
FROM kayttaja k
WHERE k.poistettu = FALSE
      AND
      -- tarkistetaan käyttöoikeus: pääkäyttäjä näkee kaikki, muuten oman organisaation
      ((SELECT COUNT(*)
        FROM kayttaja_rooli
        WHERE kayttaja = :hakija AND rooli = 'jarjestelmavastuuhenkilo' AND poistettu = FALSE) > 0
       OR
       k.organisaatio IN (SELECT kor.organisaatio
                          FROM kayttaja_organisaatio_rooli kor
                          WHERE kor.kayttaja = :hakija
                                AND kor.rooli = 'urakoitsijan paakayttaja'
       ))
      -- tarkistetaan hakuehto
      AND (:haku = '' OR (k.kayttajanimi LIKE :haku OR k.etunimi LIKE :haku OR k.sukunimi LIKE :haku))

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
  array_cat(
      (SELECT
  array_agg(rooli)
       FROM
         kayttaja_rooli
       WHERE
         kayttaja
         = k.id
         AND
         poistettu
         = FALSE),
      (SELECT
  array_agg(rooli)
       FROM
         kayttaja_urakka_rooli
       WHERE
         kayttaja
         = k.id
         AND
         poistettu
         =
         FALSE)) AS roolit,
  k.jarjestelma
FROM kayttaja k
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.poistettu = FALSE
      AND k.id = :id


-- name: hae-kayttajan-urakka-roolit
-- Hakee käyttäjän urakka roolit.
SELECT
  rooli,
  urakka   AS urakka_id,
  luotu,
  ur.nimi  AS urakka_nimi,
  urk.nimi AS urakka_urakoitsija_nimi,
  urk.id   AS urakka_urakoitsija_id,
  hal.nimi AS urakka_hallintayksikko_nimi,
  hal.id   AS urakka_hallintayksikko_id
FROM kayttaja_urakka_rooli
  LEFT JOIN urakka ur ON urakka = ur.id
  LEFT JOIN organisaatio urk ON ur.urakoitsija = urk.id
  LEFT JOIN organisaatio hal ON ur.hallintayksikko = hal.id
WHERE kayttaja = :kayttaja AND poistettu = FALSE

-- name: hae-kayttajan-urakat-aikavalilta
SELECT
  urakka AS urakka_id,
  u.nimi AS urakka_nimi,
  u.tyyppi AS urakka_tyyppi,
  o.id AS hallintayksikko_id,
  o.nimi AS hallintayksikko_nimi
FROM kayttaja_urakka_rooli
  LEFT JOIN urakka u ON urakka = u.id
  JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE kayttaja = :kayttaja AND
      poistettu IS NOT TRUE AND
      ((u.loppupvm >= :alku AND u.alkupvm <= :loppu) OR (u.loppupvm IS NULL AND u.alkupvm <= :loppu)) AND
      (:urakoitsija :: INTEGER IS NULL OR :urakoitsija = u.urakoitsija) AND
      (:urakkatyyppi :: urakkatyyppi IS NULL OR u.tyyppi :: TEXT = :urakkatyyppi) AND
      (:hallintayksikko :: INTEGER IS NULL OR u.hallintayksikko IN (:hallintayksikko));


-- name: lisaa-urakka-rooli<!
-- Lisää annetulle käyttäjälle roolin urakkaan.
INSERT INTO kayttaja_urakka_rooli (luoja, luotu, kayttaja, urakka, rooli)
VALUES (:luoja, NOW(), :kayttaja, :urakka, :rooli :: kayttajarooli)

-- name: poista-urakka-rooli!
-- Poista käyttäjän rooli annetusta urakkasta.
UPDATE kayttaja_urakka_rooli
SET poistettu = TRUE, muokkaaja = :muokkaaja, muokattu = NOW()
WHERE kayttaja = :kayttaja AND urakka = :urakka AND rooli = :rooli :: kayttajarooli

-- name: hae-kayttajan-roolit
-- Palauttaa kaikki käyttäjän roolit (sekä urakka että tavalliset).
SELECT array_cat((SELECT array_agg(rooli)
                  FROM kayttaja_rooli
                  WHERE kayttaja = :kayttaja AND poistettu = FALSE),
                 (SELECT
                    array_agg(rooli)
                  FROM kayttaja_urakka_rooli
                  WHERE kayttaja = :kayttaja AND poistettu = FALSE)) AS roolit


-- name: poista-rooli!
-- Poista käyttäjältä rooli.
UPDATE kayttaja_rooli
SET poistettu = TRUE, muokkaaja = :muokkaaja, muokattu = NOW()
WHERE kayttaja = :kayttaja AND rooli = :rooli :: kayttajarooli

-- name: poista-urakka-roolit!
-- Poista käyttäjältä urakka roolit.
UPDATE kayttaja_urakka_rooli
SET poistettu = TRUE, muokkaaja = :muokkaaja, muokattu = NOW()
WHERE kayttaja = :kayttaja AND rooli = :rooli :: kayttajarooli

-- name: lisaa-rooli<!
-- Lisää käyttäjälle rooli.
INSERT INTO kayttaja_rooli (luoja, luotu, kayttaja, rooli) VALUES (:luoja, NOW(), :kayttaja, :rooli :: kayttajarooli)


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

-- name: hae-organisaatio-y-tunnuksella
-- Hakee organisaation id:n, nimen ja tyypin Y-tunnuksen perusteella.
SELECT id,nimi,tyyppi FROM organisaatio WHERE ytunnus = :y-tunnus

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
  array_cat(
      (SELECT
  array_agg(rooli)
       FROM
         kayttaja_rooli
       WHERE
         kayttaja
         = k.id
         AND
         poistettu
         = FALSE),
      (SELECT
  array_agg(rooli)
       FROM
         kayttaja_urakka_rooli
       WHERE
         kayttaja
         = k.id
         AND
         poistettu
         =
         FALSE)) AS roolit,
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

-- name: onko-kayttajalla-lisaoikeus-urakkaan
-- Tarkistaa onko käyttäjälle annettu lisäoikeudet urakkaan
SELECT exists(
    SELECT klu.id
    FROM kayttajan_lisaoikeudet_urakkaan klu
      JOIN kayttaja k ON klu.kayttaja = k.id
    WHERE urakka = :urakka
          AND klu.kayttaja = :kayttaja
          AND k.jarjestelma IS TRUE
          AND k.poistettu IS NOT TRUE);

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

-- name: liikenneviraston-jarjestelma?
-- single?: true
SELECT exists(
    SELECT k.id
    FROM kayttaja k
      JOIN organisaatio o ON k.organisaatio = o.id
    WHERE k.kayttajanimi = :kayttajanimi AND
          k.jarjestelma IS TRUE AND
          o.tyyppi = 'liikennevirasto');

-- name: hae-yhteydenpidon-vastaanottajat
SELECT
  etunimi,
  sukunimi,
  sahkoposti
FROM kayttaja;