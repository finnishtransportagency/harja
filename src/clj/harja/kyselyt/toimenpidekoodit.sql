-- name: hae-kaikki-toimenpidekoodit
-- Listaa kaikki toimenpidekoodit.
SELECT
  id,
  koodi,
  nimi,
  emo,
  taso,
  yksikko,
  kokonaishintainen
FROM toimenpidekoodi
WHERE poistettu = FALSE;

-- name: lisaa-toimenpidekoodi<!
-- Lisää uuden 4. tason toimenpidekoodin (tehtäväkoodi).
INSERT INTO toimenpidekoodi (nimi, emo, taso, yksikko, kokonaishintainen, luoja, luotu, muokattu)
VALUES (:nimi, :emo, 4, :yksikko, :kokonaishintainen, :kayttajaid, NOW(), NOW());

-- name: poista-toimenpidekoodi!
-- Poistaa (merkitsee poistetuksi) annetun toimenpidekoodin.
UPDATE toimenpidekoodi
SET poistettu = TRUE, muokkaaja = :kayttajaid, muokattu = NOW()
WHERE id = :id;

-- name: muokkaa-toimenpidekoodi!
-- Muokkaa annetun toimenpidekoodin nimen.
UPDATE toimenpidekoodi
SET muokkaaja = :kayttajaid, muokattu = NOW(), nimi = :nimi, yksikko = :yksikko, kokonaishintainen = :kokonaishintainen
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
