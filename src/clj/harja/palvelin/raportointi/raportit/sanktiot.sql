-- name: hae-sanktiot
-- Hakee sanktiot
SELECT
  sakkoryhma,
  maara,
  indeksi,
  suorasanktio,
  st.id          AS sanktiotyyppi_id,
  st.nimi        AS sanktiotyyppi_nimi,
  st.sanktiolaji AS sanktiotyyppi_laji,
  tpi.id         AS toimenpideinstanssi_id,
  tpi.nimi       AS toimenpideinstanssi_nimi,
  lp.id          AS laatupoikkeama_id,
  lp.aika        AS laatupoikkeama_aika,
  u.id           AS urakka_id,
  u.nimi         AS urakka_nimi
FROM sanktio s
  JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  JOIN urakka u ON lp.urakka = u.id
WHERE (:urakka::INTEGER IS NULL OR lp.urakka = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR lp.urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko =
                                                           :hallintayksikko))
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                 u.tyyppi = :urakkatyyppi :: urakkatyyppi)))
      AND lp.aika :: DATE BETWEEN :alku AND :loppu
ORDER BY urakka_nimi;
