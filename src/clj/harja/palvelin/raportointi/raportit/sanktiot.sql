-- name: hae-sanktiot
-- Hakee sanktiot
SELECT
  s.id,
  sakkoryhma,
  maara AS summa,
  s.indeksi,
  suorasanktio,
  st.id          AS sanktiotyyppi_id,
  st.nimi        AS sanktiotyyppi_nimi,
  tpi.id         AS toimenpideinstanssi_id,
  tpi.nimi       AS toimenpideinstanssi_nimi,
  u.id           AS "urakka-id",
  u.nimi         AS nimi,
  o.id           AS hallintayksikko_id,
  o.nimi         AS hallintayksikko_nimi,
  o.elynumero    AS hallintayksikko_elynumero,
  (SELECT nimi FROM toimenpidekoodi WHERE id = (SELECT emo FROM toimenpidekoodi WHERE id = tpi.toimenpide)) AS toimenpidekoodi_taso2,
  CASE WHEN s.indeksi IS NOT NULL THEN
    kuukauden_indeksikorotus(s.perintapvm::DATE, s.indeksi, s.maara, u.id) - s.maara
  END AS indeksikorotus
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  JOIN urakka u ON (tpi.urakka = u.id OR lp.urakka = u.id)
  JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka)
      AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR (u.id IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko =
                                                           :hallintayksikko) AND u.urakkanro IS NOT NULL))
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                 u.tyyppi = :urakkatyyppi :: urakkatyyppi)))
      AND s.perintapvm BETWEEN :alku AND :loppu;