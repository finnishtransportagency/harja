-- name: hae-kaikki-toimenpidekoodit
-- Listaa kaikki toimenpidekoodit.
SELECT
  t.id,
  t.koodi,
  t.nimi,
  t.emo,
  t.taso,
  t.yksikko,
  t.jarjestys,
  t.hinnoittelu,
  t.poistettu,
  t.luoja        AS luoja_id,
  k.kayttajanimi AS luoja_kayttajanimi,
  k.etunimi      AS luoja_etunimi,
  k.sukunimi     AS luoja_sukunimi,
  api_seuranta   AS "api-seuranta"
FROM toimenpidekoodi t
  LEFT JOIN kayttaja k ON t.luoja = k.id

-- name: lisaa-toimenpidekoodi<!
-- Lisää uuden 4. tason toimenpidekoodin (tehtäväkoodi).
INSERT INTO toimenpidekoodi (nimi, emo, taso, yksikko, hinnoittelu, api_seuranta, luoja, luotu, muokattu)
VALUES (:nimi, :emo, 4, :yksikko, :hinnoittelu :: hinnoittelutyyppi [], :apiseuranta, :kayttajaid, NOW(), NOW());

-- name: poista-toimenpidekoodi!
-- Poistaa (merkitsee poistetuksi) annetun toimenpidekoodin.
UPDATE toimenpidekoodi
SET poistettu = TRUE, muokkaaja = :kayttajaid, muokattu = NOW()
WHERE id = :id;

-- name: muokkaa-toimenpidekoodi!
-- Muokkaa annetun toimenpidekoodin nimen.
UPDATE toimenpidekoodi
SET muokkaaja = :kayttajaid, muokattu = NOW(), poistettu = :poistettu,
  nimi        = :nimi, yksikko = :yksikko,
  hinnoittelu = :hinnoittelu :: hinnoittelutyyppi [], api_seuranta = :apiseuranta
WHERE id = :id;

-- name: viimeisin-muokkauspvm
-- Antaa MAX(muokattu) päivämäärän toimenpidekoodeista
SELECT MAX(muokattu) AS muokattu
FROM toimenpidekoodi;

--name: hae-neljannen-tason-toimenpidekoodit
SELECT
  id,
  koodi,
  nimi,
  emo,
  taso,
  yksikko
FROM toimenpidekoodi
WHERE poistettu IS NOT TRUE AND
      emo = :emo;

--name: hae-emon-nimi
SELECT nimi
FROM toimenpidekoodi
WHERE id = (SELECT emo
            FROM toimenpidekoodi
            WHERE id = :id);

-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT id
              FROM toimenpidekoodi
              WHERE koodi = :toimenpidekoodi);

-- name: onko-olemassa-idlla?
-- single?: true
SELECT exists(SELECT id
              FROM toimenpidekoodi
              WHERE id = :id);

-- name: hae-apin-kautta-seurattavat-yksikkohintaiset-tehtavat
SELECT
  tpk.id,
  tpk.nimi,
  tpk.yksikko
FROM toimenpidekoodi tpk
WHERE
  NOT tpk.poistettu AND
  tpk.api_seuranta AND
  tpk.hinnoittelu @> '{yksikkohintainen}';

-- name: hae-apin-kautta-seurattavat-kokonaishintaiset-tehtavat
SELECT
  tpk.id,
  tpk.nimi,
  tpk.yksikko
FROM toimenpidekoodi tpk
WHERE
  NOT tpk.poistettu AND
  tpk.api_seuranta AND
  tpk.hinnoittelu @> '{kokonaishintainen}';

-- name: hae-hinnoittelu
SELECT hinnoittelu
FROM toimenpidekoodi
WHERE id = :id;
