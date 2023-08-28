-- name: hae-sanktiot
-- Hakee sanktiot
SELECT
  s.id,
  s.sakkoryhma,
  s.maara AS summa,
  s.indeksi,
  suorasanktio,
  st.id          AS sanktiotyyppi_id,
  st.nimi        AS sanktiotyyppi_nimi,
  st.koodi       AS sanktiotyyppi_koodi,
  tpi.id         AS toimenpideinstanssi_id,
  tpi.nimi       AS toimenpideinstanssi_nimi,
  tpk2.koodi     AS toimenpide_koodi,
  u.id           AS "urakka-id",
  u.nimi         AS nimi,
  u.loppupvm     AS loppupvm,
  o.id           AS hallintayksikko_id,
  o.nimi         AS hallintayksikko_nimi,
  o.elynumero    AS hallintayksikko_elynumero,
  tpk2.nimi      AS toimenpidekoodi_taso2,
  (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, u.id, s.sakkoryhma)) AS indeksikorotus
FROM urakka u
     JOIN toimenpideinstanssi tpi ON tpi.urakka = u.id
     JOIN organisaatio o ON u.hallintayksikko = o.id
     LEFT JOIN sanktio s on tpi.id = s.toimenpideinstanssi
                            AND s.poistettu IS NOT TRUE
                            -- jos hakurange sisältää urakan viimeisen kuukauden, mahdolliset urakan päättymisen jälkeen tulleet sanktiot sisällytetään siihen
                            AND ((s.perintapvm BETWEEN :alku::DATE AND :loppu::DATE) OR
                                 (CASE date_part('year', :loppu::date)::integer = date_part('year', u.loppupvm)::integer
                                     AND date_part('month', :loppu::date)::integer = date_part('month', u.loppupvm)::integer
                                      WHEN TRUE THEN s.perintapvm > u.loppupvm
                                      ELSE FALSE
                                     END))
     LEFT JOIN sanktiotyyppi st ON s.tyyppi = st.id
     LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
                                 -- Ei kuulu poistettuun ylläpitokohteeseen
                                AND (lp.yllapitokohde IS NULL
                                    OR
                                     lp.yllapitokohde IS NOT NULL AND
                                     (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE)
     LEFT JOIN toimenpide tpk3 on tpk3.id = tpi.toimenpide
     LEFT JOIN toimenpide tpk2 on tpk3.emo = tpk2.id
WHERE u.alkupvm < :loppu::DATE AND u.loppupvm > :alku::DATE
    AND ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka) -- varmistaa ettei testiurakka tule mukaan alueraportteihin
    AND (:urakka::INTEGER IS NOT NULL OR (
      :urakkatyyppi :: urakkatyyppi IS NULL OR (
          CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
              ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi
              END))) -- varmistaa oikean urakkatyypin, ottaa huomioon 'teiden-hoito' - urakkatyypin
    AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL)
             OR
         (u.hallintayksikko = :hallintayksikko AND u.urakkanro IS NOT NULL));

-- name: hae-sanktiot-yllapidon-raportille
-- Hakee sanktiot
SELECT
  s.id,
  sakkoryhma,
  -maara AS summa,
  s.indeksi,
  suorasanktio,
  st.id          AS sanktiotyyppi_id,
  st.nimi        AS sanktiotyyppi_nimi,
  tpi.id         AS toimenpideinstanssi_id,
  tpi.nimi       AS toimenpideinstanssi_nimi,
  ypk.yllapitoluokka AS yllapitoluokka,
  u.id           AS "urakka-id",
  u.nimi         AS nimi,
  u.loppupvm     AS loppupvm,
  o.id           AS hallintayksikko_id,
  o.nimi         AS hallintayksikko_nimi,
  o.elynumero    AS hallintayksikko_elynumero,
  (SELECT nimi FROM toimenpide WHERE id = (SELECT emo FROM toimenpide WHERE id = tpi.toimenpide)) AS toimenpidekoodi_taso2
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id AND lp.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
  JOIN urakka u ON (tpi.urakka = u.id OR lp.urakka = u.id) AND u.alkupvm < :loppu AND u.loppupvm > :alku
  JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka) -- varmistaa ettei testiurakka tule mukaan alueraportteihin
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                          u.tyyppi = :urakkatyyppi :: urakkatyyppi))) -- varmistaa oikean urakkatyypin
      AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR (u.id IN (SELECT id
                                                                                        FROM urakka
                                                                                        WHERE hallintayksikko =
                                                                                              :hallintayksikko) AND u.urakkanro IS NOT NULL))
      AND s.poistettu IS NOT TRUE
      -- jos hakurange sisältää urakan viimeisen kuukauden, mahdolliset urakan päättymisen jälkeen tulleet sanktiot sisällytetään siihen
      AND ((s.perintapvm BETWEEN :alku AND :loppu) OR
          (CASE 
                date_part('year', :loppu::date)::integer = date_part('year', u.loppupvm)::integer 
                AND date_part('month', :loppu::date)::integer = date_part('month', u.loppupvm)::integer
           WHEN TRUE THEN s.perintapvm > u.loppupvm 
           ELSE FALSE
           END))
    -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE)
ORDER BY yllapitoluokka;


-- name: hae-bonukset
-- Ylläpito (Päällystys) urakoille on olemassa erillinen sanktio ja bonus raportti. Siitä syystä
-- tässä haussa ei tarvitse hakea ylläpidon bonuksia sanktiot taulusta.
SELECT ek.id,
       ek.pvm                                              AS pvm,
       ek.laskutuskuukausi                                 AS laskutuskuukausi,
       ek.rahasumma                                        AS summa,
       ek.tyyppi::TEXT                                     AS laji,
       u.id                                                AS "urakka-id",
       (SELECT korotus
        from erilliskustannuksen_indeksilaskenta(ek.pvm, ek.indeksin_nimi, ek.rahasumma,
                                                 ek.urakka, ek.tyyppi,
                                                 CASE
                                                     WHEN u.tyyppi = 'teiden-hoito'::urakkatyyppi THEN TRUE
                                                     ELSE FALSE
                                                     END)) AS indeksikorotus,
       o.id           AS hallintayksikko_id,
       o.nimi         AS hallintayksikko_nimi,
       o.elynumero    AS hallintayksikko_elynumero
FROM erilliskustannus ek
         JOIN urakka u ON ek.urakka = u.id
         JOIN organisaatio o ON u.hallintayksikko = o.id
    AND u.alkupvm < :loppu::DATE
    AND u.loppupvm > :alku::DATE -- Varmista, että urakka on käynnissä annetulla aikavälillä
    AND ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka) -- varmistaa ettei testiurakka tule mukaan alueraportteihin
    AND (:urakka::INTEGER IS NOT NULL OR (
            :urakkatyyppi :: urakkatyyppi IS NULL OR (
            CASE
                WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
                ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi
                END))) -- varmistaa oikean urakkatyypin, ottaa huomioon 'teiden-hoito' - urakkatyypin
    AND ((:hallintayksikko::INTEGER IS NULL AND u.urakkanro IS NOT NULL)
        OR
         (u.hallintayksikko = :hallintayksikko AND u.urakkanro IS NOT NULL))
    -- MHU urakoille on olennaista, että bonukset on tallennettu 23150 koodilla olevalle toimenpideinstanssille
    -- eli hoidon johdolle. Alueurakoilla tätä vaatimusta ei ole. Joten bonukset voivat kohdistua
    -- vapaammin mille tahansa toimenpideinstanssille
    AND (u.tyyppi = 'hoito' OR (u.tyyppi = 'teiden-hoito'
        AND ek.toimenpideinstanssi = (SELECT tpi.id AS id
                                      FROM toimenpideinstanssi tpi
                                               JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                                               JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id,
                                           maksuera m
                                      WHERE tpi.urakka = u.id
                                        AND m.toimenpideinstanssi = tpi.id
                                        AND tpk2.koodi = '23150'
                                      LIMIT 1)))
WHERE ek.laskutuskuukausi BETWEEN :alku AND :loppu
  AND ek.poistettu IS NOT TRUE
  AND ek.tyyppi != 'muu'::erilliskustannustyyppi;
