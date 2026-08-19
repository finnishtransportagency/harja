-- name: hae-sanktiot
-- Hakee sanktiot
SELECT
  s.id,
  s.sakkoryhma,
  s.maara AS summa,
  s.indeksi,
  suorasanktio,
  st.id                   AS sanktiotyyppi_id,
  tpi.id                  AS toimenpideinstanssi_id,
  tpi.nimi                AS toimenpideinstanssi_nimi,
  tpk2.koodi              AS toimenpide_koodi,
  u.id                    AS "urakka-id",
  u.nimi                  AS nimi,
  u.loppupvm              AS loppupvm,
  o.id                    AS elinvoimakeskus_id,
  o.nimi                  AS elinvoimakeskus_nimi,
  o.elinvoimakeskusnumero AS elinvoimakeskus_evknumero,
  tpk2.nimi      AS toimenpidekoodi_taso2,
  (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, u.id, s.sakkoryhma)) AS indeksikorotus
FROM urakka u
     JOIN toimenpideinstanssi tpi ON tpi.urakka = u.id
     JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
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
    AND ((:elinvoimakeskus::INTEGER IS NULL AND u.urakkanro IS NOT NULL)
             OR
         (u.elinvoimakeskus_id = :elinvoimakeskus AND u.urakkanro IS NOT NULL));

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
  u.id           AS "urakka-id",
  u.nimi         AS nimi,
  u.loppupvm     AS loppupvm,
  o.id           AS elinvoimakeskus_id,
  o.nimi         AS elinvoimakeskus_nimi,
  o.elinvoimakeskusnumero    AS elinvoimakeskus_evknumero,
  (SELECT nimi FROM toimenpide WHERE id = (SELECT emo FROM toimenpide WHERE id = tpi.toimenpide)) AS toimenpidekoodi_taso2
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id AND lp.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
  JOIN urakka u ON (tpi.urakka = u.id OR lp.urakka = u.id) AND u.alkupvm < :loppu AND u.loppupvm > :alku
  JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
WHERE ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka) -- varmistaa ettei testiurakka tule mukaan alueraportteihin
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                          u.tyyppi = :urakkatyyppi :: urakkatyyppi))) -- varmistaa oikean urakkatyypin
      AND ((:elinvoimakeskus::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR (u.id IN (SELECT id
                                                                                        FROM urakka
                                                                                        WHERE elinvoimakeskus_id =
                                                                                              :elinvoimakeskus) AND u.urakkanro IS NOT NULL))
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
       o.id           AS elinvoimakeskus_id,
       o.nimi         AS elinvoimakeskus_nimi,
       o.elinvoimakeskusnumero    AS elinvoimakeskus_evknumero
FROM erilliskustannus ek
         JOIN urakka u ON ek.urakka = u.id
         JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
    AND u.alkupvm < :loppu::DATE
    AND u.loppupvm > :alku::DATE -- Varmista, että urakka on käynnissä annetulla aikavälillä
    AND ((:urakka::INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka) -- varmistaa ettei testiurakka tule mukaan alueraportteihin
    AND (:urakka::INTEGER IS NOT NULL OR (
            :urakkatyyppi :: urakkatyyppi IS NULL OR (
            CASE
                WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
                ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi
                END))) -- varmistaa oikean urakkatyypin, ottaa huomioon 'teiden-hoito' - urakkatyypin
    AND ((:elinvoimakeskus::INTEGER IS NULL AND u.urakkanro IS NOT NULL)
        OR
         (u.elinvoimakeskus_id = :elinvoimakeskus AND u.urakkanro IS NOT NULL))
WHERE ek.laskutuskuukausi BETWEEN :alku AND :loppu
  AND ek.poistettu IS NOT TRUE
  AND ek.tyyppi != 'muu'::erilliskustannustyyppi;

-- name: hae-urakkataso-sanktiot
-- Hakee sanktiot urakkataso-sanktioraportille profiili-driven tavalla
SELECT
  s.id AS sanktio_id,
  sl.koodi AS sanktiolaji_koodi,
  COALESCE(splet.nimi, sl.nimi) AS sanktiolaji_nimi,
  st.id AS sanktiotyyppi_id,
  st.nimi AS sanktiotyyppi_nimi,
  st.koodi AS sanktiotyyppi_koodi,
  s.maara AS summa,
  s.indeksi AS indeksi,
  s.suorasanktio,
  (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi, s.maara, u.id, s.sakkoryhma)) AS indeksikorotus,
  u.id AS urakka_id,
  u.nimi AS urakan_nimi
FROM sanktio s
  JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  JOIN urakka u ON tpi.urakka = u.id
  LEFT JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  -- Liitetään profiili ja rivi
  -- Valitaan urakalle sopiva profiili urakan alkupäivämäärän perusteella
  JOIN sanktio_profiili sp
    ON sp.urakkatyyppi = u.tyyppi::TEXT
    AND sp.aktiivinen IS TRUE
    AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
    AND sp.alkupvm <= u.alkupvm
    AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
  -- HUOM: LEFT JOIN, koska sanktiotyypille ei välttämättä löydy profiiliriviä
  -- (esim. vanhentunut/poistettu sanktiotyyppi, jota ei ole koodattu profiiliin).
  -- Sanktio ei saa kadota raportilta tällöin - se näytetään "Tunnistamattomat
  -- sanktiot" -taulukossa (ks. sanktio.clj).
  LEFT JOIN sanktio_profiili_rivi spr
    ON spr.sanktio_profiili_id = sp.id
    AND spr.sanktiotyyppi_id = s.tyyppi
  LEFT JOIN sanktio_laji sl ON spr.sanktio_laji_id = sl.id
  LEFT JOIN sanktio_profiili_laji_esitystiedot splet
    ON splet.sanktio_profiili_id = sp.id
    AND splet.sanktio_laji_id = sl.id
WHERE s.poistettu IS NOT TRUE
  AND s.perintapvm BETWEEN :alku AND :loppu
  AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
    OR (:urakka::INTEGER IS NULL
      AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
  AND (lp.yllapitokohde IS NULL OR (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-urakkataso-bonukset
-- Hakee bonukset urakkataso-sanktioraportille profiili-driven tavalla
SELECT
  ek.id AS bonus_id,
  bl.koodi AS bonuslaji_koodi,
  COALESCE(bplet.nimi, bl.nimi) AS bonuslaji_nimi,
  ek.rahasumma AS summa,
  ek.indeksin_nimi AS indeksi,
  (SELECT korotus
   FROM erilliskustannuksen_indeksilaskenta(ek.pvm, ek.indeksin_nimi, ek.rahasumma,
                                            ek.urakka, ek.tyyppi,
                                            u.tyyppi = 'teiden-hoito')) AS indeksikorotus,
  u.id AS urakka_id,
  u.nimi AS urakan_nimi
FROM erilliskustannus ek
  JOIN urakka u ON ek.urakka = u.id
                AND (:urakka::INTEGER IS NOT NULL AND u.id = :urakka
                  OR :urakka::INTEGER IS NULL AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus))
  -- Liitetään bonus_profiili ja rivi
  -- Valitaan urakalle sopiva profiili urakan alkupäivämäärän perusteella
  JOIN bonus_profiili bp
    ON bp.urakkatyyppi = u.tyyppi::TEXT
    AND bp.aktiivinen IS TRUE
    AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN bp.hoitovuosi_alku AND bp.hoitovuosi_loppu)
    AND bp.alkupvm <= u.alkupvm
    AND (bp.loppupvm IS NULL OR bp.loppupvm >= u.alkupvm)
  JOIN bonus_laji bl ON bl.koodi = ek.tyyppi::TEXT
  JOIN bonus_profiili_rivi bpr
    ON bpr.bonus_profiili_id = bp.id
    AND bpr.bonus_laji_id = bl.id
  LEFT JOIN bonus_profiili_laji_esitystiedot bplet
    ON bplet.bonus_profiili_id = bp.id
    AND bplet.bonus_laji_id = bl.id
WHERE ek.poistettu IS NOT TRUE
  AND ek.laskutuskuukausi BETWEEN :alku AND :loppu
  AND (:urakka::INTEGER IS NOT NULL OR :elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus);

-- name: hae-urakkataso-sanktiolajit
-- Hakee kaikki urakan sanktiolajit ja -tyypit profiilista (myös tyhjät = nollasummat)
-- Tätä käytetään urakkataso-sanktioraportin taulukon rakenteen muodostamiseen
-- UNIQUE (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti)
-- sallii saman (laji_id, tyyppi_id)-yhdistelmän eri soveltuvuuskonteksteissa ('urakka', 'laatupoikkeama').
-- CTE laskee summat kaikista soveltuvuuskonteksteista GROUP BY(laji_id, tyyppi_id).
-- DISTINCT ON (sl.id, st.id) näyttää vain yhden rivin per laji-tyyppi-yhdistelmä.
WITH sanktio_profiili_rivit AS (
  SELECT spr_cte.sanktio_laji_id,
         spr_cte.sanktiotyyppi_id,
         SUM(s.maara) AS yhteissumma
  FROM sanktio_profiili_rivi spr_cte
    JOIN sanktio_profiili sp ON sp.id = spr_cte.sanktio_profiili_id
    JOIN sanktio_laji sl ON sl.id = spr_cte.sanktio_laji_id AND sl.aktiivinen IS TRUE
    JOIN sanktiotyyppi st ON st.id = spr_cte.sanktiotyyppi_id
    JOIN sanktio s ON s.tyyppi = spr_cte.sanktiotyyppi_id
    JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
    JOIN urakka u ON tpi.urakka = u.id
                  AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
                    OR (:urakka::INTEGER IS NULL AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
  WHERE s.poistettu IS NOT TRUE
    AND s.perintapvm BETWEEN :alku AND :loppu
    AND sp.urakkatyyppi = u.tyyppi::TEXT
    AND sp.aktiivinen IS TRUE
    AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
    AND sp.alkupvm <= u.alkupvm
    AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
  GROUP BY spr_cte.sanktio_laji_id, spr_cte.sanktiotyyppi_id
)
SELECT DISTINCT ON (sl.id, st.id)
  sl.koodi AS sanktiolaji_koodi,
  COALESCE(splet.nimi, sl.nimi) AS sanktiolaji_nimi,
  sl.jarjestys AS sanktiolaji_jarjestys,
  st.id AS sanktiotyyppi_id,
  st.nimi AS sanktiotyyppi_nimi,
  st.koodi AS sanktiotyyppi_koodi,
  COALESCE(spr_cte.yhteissumma, 0) AS summa
FROM sanktio_profiili sp
  JOIN sanktio_profiili_rivi spr
    ON spr.sanktio_profiili_id = sp.id
    AND spr.aktiivinen IS TRUE
  JOIN sanktio_laji sl
    ON sl.id = spr.sanktio_laji_id
    AND sl.aktiivinen IS TRUE
  JOIN sanktiotyyppi st
    ON st.id = spr.sanktiotyyppi_id
  LEFT JOIN sanktio_profiili_laji_esitystiedot splet
    ON splet.sanktio_profiili_id = sp.id
    AND splet.sanktio_laji_id = sl.id
  LEFT JOIN sanktio_profiili_rivit spr_cte ON spr_cte.sanktio_laji_id = sl.id AND spr_cte.sanktiotyyppi_id = st.id
  JOIN urakka u ON ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
                OR (:urakka::INTEGER IS NULL AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
WHERE sp.urakkatyyppi = u.tyyppi::TEXT
  AND sp.aktiivinen IS TRUE
  AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
  AND sp.alkupvm <= u.alkupvm
  AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
ORDER BY sl.id, st.id, sl.jarjestys, st.koodi;

-- name: hae-urakkataso-bonuslajit
-- Hakee kaikki urakan bonuslajit profiilista (myös tyhjät = nollasummat)
-- DISTINCT ON suojaa bonus_profiili_rivi-taulun mahdollisilta duplikaateilta
SELECT DISTINCT ON (bl.id)
  bl.koodi AS bonuslaji_koodi,
  COALESCE(bplet.nimi, bl.nimi) AS bonuslaji_nimi,
  bl.jarjestys AS bonuslaji_jarjestys,
  0 AS summa
FROM bonus_profiili bp
  JOIN bonus_profiili_rivi bpr
    ON bpr.bonus_profiili_id = bp.id
    AND bpr.aktiivinen IS TRUE
  JOIN bonus_laji bl
    ON bl.id = bpr.bonus_laji_id
    AND bl.aktiivinen IS TRUE
  LEFT JOIN bonus_profiili_laji_esitystiedot bplet
    ON bplet.bonus_profiili_id = bp.id
    AND bplet.bonus_laji_id = bl.id
  JOIN urakka u ON (:urakka::INTEGER IS NOT NULL AND u.id = :urakka
                OR :urakka::INTEGER IS NULL AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus))
WHERE bp.urakkatyyppi = u.tyyppi::TEXT
  AND bp.aktiivinen IS TRUE
  AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN bp.hoitovuosi_alku AND bp.hoitovuosi_loppu)
  AND bp.alkupvm <= u.alkupvm
  AND (bp.loppupvm IS NULL OR bp.loppupvm >= u.alkupvm)
ORDER BY bl.id, bl.jarjestys;
