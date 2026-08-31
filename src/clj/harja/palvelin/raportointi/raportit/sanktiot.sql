-- name: hae-sanktiot
-- Hakee sanktiot
SELECT
  s.id,
  s.sakkoryhma,
  s.maara AS summa,
  s.indeksi,
  suorasanktio,
  st.id                   AS sanktiotyyppi_id,
  st.nimi                 AS sanktiotyyppi_nimi,
  tpi.id                  AS toimenpideinstanssi_id,
  tpi.nimi                AS toimenpideinstanssi_nimi,
  tpk2.koodi              AS toimenpide_koodi,
  u.id                    AS "urakka-id",
  u.nimi                  AS nimi,
  u.alkupvm               AS urakan_alkupvm,
  u.loppupvm              AS loppupvm,
  o.id                    AS elinvoimakeskus_id,
  o.nimi                  AS elinvoimakeskus_nimi,
  o.lyhenne              AS elinvoimakeskus_lyhenne,
  tpk2.nimi      AS toimenpidekoodi_taso2,
  (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, u.id, s.sakkoryhma)) AS indeksikorotus
FROM urakka u
     JOIN toimenpideinstanssi tpi ON tpi.urakka = u.id
    JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
            AND o.tyyppi = 'elinvoimakeskus'
     LEFT JOIN sanktio s on tpi.id = s.toimenpideinstanssi
                            AND s.poistettu IS NOT TRUE
                            -- jos hakurange sisältää urakan viimeisen kuukauden, mahdolliset urakan päättymisen jälkeen tulleet sanktiot sisällytetään siihen
                            AND (s.perintapvm BETWEEN :alku::DATE AND :loppu::DATE OR
                                 (date_trunc('month', :loppu::DATE) = date_trunc('month', u.loppupvm)
                                  AND s.perintapvm > u.loppupvm))
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
  u.alkupvm      AS urakan_alkupvm,
  u.loppupvm     AS loppupvm,
  o.id           AS elinvoimakeskus_id,
  o.nimi         AS elinvoimakeskus_nimi,
  o.lyhenne                  AS elinvoimakeskus_lyhenne,
  t2.nimi AS toimenpidekoodi_taso2
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  LEFT JOIN toimenpide t3 ON t3.id = tpi.toimenpide
  LEFT JOIN toimenpide t2 ON t2.id = t3.emo
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id AND lp.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
  JOIN urakka u ON (tpi.urakka = u.id OR lp.urakka = u.id) AND u.alkupvm < :loppu AND u.loppupvm > :alku
  JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
                      AND o.tyyppi = 'elinvoimakeskus'
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
      AND (s.perintapvm BETWEEN :alku AND :loppu OR
           (date_trunc('month', :loppu::DATE) = date_trunc('month', u.loppupvm)
        AND s.perintapvm > u.loppupvm))
    -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
          OR
          lp.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE)
ORDER BY yllapitoluokka;

-- name: hae-urakkataso-yllapito-sanktiot
-- Hakee ylläpidon sanktiot profiilin ja tapahtuman soveltuvuuskontekstin perusteella.
SELECT
  s.id AS sanktio_id,
  s.sakkoryhma,
  -s.maara AS summa,
  s.indeksi,
  s.suorasanktio,
  sl.koodi AS sanktiolaji_koodi,
  COALESCE(splet.nimi, sl.nimi) AS sanktiolaji_nimi,
  st.id AS sanktiotyyppi_id,
  st.koodi AS sanktiotyyppi_koodi,
  st.nimi AS sanktiotyyppi_nimi,
  tpi.id AS toimenpideinstanssi_id,
  u.id AS urakka_id,
  u.nimi AS urakan_nimi,
  u.alkupvm AS urakan_alkupvm,
  u.loppupvm AS urakan_loppupvm,
  o.id AS elinvoimakeskus_id,
  o.nimi AS elinvoimakeskus_nimi,
  o.lyhenne AS elinvoimakeskus_lyhenne,
  ypk.yllapitoluokka AS yllapitoluokka,
  (CASE WHEN s.laatupoikkeama IS NULL THEN 'urakka' ELSE 'laatupoikkeama' END) AS soveltuvuuskonteksti,
  t2.nimi AS toimenpidekoodi_taso2
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  LEFT JOIN toimenpide t3 ON t3.id = tpi.toimenpide
  LEFT JOIN toimenpide t2 ON t2.id = t3.emo
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id AND lp.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
  JOIN urakka u ON u.id = COALESCE(tpi.urakka, lp.urakka)
  JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
                      AND o.tyyppi = 'elinvoimakeskus'
  JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN LATERAL (
    SELECT sp.*
    FROM sanktio_profiili sp
    WHERE sp.urakkatyyppi = u.tyyppi::TEXT
      AND sp.aktiivinen IS TRUE
      AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
      AND sp.alkupvm <= u.alkupvm
      AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
    ORDER BY sp.alkupvm DESC, sp.id DESC
    LIMIT 1
  ) sp ON TRUE
  LEFT JOIN sanktio_profiili_rivi spr
    ON spr.sanktio_profiili_id = sp.id
    AND spr.sanktiotyyppi_id = s.tyyppi
    AND spr.soveltuvuuskonteksti = CASE WHEN s.laatupoikkeama IS NULL THEN 'urakka' ELSE 'laatupoikkeama' END
    AND spr.aktiivinen IS TRUE
  LEFT JOIN sanktio_laji sl ON sl.id = spr.sanktio_laji_id AND sl.aktiivinen IS TRUE
  LEFT JOIN sanktio_profiili_laji_esitystiedot splet
    ON splet.sanktio_profiili_id = sp.id
    AND splet.sanktio_laji_id = sl.id
WHERE s.poistettu IS NOT TRUE
  AND s.sakkoryhma != 'yllapidon_bonus'::SANKTIOLAJI
  -- Jos hakuväli sisältää urakan viimeisen kuukauden, mukaan otetaan myös
  -- urakan päättymisen jälkeen perityt sanktiot.
  AND (s.perintapvm BETWEEN :alku AND :loppu OR
       (date_trunc('month', :loppu::DATE) = date_trunc('month', u.loppupvm)
        AND s.perintapvm > u.loppupvm))
  AND u.alkupvm < :loppu::DATE
  AND u.loppupvm > :alku::DATE
  AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
    OR (:urakka::INTEGER IS NULL
      AND u.urakkanro IS NOT NULL
      AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
  AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
    OR u.tyyppi = :urakkatyyppi::urakkatyyppi)
  AND (lp.yllapitokohde IS NULL OR ypk.id IS NOT NULL)
ORDER BY ypk.yllapitoluokka NULLS LAST, s.perintapvm, s.id;

-- name: hae-urakkataso-yllapito-bonukset
-- Hakee ylläpidon legacy-bonukset erillisenä bonusdatana.
SELECT
  s.id AS bonus_id,
  'yllapidon_bonus' AS bonuslaji_koodi,
  'Bonus' AS bonuslaji_nimi,
  -s.maara AS summa,
  s.indeksi,
  s.suorasanktio,
  tpi.id AS toimenpideinstanssi_id,
  u.id AS urakka_id,
  u.nimi AS urakan_nimi,
  u.alkupvm AS urakan_alkupvm,
  u.loppupvm AS urakan_loppupvm,
  o.id AS elinvoimakeskus_id,
  o.nimi AS elinvoimakeskus_nimi,
  o.lyhenne AS elinvoimakeskus_lyhenne,
  ypk.yllapitoluokka AS yllapitoluokka
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id AND lp.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
  JOIN urakka u ON u.id = COALESCE(tpi.urakka, lp.urakka)
  JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
                      AND o.tyyppi = 'elinvoimakeskus'
WHERE s.poistettu IS NOT TRUE
  AND s.sakkoryhma = 'yllapidon_bonus'::SANKTIOLAJI
  -- Sama päättymiskuukauden rajaus kuin vanhassa ylläpitoraportissa.
  AND (s.perintapvm BETWEEN :alku AND :loppu OR
       (date_trunc('month', :loppu::DATE) = date_trunc('month', u.loppupvm)
        AND s.perintapvm > u.loppupvm))
  AND u.alkupvm < :loppu::DATE
  AND u.loppupvm > :alku::DATE
  AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
    OR (:urakka::INTEGER IS NULL
      AND u.urakkanro IS NOT NULL
      AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
  AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
    OR u.tyyppi = :urakkatyyppi::urakkatyyppi)
  AND (lp.yllapitokohde IS NULL OR ypk.id IS NOT NULL)
ORDER BY ypk.yllapitoluokka NULLS LAST, s.perintapvm, s.id;


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
                                                 u.tyyppi = 'teiden-hoito'::urakkatyyppi)) AS indeksikorotus,
       o.id           AS elinvoimakeskus_id,
       o.nimi         AS elinvoimakeskus_nimi,
      o.lyhenne                  AS elinvoimakeskus_lyhenne
FROM erilliskustannus ek
         JOIN urakka u ON ek.urakka = u.id
         JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
                              AND o.tyyppi = 'elinvoimakeskus'
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
  s.sakkoryhma,
  CASE WHEN spr.id IS NOT NULL THEN sl.koodi END AS sanktiolaji_koodi,
  CASE WHEN spr.id IS NOT NULL THEN COALESCE(splet.nimi, sl.nimi) END AS sanktiolaji_nimi,
  st.id AS sanktiotyyppi_id,
  st.nimi AS sanktiotyyppi_nimi,
  st.koodi AS sanktiotyyppi_koodi,
  s.maara AS summa,
  s.indeksi AS indeksi,
  s.suorasanktio,
  (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi, s.maara, u.id, s.sakkoryhma)) AS indeksikorotus,
  u.id AS urakka_id,
  u.nimi AS urakan_nimi,
  u.alkupvm AS urakan_alkupvm,
  u.loppupvm AS urakan_loppupvm,
  o.lyhenne AS elinvoimakeskus_lyhenne,
  (CASE WHEN s.laatupoikkeama IS NULL THEN 'urakka' ELSE 'laatupoikkeama' END) AS soveltuvuuskonteksti
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
  LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
  JOIN urakka u ON u.id = CASE WHEN s.laatupoikkeama IS NULL THEN tpi.urakka ELSE lp.urakka END
  JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
                      AND o.tyyppi = 'elinvoimakeskus'
  LEFT JOIN sanktiotyyppi st ON s.tyyppi = st.id
  LEFT JOIN LATERAL (
    SELECT sp.*
    FROM sanktio_profiili sp
    WHERE sp.urakkatyyppi = u.tyyppi::TEXT
      AND sp.aktiivinen IS TRUE
      AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
      AND sp.alkupvm <= u.alkupvm
      AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
    ORDER BY sp.alkupvm DESC, sp.id DESC
    LIMIT 1
  ) sp ON TRUE
  LEFT JOIN sanktio_laji sl
    ON sl.koodi = s.sakkoryhma::TEXT
    AND sl.aktiivinen IS TRUE
  LEFT JOIN sanktio_profiili_rivi spr
    ON spr.sanktio_profiili_id = sp.id
    AND spr.sanktio_laji_id = sl.id
    AND spr.sanktiotyyppi_id = s.tyyppi
    AND spr.soveltuvuuskonteksti = CASE WHEN s.laatupoikkeama IS NULL THEN 'urakka' ELSE 'laatupoikkeama' END
    AND spr.aktiivinen IS TRUE
  LEFT JOIN sanktio_profiili_laji_esitystiedot splet
    ON splet.sanktio_profiili_id = sp.id
    AND splet.sanktio_laji_id = sl.id
WHERE s.poistettu IS NOT TRUE
  AND s.sakkoryhma != 'yllapidon_bonus'::SANKTIOLAJI
  AND s.perintapvm BETWEEN :alku AND :loppu
  AND u.alkupvm < :loppu::DATE
  AND u.loppupvm > :alku::DATE
  AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
    OR (:urakka::INTEGER IS NULL
      AND u.urakkanro IS NOT NULL
      AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
  AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
    OR CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
            ELSE u.tyyppi = :urakkatyyppi::urakkatyyppi END)
  AND (s.laatupoikkeama IS NULL
    OR (lp.poistettu IS NOT TRUE AND (lp.yllapitokohde IS NULL OR ypk.id IS NOT NULL)));

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
  u.nimi AS urakan_nimi,
  u.alkupvm AS urakan_alkupvm,
  u.loppupvm AS urakan_loppupvm,
  o.lyhenne AS elinvoimakeskus_lyhenne
FROM erilliskustannus ek
  JOIN urakka u ON ek.urakka = u.id
  JOIN toimenpideinstanssi tpi ON tpi.id = ek.toimenpideinstanssi
                              AND tpi.urakka = u.id
  JOIN toimenpide t3 ON t3.id = tpi.toimenpide
  LEFT JOIN toimenpide t2 ON t2.id = t3.emo
  JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
                      AND o.tyyppi = 'elinvoimakeskus'
  LEFT JOIN LATERAL (
    SELECT bp.*
    FROM bonus_profiili bp
    WHERE bp.urakkatyyppi = u.tyyppi::TEXT
      AND bp.aktiivinen IS TRUE
      AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN bp.hoitovuosi_alku AND bp.hoitovuosi_loppu)
      AND bp.alkupvm <= u.alkupvm
      AND (bp.loppupvm IS NULL OR bp.loppupvm >= u.alkupvm)
    ORDER BY bp.alkupvm DESC, bp.id DESC
    LIMIT 1
  ) bp ON TRUE
  JOIN bonus_laji bl ON bl.koodi = ek.tyyppi::TEXT
  LEFT JOIN bonus_profiili_laji_esitystiedot bplet
    ON bplet.bonus_profiili_id = bp.id
    AND bplet.bonus_laji_id = bl.id
WHERE ek.poistettu IS NOT TRUE
  AND ek.laskutuskuukausi BETWEEN :alku AND :loppu
  AND EXISTS (
        SELECT 1
          FROM bonus_profiili_rivi bpr
         WHERE bpr.bonus_profiili_id = bp.id
           AND bpr.bonus_laji_id = bl.id
           AND bpr.aktiivinen IS TRUE
           AND (bpr.toimenpiderajauksen_tyyppi = 'kaikki'
             OR (bpr.toimenpiderajauksen_tyyppi = 't2-koodi'
               AND bpr.toimenpide_t2_koodi = t2.koodi))
           AND (NOT EXISTS (
                  SELECT 1
                    FROM bonus_profiili_rivi_urakka bpru
                   WHERE bpru.bonus_profiili_rivi_id = bpr.id)
             OR EXISTS (
                  SELECT 1
                    FROM bonus_profiili_rivi_urakka bpru
                   WHERE bpru.bonus_profiili_rivi_id = bpr.id
                     AND bpru.urakka_id = u.id)))
  AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
    OR (:urakka::INTEGER IS NULL
      AND u.urakkanro IS NOT NULL
      AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
  AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
    OR CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
            ELSE u.tyyppi = :urakkatyyppi::urakkatyyppi END);

-- name: hae-urakkataso-sanktiolajit
-- Hakee kaikki urakan sanktiolajit ja -tyypit profiilista (myös tyhjät = nollasummat)
-- Tätä käytetään urakkataso-sanktioraportin taulukon rakenteen muodostamiseen
-- UNIQUE (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti)
-- sallii saman (laji_id, tyyppi_id)-yhdistelmän eri soveltuvuuskonteksteissa ('urakka', 'laatupoikkeama').
-- CTE laskee summat valitulle urakalle ja profiilille.
WITH sanktio_profiili_rivit AS (
  SELECT spr.sanktio_profiili_id,
         spr.sanktio_laji_id,
         spr.sanktiotyyppi_id,
         SUM(s.maara) AS yhteissumma
  FROM sanktio_profiili_rivi spr
    JOIN sanktio_profiili sp ON sp.id = spr.sanktio_profiili_id
    JOIN sanktio_laji sl ON sl.id = spr.sanktio_laji_id AND sl.aktiivinen IS TRUE
    JOIN sanktiotyyppi st ON st.id = spr.sanktiotyyppi_id
    JOIN sanktio s ON s.tyyppi = spr.sanktiotyyppi_id
    LEFT JOIN toimenpideinstanssi tpi ON tpi.id = s.toimenpideinstanssi
    LEFT JOIN laatupoikkeama lp
      ON lp.id = s.laatupoikkeama
      AND lp.poistettu IS NOT TRUE
    LEFT JOIN yllapitokohde ypk
      ON ypk.id = lp.yllapitokohde
      AND ypk.poistettu IS NOT TRUE
    JOIN urakka u ON u.id = CASE WHEN s.laatupoikkeama IS NULL THEN tpi.urakka ELSE lp.urakka END
  WHERE s.poistettu IS NOT TRUE
    AND s.sakkoryhma != 'yllapidon_bonus'::SANKTIOLAJI
    AND s.perintapvm BETWEEN :alku AND :loppu
    AND sp.aktiivinen IS TRUE
    AND sp.urakkatyyppi = u.tyyppi::TEXT
    AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
    AND sp.alkupvm <= u.alkupvm
    AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
    AND u.alkupvm < :loppu::DATE
    AND u.loppupvm > :alku::DATE
    AND spr.aktiivinen IS TRUE
    AND spr.soveltuvuuskonteksti = CASE WHEN s.laatupoikkeama IS NULL THEN 'urakka' ELSE 'laatupoikkeama' END
    AND s.sakkoryhma::TEXT = sl.koodi
    AND ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
      OR (:urakka::INTEGER IS NULL
        AND u.urakkanro IS NOT NULL
        AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
    AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
      OR CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
              ELSE u.tyyppi = :urakkatyyppi::urakkatyyppi END)
    AND (s.laatupoikkeama IS NULL
      OR lp.yllapitokohde IS NULL
      OR ypk.id IS NOT NULL)
  GROUP BY spr.sanktio_profiili_id, spr.sanktio_laji_id, spr.sanktiotyyppi_id
)
SELECT DISTINCT ON (sl.id, st.id)
  sl.koodi AS sanktiolaji_koodi,
  COALESCE(splet.nimi, sl.nimi) AS sanktiolaji_nimi,
  sl.jarjestys AS sanktiolaji_jarjestys,
  st.id AS sanktiotyyppi_id,
  st.nimi AS sanktiotyyppi_nimi,
  st.koodi AS sanktiotyyppi_koodi,
  COALESCE(spr_summa.yhteissumma, 0) AS summa
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
  LEFT JOIN sanktio_profiili_rivit spr_summa
    ON spr_summa.sanktio_profiili_id = sp.id
    AND spr_summa.sanktio_laji_id = sl.id
    AND spr_summa.sanktiotyyppi_id = st.id
  JOIN urakka u ON ((:urakka::INTEGER IS NOT NULL AND u.id = :urakka)
                OR (:urakka::INTEGER IS NULL AND (:elinvoimakeskus::INTEGER IS NULL OR u.elinvoimakeskus_id = :elinvoimakeskus)))
WHERE sp.urakkatyyppi = u.tyyppi::TEXT
  AND sp.aktiivinen IS TRUE
  AND (:hoitovuosi::INTEGER IS NULL OR :hoitovuosi::INTEGER BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu)
  AND sp.alkupvm <= u.alkupvm
  AND (sp.loppupvm IS NULL OR sp.loppupvm >= u.alkupvm)
  AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
    OR CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
            ELSE u.tyyppi = :urakkatyyppi::urakkatyyppi END)
GROUP BY sl.id, sl.koodi, sl.nimi, sl.jarjestys,
         st.id, st.nimi, st.koodi,
         splet.nimi, spr_summa.yhteissumma,
         sp.id, sp.alkupvm
ORDER BY sl.id, st.id, sp.alkupvm DESC, sp.id DESC, sl.jarjestys, st.koodi;

-- name: hae-urakkataso-bonuslajit
-- Hakee kaikki urakan bonuslajit profiilista (myös tyhjät = nollasummat)
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
  AND (:urakka::INTEGER IS NOT NULL OR :urakkatyyppi::urakkatyyppi IS NULL
    OR CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
            ELSE u.tyyppi = :urakkatyyppi::urakkatyyppi END)
ORDER BY bl.id, bp.alkupvm DESC, bp.id DESC, bl.jarjestys;
