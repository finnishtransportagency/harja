-- name: poista-urakan-yha-tiedot!
-- Poistaa urakan yha-tiedot
DELETE FROM yhatiedot
WHERE urakka = :urakka;

-- name: lisaa-urakalle-yha-tiedot<!
-- Lisää urakalle YHA-tiedot
INSERT INTO yhatiedot
(urakka, yhatunnus, yhaid, yhanimi, elyt, vuodet, kohdeluettelo_paivitetty, luotu, linkittaja, muokattu)
VALUES (:urakka, :yhatunnus, :yhaid, :yhanimi, :elyt :: TEXT [], :vuodet :: INTEGER [], NULL, NOW(), :kayttaja, NOW());

-- name: hae-urakan-yhatiedot
SELECT
  yhatunnus,
  yhaid,
  yhanimi,
  elyt,
  vuodet,
  kohdeluettelo_paivitetty AS "kohdeluettelo-paivitetty",
  kohdeluettelo_paivittaja AS "kohdeluettelo-paivittaja",
  k.etunimi                AS "kohdeluettelo-paivittaja-etunimi",
  k.sukunimi               AS "kohdeluettelo-paivittaja-sukunimi",
  sidonta_lukittu          AS "sidonta-lukittu"
FROM yhatiedot yt
  LEFT JOIN kayttaja k ON k.id = yt.kohdeluettelo_paivittaja
WHERE urakka = :urakka;

-- name: poista-urakan-yllapitokohteet!
UPDATE yllapitokohde
SET poistettu = TRUE
WHERE urakka = :urakka;

-- name: hae-yllapitokohteen-kohdeosat
SELECT
  id,
  yllapitokohde,
  nimi,
  tunnus,
  tr_numero                  AS "tr-numero",
  tr_alkuosa                 AS "tr-alkuosa",
  tr_alkuetaisyys            AS "tr-alkuetaisyys",
  tr_loppuosa                AS "tr-loppuosa",
  tr_loppuetaisyys           AS "tr-loppuetaisyys",
  tr_ajorata                 AS "tr-ajorata",
  tr_kaista                  AS "tr-kaista",
  poistettu,
  sijainti,
  yhaid,
  toimenpide,
  (SELECT viimeisin_paivitys
   FROM geometriapaivitys
   WHERE nimi = 'tieverkko') AS karttapvm
FROM yllapitokohdeosa
WHERE yllapitokohde = :yllapitokohde AND
      poistettu IS NOT TRUE;

-- name: hae-urakoiden-sidontatiedot
SELECT
  yt.yhaid,
  u.nimi AS "sidottu-urakkaan"
FROM yhatiedot yt
  JOIN urakka u ON yt.urakka = u.id
WHERE yt.yhaid IN (:yhaidt);

-- name: luo-yllapitokohde<!
INSERT INTO yllapitokohde
(urakka, sopimus, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
 tr_ajorata, tr_kaista,
 yhatunnus, yhaid, yha_kohdenumero, kohdenumero, yllapitokohdetyyppi, yllapitokohdetyotyyppi, yllapitoluokka, keskimaarainen_vuorokausiliikenne,
 nykyinen_paallyste, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi, kaasuindeksi, nimi, vuodet)
VALUES (
  :urakka,
  (SELECT id
   FROM sopimus
   WHERE paasopimus IS NULL AND urakka = :urakka),
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_ajorata,
  :tr_kaista,
  :yhatunnus,
  :yhaid,
  :yha_kohdenumero,
  :kohdenumero,
  :yllapitokohdetyyppi :: yllapitokohdetyyppi,
  :yllapitokohdetyotyyppi :: yllapitokohdetyotyyppi,
  :yllapitoluokka,
  :keskimaarainen_vuorokausiliikenne,
  :nykyinen_paallyste,
  0,
  0,
  0,
  0,
  :nimi,
  :vuodet :: INTEGER []);

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tunnus, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti,
                              yhaid)
VALUES (
  :yllapitokohde,
  :nimi,
  :tunnus,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_ajorata,
  :tr_kaista,
  (SELECT tierekisteriosoitteelle_viiva AS geom
   FROM tierekisteriosoitteelle_viiva(CAST(:tr_numero AS INTEGER),
                                      CAST(:tr_alkuosa AS INTEGER),
                                      CAST(:tr_alkuetaisyys AS INTEGER),
                                      CAST(:tr_loppuosa AS INTEGER),
                                      CAST(:tr_loppuetaisyys AS INTEGER))),
  :yhaid);

-- name: hae-urakan-yha-id
-- single?: true
SELECT yhaid
FROM yhatiedot
WHERE urakka = :urakkaid;

-- name: hae-urakan-kohteiden-yha-idt
SELECT yhaid
FROM yllapitokohde
WHERE urakka = :urakkaid AND poistettu IS FALSE;

-- name: merkitse-urakan-yllapitokohteet-paivitetyksi<!
UPDATE yhatiedot
SET
  kohdeluettelo_paivitetty = NOW(),
  kohdeluettelo_paivittaja = :kayttaja
WHERE urakka = :urakka AND poistettu IS FALSE;

-- name: luo-paallystysilmoitus<!
INSERT INTO paallystysilmoitus
(paallystyskohde, ilmoitustiedot, luotu, luoja)
VALUES (:paallystyskohde, :ilmoitustiedot :: JSONB, NOW(), :luoja);

-- name: lukitse-urakan-yha-sidonta<!
UPDATE yhatiedot
SET sidonta_lukittu = TRUE
WHERE urakka = :urakka;

-- name: poista-urakan-paallystysilmoitukset!
DELETE FROM paallystysilmoitus
WHERE paallystyskohde IN (SELECT id
                          FROM yllapitokohde
                          WHERE urakka = :urakka);

-- name: poista-urakan-paikkausilmoitukset!
DELETE FROM paikkausilmoitus
WHERE paikkauskohde IN (SELECT id
                        FROM yllapitokohde
                        WHERE urakka = :urakka);