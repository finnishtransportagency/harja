-- name: hae-urakan-sillat
-- Hakee hoidon alueurakalle sillat sekä niiden viimeiset tarkastuspvm:t.
SELECT
  s.id,
  s.siltanimi,
  s.siltatunnus,
  s.alue,
  s1.tarkastusaika,
  s1.tarkastaja,
  s.tr_alkuosa,
  s.tr_alkuetaisyys,
  s.tr_loppuosa,
  s.tr_loppuetaisyys,
  s.tr_numero
FROM silta s
  LEFT JOIN siltatarkastus s1 ON (s1.silta = s.id
                                  AND s1.poistettu = FALSE)
  LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id
                                  AND s2.tarkastusaika > s1.tarkastusaika
                                  AND s2.poistettu = FALSE)
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
      AND s2.id IS NULL;

-- name: hae-urakan-sillat-puutteet
-- Hakee alueurakan sillat, joissa on puutteita (tulos muu kuin A) uusimmassa tarkastuksessa.
-- DEPRECATED
SELECT
  s.id,
  s.siltanimi,
  s.siltatunnus,
  s.alue,
  s1.tarkastusaika,
  s1.tarkastaja,
  s.tr_alkuosa,
  s.tr_alkuetaisyys,
  s.tr_loppuosa,
  s.tr_loppuetaisyys,
  s.tr_numero,
  (SELECT array_agg(concat(k.kohde, '=', COALESCE(k.tulos, ' '), ':', k.lisatieto))
   FROM siltatarkastuskohde k
   WHERE k.siltatarkastus = s1.id
         AND k.tulos != 'A') AS kohteet
FROM silta s
  LEFT JOIN siltatarkastus s1 ON (s1.silta = s.id
                                  AND s1.poistettu = FALSE)
  LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id
                                  AND s2.tarkastusaika > s1.tarkastusaika
                                  AND s2.poistettu = FALSE)
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
      AND s2.id IS NULL;

--name: hae-urakan-sillat-korjattavat
-- Hakee sillat, joissa viimeisimmässä tarkastuksessa vähintään 1 B TAI C kohde.
-- Erona edellisiin puutteisiin on, että nyt ei näytetä D:tä
SELECT
  s.id,
  s.siltanimi,
  s.siltatunnus,
  s.alue,
  s1.tarkastusaika,
  s1.tarkastaja,
  s.tr_alkuosa,
  s.tr_alkuetaisyys,
  s.tr_loppuosa,
  s.tr_loppuetaisyys,
  s.tr_numero,
  (SELECT array_agg(concat(k.kohde, '=', COALESCE(k.tulos, ' '), ':', k.lisatieto))
   FROM siltatarkastuskohde k
   WHERE k.siltatarkastus = s1.id
         AND
         (k.tulos = 'C' OR k.tulos = 'B'))
    AS kohteet
FROM silta s
  LEFT JOIN siltatarkastus s1 ON (s1.silta = s.id
                                  AND s1.poistettu = FALSE)
  LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id
                                  AND s2.tarkastusaika > s1.tarkastusaika
                                  AND s2.poistettu = FALSE)
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
      AND s2.id IS NULL;

-- name: hae-urakan-sillat-ohjelmoitavat
-- Hakee sillat, joiden tulos on D ("Korjaus ohjelmoitava")
SELECT
  s.id,
  s.siltanimi,
  s.siltatunnus,
  s.alue,
  s1.tarkastusaika,
  s1.tarkastaja,
  s.tr_alkuosa,
  s.tr_alkuetaisyys,
  s.tr_loppuosa,
  s.tr_loppuetaisyys,
  s.tr_numero,
  (SELECT array_agg(concat(k.kohde, '=', COALESCE(k.tulos, ' '), ':', k.lisatieto))
   FROM siltatarkastuskohde k
   WHERE k.siltatarkastus = s1.id
         AND k.tulos = 'D') AS kohteet
FROM silta s
  LEFT JOIN siltatarkastus s1 ON (s1.silta = s.id
                                  AND s1.poistettu = FALSE)
  LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id
                                  AND s2.tarkastusaika > s1.tarkastusaika
                                  AND s2.poistettu = FALSE)
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
      AND s2.id IS NULL;

-- name: hae-urakan-sillat-korjatut
-- Hakee sillat, joille on aiemmassa tarkastuksessa on ollut virheitä.
-- Palauttaa aimmin rikki olleet kohteet sekä nyt rikki olevien lukumäärän.
SELECT
  (SELECT COUNT(k1.kohde)
   FROM siltatarkastuskohde k1
   WHERE k1.siltatarkastus = st1.id AND (tulos = 'B' OR tulos = 'C')) AS "rikki-ennen",
  (SELECT array_agg(concat(k1.kohde, '=', k1.tulos, ':'))
   FROM siltatarkastuskohde k1
   WHERE k1.siltatarkastus = st1.id AND (tulos = 'B' OR tulos = 'C'))
                                                                      AS kohteet,
  (SELECT COUNT(k2.kohde)
   FROM siltatarkastuskohde k2
   WHERE k2.siltatarkastus = st2.id AND (tulos = 'B' OR tulos = 'C')) AS "rikki-nyt",
  s.id,
  s.siltanimi,
  s.siltatunnus,
  s.alue,
  st2.tarkastusaika,
  st2.tarkastaja,
  s.tr_alkuosa,
  s.tr_alkuetaisyys,
  s.tr_loppuosa,
  s.tr_loppuetaisyys,
  s.tr_numero
FROM siltatarkastus st1
  JOIN siltatarkastus st2 ON (st2.silta = st1.silta AND st2.tarkastusaika > st1.tarkastusaika
                              AND st2.poistettu = FALSE)
  JOIN silta s ON st1.silta = s.id
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka) AND st1.poistettu = FALSE;

-- name: hae-sillan-tarkastukset
-- Hakee sillan sillantarkastukset
SELECT
  st.id,
  silta,
  st.urakka,
  tarkastusaika,
  tarkastaja,
  st.luotu,
  st.luoja,
  muokattu,
  muokkaaja,
  poistettu,
  (SELECT array_agg(concat(k.kohde, '=', COALESCE(k.tulos, ' '), ':', k.lisatieto))
   FROM siltatarkastuskohde k
   WHERE k.siltatarkastus = st.id) AS kohteet,
  skl.kohde as liite_kohde,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM siltatarkastus st
  LEFT JOIN siltatarkastus_kohde_liite skl ON st.id = skl.siltatarkastus
  LEFT JOIN liite ON skl.liite = liite.id
WHERE silta = :silta
      AND poistettu = FALSE
ORDER BY tarkastusaika DESC;

-- name: hae-siltatarkastus
-- Hakee yhden siltatarkastuksen id:n mukaan
SELECT
  st.id,
  silta,
  st.urakka,
  tarkastusaika,
  tarkastaja,
  st.luotu,
  st.luoja,
  muokattu,
  muokkaaja,
  poistettu,
  (SELECT array_agg(concat(k.kohde, '=', COALESCE(k.tulos, ' '), ':', k.lisatieto))
   FROM siltatarkastuskohde k
   WHERE k.siltatarkastus = st.id) AS kohteet,
  skl.kohde as liite_kohde,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM siltatarkastus st
  LEFT JOIN siltatarkastus_kohde_liite skl ON st.id = skl.siltatarkastus
  LEFT JOIN liite ON skl.liite = liite.id
WHERE st.id = :id
      AND poistettu = FALSE;

-- name: hae-siltatarkastus-ulkoisella-idlla-ja-luojalla
-- Hakee yhden siltatarkastuksen ulkoisella id:llä ja luojalla
SELECT
  id,
  silta,
  urakka,
  tarkastusaika,
  tarkastaja,
  luotu,
  luoja,
  muokattu,
  muokkaaja,
  poistettu
FROM siltatarkastus
WHERE ulkoinen_id = :id AND luoja = :luoja AND poistettu = FALSE;

-- name: hae-silta-tunnuksella
-- Hakee sillan siltanumerolla
SELECT
  id,
  tyyppi,
  siltatunnus,
  siltanimi
FROM silta
WHERE siltatunnus = :siltatunnus;

-- name: hae-silta-idlla
-- Hakee sillan siltaidlla
SELECT
  id,
  tyyppi,
  siltatunnus,
  siltanimi
FROM silta
WHERE siltaid = :id;

-- name: luo-siltatarkastus<!
-- Luo uuden siltatarkastuksen annetulla sillalle.
INSERT
INTO siltatarkastus
(silta, urakka, tarkastusaika, tarkastaja, luotu, luoja, poistettu, ulkoinen_id, lahde)
VALUES (:silta, :urakka, :tarkastusaika, :tarkastaja, current_timestamp, :luoja, FALSE,
        :ulkoinen_id, :lahde::lahde);

-- name: paivita-siltatarkastus-ulkoisella-idlla<!
-- Päivittää siltatarkastuksen
UPDATE siltatarkastus
SET silta       = :silta,
  urakka        = :urakka,
  tarkastusaika = :tarkastusaika,
  tarkastaja    = :tarkastaja,
  luoja         = :luoja,
  poistettu     = :poistettu
WHERE ulkoinen_id = :ulkoinen_id;

-- name: hae-siltatarkastusten-kohteet
-- Hakee annettujen siltatarkastusten kohteet ID:iden perusteella
SELECT
  siltatarkastus,
  kohde,
  tulos,
  lisatieto
FROM siltatarkastuskohde
WHERE siltatarkastus = ANY(:siltatarkastus_idt);

-- name: paivita-siltatarkastus!
-- Päivittää siltatarkastuksen tiedot
UPDATE siltatarkastus
  SET
  tarkastaja = :tarkastaja,
  tarkastusaika = :tarkastusaika,
  muokattu = NOW(),
  muokkaaja = :kayttaja
  WHERE id = :id
      AND urakka = :urakka;

-- name: paivita-siltatarkastuksen-kohteet!
-- Päivittää olemassaolevan siltatarkastuksen kohteet
UPDATE siltatarkastuskohde
SET tulos = :tulos, lisatieto = :lisatieto
WHERE siltatarkastus = :siltatarkastus
      AND kohde = :kohde
      AND (SELECT urakka FROM siltatarkastus WHERE id = :siltatarkastus) = :urakka;

-- name: luo-siltatarkastuksen-kohde<!
-- Luo siltatarkastukselle uuden kohteet
INSERT
INTO siltatarkastuskohde
(tulos, lisatieto, siltatarkastus, kohde)
VALUES (:tulos, :lisatieto, :siltatarkastus, :kohde);

-- name: poista-siltatarkastus!
-- Merkitsee annetun siltatarkastuksen poistetuksi
SET poistettu = TRUE
WHERE id = :id
      AND urakka = :urakka;

-- name: poista-siltatarkastukset-ulkoisilla-idlla-ja-luojalla!
-- Merkitsee annetun siltatarkastuksen poistetuksi
UPDATE siltatarkastus
  SET poistettu = TRUE,
  muokattu = NOW(),
  muokkaaja = :kayttaja-id
WHERE ulkoinen_id::integer IN (:ulkoiset-idt)
  AND luoja = :kayttaja-id
  AND urakka = :urakka-id;

-- name: poista-siltatarkastuskohteet!
-- Poistaa siltatarkastuksen kohteet siltatarkastuksen
DELETE FROM siltatarkastuskohde
WHERE siltatarkastus = :siltatarkastus;

-- name: luo-silta!
INSERT INTO silta (tyyppi, siltanro, siltanimi, alue, tr_numero, tr_alkuosa, tr_alkuetaisyys, siltatunnus, siltaid)
VALUES (:nimi, :siltanro, :siltanimi, ST_GeomFromText(:geometria) :: GEOMETRY, :numero, :aosa, :aet, :tunnus, :siltaid);

-- name: paivita-silta-idlla!
UPDATE silta
SET tyyppi        = :tyyppi,
  siltanro        = :siltanro,
  siltanimi       = :nimi,
  alue            = ST_GeomFromText(:geometria) :: GEOMETRY,
  tr_numero       = :numero,
  tr_alkuosa      = :aosa,
  tr_alkuetaisyys = :aet,
  siltatunnus     = :tunnus
WHERE siltaid = :siltaid;

-- name: paivita-urakoiden-sillat
SELECT paivita_sillat_alueurakoittain();

-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT id
              FROM siltatarkastus
              WHERE silta = :silta AND
                    ulkoinen_id != :ulkoinen_id AND
                    tarkastusaika = :tarkastusaika);

-- name: lisaa-liite-siltatarkastuskohteelle<!
INSERT INTO siltatarkastus_kohde_liite (siltatarkastus, kohde, liite)
    VALUES (:siltatarkastus, :kohde, :liite);

-- name: hae-sillan-urakat
SELECT urakka FROM sillat_alueurakoittain WHERE silta = :siltaid;
