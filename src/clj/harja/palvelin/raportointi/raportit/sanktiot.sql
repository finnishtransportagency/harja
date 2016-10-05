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
  u.id           AS urakka_id,
  u.nimi         AS urakka_nimi,
  o.id           AS hallintayksikko_id,
  o.nimi         AS hallintayksikko_nimi,
  o.elynumero    AS hallintayksikko_elynumero,
  (SELECT nimi FROM toimenpidekoodi WHERE id = (SELECT emo FROM toimenpidekoodi WHERE id = tpi.toimenpide)) AS toimenpidekoodi_taso2,
  CASE WHEN s.indeksi IS NOT NULL THEN
    kuukauden_indeksikorotus(s.perintapvm::DATE, s.indeksi, s.maara, u.id) - s.maara
  END AS indeksikorotus
FROM sanktio s
  JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  JOIN urakka u ON tpi.urakka = u.id
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

-- name: hae-kontekstin-urakat
-- Listaa kaikki ne urakat, joita haku koskee
SELECT
  u.id           AS urakka_id,
  u.nimi         AS urakka_nimi
FROM urakka u
WHERE
  (:urakka :: INTEGER IS NULL OR u.id = :urakka)
  AND (:hallintayksikko :: INTEGER IS NULL OR hallintayksikko = :hallintayksikko)
  AND (:urakka :: INTEGER IS NOT NULL OR
       (:urakka :: INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                        u.tyyppi = :urakkatyyppi :: urakkatyyppi)))
  AND (:urakka :: INTEGER IS NOT NULL OR :urakka :: INTEGER IS NULL AND ((alkupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (loppupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (:alku >= alkupvm AND :loppu <= loppupvm)))
ORDER BY urakka_nimi;

-- name: hae-kontekstin-hallintayksikot
-- Listaa kaikki ne hallintayksikot, joita haku koskee
SELECT
  o.id           AS hallintayksikko_id,
  o.nimi         AS hallintayksikko_nimi,
  o.elynumero    AS hallintayksikko_elynumero
FROM organisaatio o
WHERE elynumero IS NOT NULL
ORDER BY hallintayksikko_elynumero;
