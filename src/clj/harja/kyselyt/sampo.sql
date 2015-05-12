-- name: hae-maksuera
-- Hakee id:n perusteella maksueran lähettämiseen tarvittavat tiedot
SELECT
  m.numero,
  m.tyyppi,
  tpi.alkupvm as toimenpideinstanssi_alkupvm,
  tpi.loppupvm as toimenpideinstanssi_loppupvm,
  tpi.vastuuhenkilo_id as toimenpideinstanssi_vastuuhenkilo,
  tpi.talousosasto_id as toimenpideinstanssi_talousosasto,
  tpi.tuotepolku as toimenpideinstanssi_tuotepolku,
  u.sampoid as urakka_sampoid
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN urakka u ON u.id = tpi.urakka
WHERE m.numero = :numero;