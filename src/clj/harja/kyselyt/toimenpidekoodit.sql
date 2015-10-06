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

--name: hae-toimenpidekoodit-historiakuvaan
SELECT
  tpk4.id,
  tpk4.koodi,
  tpk4.nimi,
  (SELECT nimi
   FROM toimenpidekoodi t2
   WHERE t2.taso = 2
         AND t2.id =
             (SELECT emo
              FROM toimenpidekoodi t3
              WHERE t3.id = tpk4.emo)) AS emo,
  tpk4.taso,
  tpk4.yksikko
FROM toimenpidekoodi tpk4
  INNER JOIN toimenpideinstanssi tpi ON tpk4.emo = tpi.toimenpide
                                        AND tpi.urakka IN (:urakat)
WHERE poistettu IS NOT TRUE
      AND tpk4.taso = 4;

--name: hae-emon-nimi
SELECT nimi
FROM toimenpidekoodi
WHERE id = (SELECT emo
            FROM toimenpidekoodi
            WHERE koodi = :id);