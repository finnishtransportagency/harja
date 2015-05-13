-- name: hae-urakan-maksuerat
-- Hakee id:n perusteella maksueran lähettämiseen tarvittavat tiedot
-- FIXME: miten haetaan summat?
SELECT
  m.numero,
  m.tyyppi,
  m.lahetetty,
  tpi.id       AS toimenpideinstanssi_id,
  tpi.nimi     AS toimenpideinstanssi_nimi,
  tpi.alkupvm  AS toimenpideinstanssi_alkupvm,
  tpi.loppupvm AS toimenpideinstanssi_loppupvm,
  s.sampoid    AS sopimus_sapoid
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN urakka u ON u.id = tpi.urakka
  JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
WHERE tpi.urakka = :urakkaid;


-- name: hae-lahetettava-maksuera
-- Hakee numeron perusteella maksueran lähettämiseen tarvittavat tiedot
SELECT
  m.numero,
  m.tyyppi,
  tpi.alkupvm          AS toimenpideinstanssi_alkupvm,
  tpi.loppupvm         AS toimenpideinstanssi_loppupvm,
  tpi.vastuuhenkilo_id AS toimenpideinstanssi_vastuuhenkilo,
  tpi.talousosasto_id  AS toimenpideinstanssi_talousosasto,
  tpi.tuotepolku       AS toimenpideinstanssi_tuotepolku,
  u.sampoid            AS urakka_sampoid,
  s.sampoid            AS sopimus_sapoid
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN urakka u ON u.id = tpi.urakka
  JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
WHERE m.numero = :numero;

-- name: lukitse-maksuera!
-- Lukitsee maksuerän lähetyksen ajaksi
UPDATE maksuera
SET lukko = :lukko, lukittu = current_timestamp
WHERE numero = :numero AND (lukko IS NULL OR
                            (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));

-- name: merkitse-maksuera-lahetetyksi!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE maksuera
SET lahetetty = current_timestamp, lahetysid = :lahetysid, lukko = NULL
WHERE numero = :numero;
