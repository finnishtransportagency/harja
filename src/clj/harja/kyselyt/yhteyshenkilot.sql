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

-- name: poista-urakan-paivystykset!
-- Poistaa annetun urakan päivystykset ulkoisella id:lla
DELETE FROM paivystys
WHERE urakka = :urakka AND
      luoja = :kayttaja_id AND
      ulkoinen_id IN (:ulkoiset_idt);

-- name: hae-urakan-paivystajat
-- Hakee urakan päivystykset
SELECT
  p.id,
  p.vastuuhenkilo,
  p.varahenkilo,
  p.alku,
  p.loppu,
  y.id        AS yhteyshenkilo_id,
  y.etunimi,
  y.sukunimi,
  y.sahkoposti,
  y.tyopuhelin,
  y.matkapuhelin,

  urk.id      AS urakoitsija_id,
  urk.nimi    AS urakoitsija_nimi,
  urk.tyyppi  AS urakoitsija_tyyppi,
  urk.ytunnus AS urakoitsija_ytunnus,

  org.id      AS organisaatio_id,
  org.nimi    AS organisaatio_nimi,
  org.tyyppi  AS organisaatio_tyyppi,
  org.ytunnus AS organisaatio_ytunnus,

  u.id        AS urakka_id,
  u.nimi      AS urakka_nimi,
  u.alkupvm   AS urakka_alkupvm,
  u.loppupvm  AS urakka_loppupvm,
  u.tyyppi    AS urakka_tyyppi
FROM paivystys p
  LEFT JOIN yhteyshenkilo y ON p.yhteyshenkilo = y.id
  LEFT JOIN urakka u ON u.id = :urakka
  LEFT JOIN organisaatio org ON y.organisaatio = org.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE p.urakka = :urakka AND
      (:alkaen :: DATE IS NULL OR p.alku <= :paattyen :: TIMESTAMP) AND
      (:paattyen :: DATE IS NULL OR p.loppu >= :alkaen :: TIMESTAMP);

-- name: hae-kaikki-paivystajat
-- Hakee kaikki päivystykset
SELECT
  p.id,
  p.vastuuhenkilo,
  p.varahenkilo,
  p.alku,
  p.loppu,
  y.id        AS yhteyshenkilo_id,
  y.etunimi,
  y.sukunimi,
  y.sahkoposti,
  y.tyopuhelin,
  y.matkapuhelin,
  y.organisaatio,
  org.id      AS urakoitsija_id,
  org.nimi    AS urakoitsija_nimi,
  org.tyyppi  AS urakoitsija_tyyppi,
  org.ytunnus AS urakoitsija_ytunnus,
  u.id        AS urakka_id,
  u.nimi      AS urakka_nimi,
  u.alkupvm   AS urakka_alkupvm,
  u.loppupvm  AS urakka_loppupvm,
  u.tyyppi    AS urakka_tyyppi
FROM paivystys p
  LEFT JOIN yhteyshenkilo y ON p.yhteyshenkilo = y.id
  LEFT JOIN urakka u ON p.urakka = u.id
  LEFT JOIN organisaatio org ON u.urakoitsija = org.id
WHERE (:alkaen :: DATE IS NULL OR p.alku <= :paattyen :: TIMESTAMP) AND
      (:paattyen :: DATE IS NULL OR p.loppu >= :alkaen :: TIMESTAMP);

-- name: hae-urakan-kayttajat
-- Hakee urakkaan linkitetyt oikeat käyttäjät
SELECT
  k.id,
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
VALUES (:etunimi, :sukunimi, :tyopuhelin, :matkapuhelin, :sahkoposti, :organisaatio,
        :sampoid, :kayttajatunnus, :ulkoinen_id);

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
SET etunimi  = :etunimi, sukunimi = :sukunimi,
  tyopuhelin = :tyopuhelin, matkapuhelin = :matkapuhelin,
  sahkoposti = :sahkoposti, organisaatio = :organisaatio
WHERE id = :id;

-- name: paivita-yhteyshenkilo-ulkoisella-idlla<!
-- Päivittää yhteyshenkilön tiedot
UPDATE yhteyshenkilo
SET etunimi  = :etu, sukunimi = :suku, tyopuhelin = :tyopuh, matkapuhelin = :matkapuh,
  sahkoposti = :email
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
(alku, loppu, urakka, yhteyshenkilo, varahenkilo, vastuuhenkilo, ulkoinen_id, luoja)
VALUES (:alku, :loppu, :urakka, :yhteyshenkilo, :varahenkilo, :vastuuhenkilo, :ulkoinen_id, :kayttaja_id);

-- name: hae-paivystyksen-yhteyshenkilo-id
-- Hakee annetun urakan päivystyksen yhteyshenkilön id:n
SELECT yhteyshenkilo
FROM paivystys
WHERE id = :id AND urakka = :urakka;

-- name: paivita-paivystys!
-- Päivittää päivystyksen tiedot
UPDATE paivystys
SET alku = :alku, loppu = :loppu, vastuuhenkilo = :vastuuhenkilo, varahenkilo = :varahenkilo
WHERE id = :id AND urakka = :urakka;

-- name: paivita-paivystys-yhteyshenkilon-idlla<!
-- Päivittää päivystyksen tiedot
UPDATE paivystys
SET alku        = :alku,
  loppu         = :loppu,
  varahenkilo   = :varahenkilo,
  vastuuhenkilo = :vastuuhenkilo
WHERE yhteyshenkilo = :yhteyshenkilo_id;

-- name: paivita-paivystys-ulkoisella-idlla<!
-- Päivittää päivystyksen tiedot
UPDATE paivystys
SET alku        = :alku,
  loppu         = :loppu,
  varahenkilo   = :varahenkilo,
  vastuuhenkilo = :vastuuhenkilo,
  yhteyshenkilo = :yhteyshenkilo_id
WHERE ulkoinen_id = :ulkoinen_id AND
      luoja = :kayttaja_id;

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
WHERE yhteyshenkilo_sampoid = :yhteyshenkilo_sampoid;

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

-- name: onko-olemassa-paivystys-ulkoisella-idlla
SELECT exists(
    SELECT id
    FROM paivystys
    WHERE ulkoinen_id = :ulkoinen_id AND
          luoja = :luoja_id);

-- name: hae-urakan-taman-hetkiset-paivystajat
SELECT
  yh.id,
  yh.etunimi,
  yh.sukunimi,
  yh.matkapuhelin,
  yh.tyopuhelin,
  yh.sahkoposti,
  p.alku,
  p.loppu,
  p.vastuuhenkilo,
  p.varahenkilo
FROM paivystys p
  INNER JOIN yhteyshenkilo yh ON p.yhteyshenkilo = yh.id
WHERE
  urakka = :urakkaid AND
  ((now() BETWEEN p.alku AND p.loppu) OR
   (now() <= p.alku AND loppu IS NULL));

-- name: hae-paivystaja-puhelinnumerolla
SELECT
  y.id,
  y.etunimi,
  y.sukunimi,
  y.matkapuhelin,
  y.tyopuhelin,
  y.sahkoposti,
  o.ytunnus,
  o.nimi
FROM yhteyshenkilo y
  LEFT JOIN organisaatio o ON o.id = y.organisaatio
WHERE (matkapuhelin = :puhelinnumero OR tyopuhelin = :puhelinnumero) AND
      (SELECT exists(SELECT id
                     FROM paivystys p
                     WHERE p.yhteyshenkilo = y.id))
LIMIT 1;

-- name: hae-urakan-paivystaja-sahkopostilla
SELECT
  y.id,
  y.etunimi,
  y.sukunimi,
  y.matkapuhelin,
  y.tyopuhelin,
  y.sahkoposti,
  o.ytunnus,
  o.nimi
FROM yhteyshenkilo y
  JOIN paivystys p ON p.yhteyshenkilo = y.id
  LEFT JOIN organisaatio o ON o.id = y.organisaatio
WHERE p.urakka = :urakka AND
      -- Tarvitaan jotta voidaan hakea esim. muodossa Erkki Esimerkki <erkki.esimerkki@example.com>
      :sahkoposti ILIKE (SELECT '%' || y.sahkoposti || '%');

-- name: hae-yhteyshenkilo
SELECT
  y.id,
  y.etunimi,
  y.sukunimi,
  y.matkapuhelin,
  y.tyopuhelin,
  y.sahkoposti,
  o.ytunnus,
  o.nimi
FROM yhteyshenkilo y
  LEFT JOIN organisaatio o ON o.id = y.organisaatio
WHERE
  y.id = :id;

-- name: hae-kaynissa-olevien-urakoiden-paivystykset
SELECT
  p.id,
  alku as "paivystys-alku",
  loppu as "paivystys-loppu",
  u.id as "urakka-id",
  u.nimi as "urakka-nimi",
  u.sampoid as "sampo-id"
FROM paivystys p
  JOIN urakka u ON p.urakka = u.id
WHERE u.loppupvm >= :pvm;

-- name: hae-urakat-paivystystarkistukseen
-- Hakee urakat, jotka ovat voimassa annettuna päivänä
SELECT
  id,
  nimi,
  sampoid
FROM urakka u
WHERE u.alkupvm <= :pvm
      AND u.loppupvm >= :pvm
      -- PENDING Lisätään urakkatyyppejä sitä mukaan kun
      -- päätyvät tuotantoon
      AND (:tyyppi::urakkatyyppi IS NULL OR tyyppi = :tyyppi::urakkatyyppi);

-- name: hae-urakan-vastuuhenkilot
SELECT * FROM urakanvastuuhenkilo WHERE urakka = :urakka;

-- name: poista-urakan-vastuuhenkilot-roolille!
DELETE FROM urakanvastuuhenkilo
 WHERE urakka = :urakka AND
       rooli = :rooli;

-- name: luo-urakan-vastuuhenkilo<!
INSERT INTO urakanvastuuhenkilo
       (urakka, rooli, etunimi, sukunimi, puhelin, sahkoposti, kayttajatunnus, ensisijainen)
VALUES (:urakka, :rooli, :etunimi, :sukunimi, :puhelin, :sahkoposti, :kayttajatunnus, :ensisijainen);

-- name: hae-urakan-vastuuhenkilot
SELECT
  kayttajatunnus,
  etunimi,
  sukunimi,
  sahkoposti,
  puhelin,
  rooli,
  ensisijainen
FROM urakanvastuuhenkilo
WHERE urakka = :id;

-- name: onko-urakalla-paivystajia?
-- single?: true
SELECT exists(SELECT p.id
              FROM paivystys p
                INNER JOIN yhteyshenkilo yh ON p.yhteyshenkilo = yh.id
              WHERE
                urakka = :urakkaid AND
                ((now() BETWEEN p.alku AND p.loppu) OR
                 (now() <= p.alku AND loppu IS NULL)));
