-- name: hae-urakan-yhteyshenkilot
-- Hakee annetun urakan kaikki yhteyshenkilöt, sekä urakoitsijan että tilaajan puolelta
SELECT
  y.id,
  y.etunimi,
  y.sukunimi,
  y.kayttajatunnus,
  y.tyopuhelin,
  y.matkapuhelin,
  y.sahkoposti,
  yu.rooli,
  yu.id       AS yu,
  org.id      AS organisaatio_id,
  org.nimi    AS organisaatio_nimi,
  org.tyyppi  AS organisaatio_tyyppi,
  org.lyhenne AS organisaatio_lyhenne
FROM yhteyshenkilo y
  LEFT JOIN yhteyshenkilo_urakka yu ON yu.yhteyshenkilo = y.id
  LEFT JOIN organisaatio org ON y.organisaatio = org.id
WHERE yu.urakka = :urakka;

-- name: hae-urakan-paivystajat
-- Hakee urakan päivystykset
SELECT
  p.id,
  p.vastuuhenkilo,
  p.varahenkilo,
  p.alku,
  p.loppu,
  y.etunimi,
  y.sukunimi,
  y.sahkoposti,
  y.tyopuhelin,
  y.matkapuhelin,
  y.organisaatio,
  org.id     AS organisaatio_id,
  org.nimi   AS organisaatio_nimi,
  org.tyyppi AS organisaatio_tyyppi
FROM paivystys p
  LEFT JOIN yhteyshenkilo y ON p.yhteyshenkilo = y.id
  LEFT JOIN organisaatio org ON y.organisaatio = org.id
WHERE p.urakka = :urakka;

-- name: hae-urakan-kayttajat
-- Hakee urakkaan linkitetyt oikeat käyttäjät
SELECT
  kur.rooli,
  k.etunimi,
  k.sukunimi,
  k.puhelin,
  k.sahkoposti,
  o.nimi AS organisaatio_nimi
FROM kayttaja_urakka_rooli kur
  JOIN kayttaja k ON kur.kayttaja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
WHERE kur.urakka = :urakka
      AND kur.poistettu = FALSE AND k.poistettu = FALSE;


-- name: hae-yhteyshenkilotyypit
-- Hakee käytetyt yhteyshenkilötyypit
SELECT DISTINCT (rooli)
FROM yhteyshenkilo_urakka;

-- name: luo-yhteyshenkilo<!
-- Tekee uuden yhteyshenkilön
INSERT INTO yhteyshenkilo (etunimi, sukunimi, tyopuhelin, matkapuhelin, sahkoposti, organisaatio, sampoid, kayttajatunnus, ulkoinen_id)
VALUES (:etu, :suku, :tyopuh, :matkapuh, :email, :org, :sampoid, :kayttajatunnus, :ulkoinen_id);

-- name: aseta-yhteyshenkilon-rooli!
UPDATE yhteyshenkilo_urakka
SET rooli = :rooli
WHERE yhteyshenkilo = :id AND urakka = :urakka;

-- name: liita-yhteyshenkilo-urakkaan<!
-- Liittää yhteyshenkilön urakkaan
INSERT INTO yhteyshenkilo_urakka (rooli, yhteyshenkilo, urakka) VALUES (:rooli, :yht, :urakka);

-- name: paivita-yhteyshenkilo<!
-- Päivittää yhteyshenkilön tiedot
UPDATE yhteyshenkilo
SET etunimi  = :etu, sukunimi = :suku, tyopuhelin = :tyopuh, matkapuhelin = :matkapuh,
  sahkoposti = :email, organisaatio = :org
WHERE id = :id;

-- name: paivita-yhteyshenkilo-ulkoisella-idlla<!
-- Päivittää yhteyshenkilön tiedot
UPDATE yhteyshenkilo
SET etunimi  = :etu, sukunimi = :suku, tyopuhelin = :tyopuh, matkapuhelin = :matkapuh,
  sahkoposti = :email, organisaatio = :org
WHERE ulkoinen_id = :id;

-- name: hae-urakan-yhteyshenkilo-idt
-- Hakee yhteyshenkilöiden id, jotka ovat liitetty annettuun urakkaan
SELECT yhteyshenkilo
FROM yhteyshenkilo_urakka
WHERE urakka = :urakka;

-- name: poista-yhteyshenkilo!
-- Poistaa yhteyshenkilön, joka on annetussa urakassa.
DELETE FROM yhteyshenkilo
WHERE id = :id AND id IN (SELECT yhteyshenkilo
                          FROM yhteyshenkilo_urakka
                          WHERE urakka = :urakka);


-- name: poista-paivystaja!
-- Poista päivystäjän annetusta urakasta.,
DELETE FROM yhteyshenkilo
WHERE id = (SELECT yhteyshenkilo
            FROM paivystys
            WHERE id = :id AND urakka = :urakka);

-- name: luo-paivystys<!
-- Luo annetulle yhteyshenkilölle päivystyksen urakkaan
INSERT INTO paivystys
(vastuuhenkilo, varahenkilo, alku, loppu, urakka, yhteyshenkilo)
VALUES (TRUE, FALSE, :alku, :loppu, :urakka, :yhteyshenkilo);

-- name: hae-paivystyksen-yhteyshenkilo-id
-- Hakee annetun urakan päivystyksen yhteyshenkilön id:n
SELECT yhteyshenkilo
FROM paivystys
WHERE id = :id AND urakka = :urakka;

-- name: paivita-paivystys!
-- Päivittää päivystyksen tiedot
UPDATE paivystys
SET alku = :alku, loppu = :loppu
WHERE id = :id AND urakka = :urakka;

-- name: paivita-paivystys-yhteyshenkilon-idlla<!
-- Päivittää päivystyksen tiedot
UPDATE paivystys
SET alku = :alku, loppu = :loppu
WHERE yhteyshenkilo = :yhteyshenkilo_id

-- name: liita-sampon-yhteyshenkilo-urakkaan<!
-- Liittää yhteyshenkilön urakkaan Sampo id:llä
INSERT INTO yhteyshenkilo_urakka (rooli, yhteyshenkilo_sampoid, urakka, yhteyshenkilo)
VALUES ('Sampo yhteyshenkilö', :yhteyshenkilo_sampoid, :urakka,
        (SELECT id
         FROM yhteyshenkilo
         WHERE sampoid = :yhteyshenkilo_sampoid));

-- name: irrota-sampon-yhteyshenkilot-urakalta!
-- Irrottaa Samposta tuodut yhteyshenkilöt urakalta
DELETE FROM yhteyshenkilo_urakka
WHERE rooli = 'Sampo yhteyshenkilö' AND
      urakka = :urakka_id;

-- name: hae-id-sampoidlla
-- Hakee yhteyshenkilön id:n sampo id:llä
SELECT id
FROM yhteyshenkilo
WHERE sampoid = :sampoid;

-- name: paivita-yhteyshenkilot-urakalle-sampoidlla!
-- Päivittää yhteyshenkilöt urakalle yhteyshenkilön Sampo id:llä
UPDATE yhteyshenkilo_urakka
SET yhteyshenkilo = (
  SELECT id
  FROM yhteyshenkilo
  WHERE sampoid = :yhteyshenkilo_sampoid)
WHERE yhteyshenkilo_sampoid = :yhteyshenkilo_sampoid

-- name: onko-olemassa-yhteyshenkilo-ulkoisella-idlla
SELECT exists(
    SELECT id
    FROM yhteyshenkilo
    WHERE ulkoinen_id = :ulkoinen_id);

-- name: onko-olemassa-paivystys-jossa-yhteyshenkilona-id
-- Etsii päivystyksen, jossa yhteyshenkilönä on annettu id.
SELECT exists(
    SELECT id
    FROM paivystys
    WHERE yhteyshenkilo = :yhteyshenkilo);