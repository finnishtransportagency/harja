-- name: luo-toimenpideinstanssi<!
-- Luo uuden toimenpideinstanssin.
INSERT INTO toimenpideinstanssi (sampoid, nimi, alkupvm, loppupvm, vastuuhenkilo_id, talousosasto_id, talousosastopolku,
                                 tuote_id, tuotepolku, urakka_sampoid, toimenpide, urakka)
VALUES (:sampoid, :nimi, :alkupvm, :loppupvm, :vastuuhenkilo_id, :talousosasto_id, :talousosasto_polku, :tuote_id,
                  :tuote_polku, :urakka_sampoid,
                  (SELECT id
                   FROM toimenpidekoodi
                   WHERE koodi = :sampo_toimenpidekoodi),
        (SELECT id
         FROM urakka
         WHERE sampoid = :urakka_sampoid));

-- name: luo-yllapidon-toimenpideinstanssi<!
-- Luo uuden ylläpidon toimenpideinstanssin.
INSERT INTO toimenpideinstanssi (nimi, alkupvm, loppupvm, toimenpide, urakka)
VALUES ((SELECT nimi
         FROM toimenpidekoodi
         WHERE koodi = :toimenpidekoodi),
        :alkupvm, :loppupvm,
        (SELECT id
         FROM toimenpidekoodi
         WHERE koodi = :toimenpidekoodi),
        :urakkaid);

-- name: onko-tuotu-samposta
-- Tarkistaa onko Samposta tuodulla urakalla jo toimenpidekoodilla tuotu toimenpideinstanssi
SELECT exists(
    SELECT id
    FROM toimenpideinstanssi
    WHERE toimenpide = (SELECT id
                        FROM toimenpidekoodi
                        WHERE koodi = :sampo_toimenpidekoodi) AND
          sampoid != :sampo_toimenpide_id AND
          urakka_sampoid = :urakka_sampoid);

-- name: paivita-toimenpideinstanssi!
-- Paivittaa toimenpideinstanssin.
UPDATE toimenpideinstanssi
SET
  nimi              = :nimi,
  alkupvm           = :alkupvm,
  loppupvm          = :loppupvm,
  vastuuhenkilo_id  = :vastuuhenkilo_id,
  talousosasto_id   = :talousosasto_id,
  talousosastopolku = :talousosasto_polku,
  tuote_id          = :tuote_id,
  tuotepolku        = :tuote_polku,
  urakka_sampoid    = :urakka_sampoid,
  toimenpide        = (SELECT id
                       FROM toimenpidekoodi
                       WHERE koodi = :sampo_toimenpidekoodi),
  urakka            = (SELECT id
                       FROM urakka
                       WHERE sampoid = :urakka_sampoid)
WHERE id = :id;

-- name: hae-id-sampoidlla
-- Hakee sopimuksen id:n sampo id:llä
SELECT id
FROM toimenpideinstanssi
WHERE sampoid = :sampoid;

--name: paivita-urakka-sampoidlla!
-- Päivittää toimenpideinstansseille urakka id:n sen Sampo id:llä
UPDATE toimenpideinstanssi
SET urakka = (
  SELECT id
  FROM urakka
  WHERE sampoid = :urakka_sampoid)
WHERE urakka_sampoid = :urakka_sampoid;

-- name: hae-urakan-toimenpideinstanssi
-- Hakee urakan toimenpideinstanssin urakan ja 3. tason toimenpidekoodin mukaan
SELECT *
FROM toimenpideinstanssi
WHERE urakka = :urakka AND toimenpide = :tp;

-- name: hae-hoidon-maksuerattomat-toimenpideistanssit
SELECT
  tpi.id   AS toimenpide_id,
  tpk.nimi AS toimenpide_nimi
FROM toimenpideinstanssi tpi
  JOIN urakka ON tpi.urakka = urakka.id
  JOIN toimenpidekoodi tpk ON tpk.id = (SELECT emo
                                        FROM toimenpidekoodi
                                        WHERE id = tpi.toimenpide)
WHERE tpi.id NOT IN (SELECT DISTINCT toimenpideinstanssi
                     FROM maksuera
                     WHERE toimenpideinstanssi IS NOT NULL)
      AND urakka.tyyppi = 'hoito';

-- name: onko-urakalla-toimenpide
SELECT exists(SELECT id
              FROM toimenpideinstanssi
              WHERE urakka = :urakka AND toimenpide = (SELECT id
                                                       FROM toimenpidekoodi
                                                       WHERE koodi = :toimenpidekoodi))