-- name: hae-urakan-sanktio-profiilit
-- row-fn: muunna-sanktio-profiili
SELECT sp.id,
       sp.nimi,
       sp.urakkatyyppi,
       sp.hoitovuosi_alku,
       sp.hoitovuosi_loppu
  FROM sanktio_profiili sp
       JOIN urakka u
         ON u.id = :urakka_id
 WHERE sp.urakkatyyppi = u.tyyppi::TEXT
   AND sp.aktiivinen IS TRUE
   AND u.alkupvm >= sp.alkupvm
   AND (sp.loppupvm IS NULL OR u.alkupvm <= sp.loppupvm)
   AND :hoitovuosi BETWEEN sp.hoitovuosi_alku AND sp.hoitovuosi_loppu
 ORDER BY sp.alkupvm DESC,
          sp.id DESC;

-- name: hae-sanktio-profiilit-admin
-- row-fn: muunna-sanktio-profiili-admin-listarivi
SELECT sp.id,
       sp.nimi,
       sp.urakkatyyppi,
       sp.hoitovuosi_alku,
       sp.hoitovuosi_loppu,
       sp.alkupvm,
       sp.loppupvm,
       sp.aktiivinen,
       COUNT(DISTINCT spr.id)                                        AS rivimaara,
       COUNT(DISTINCT sl.id)                                         AS lajimaara,
       COALESCE(ARRAY_AGG(DISTINCT spr.soveltuvuuskonteksti)
                  FILTER (WHERE spr.soveltuvuuskonteksti IS NOT NULL),
                ARRAY[]::TEXT[])                                     AS soveltuvuuskontekstit
  FROM sanktio_profiili sp
       LEFT JOIN sanktio_profiili_rivi spr
         ON spr.sanktio_profiili_id = sp.id
        AND spr.aktiivinen IS TRUE
       LEFT JOIN sanktio_laji sl
         ON sl.id = spr.sanktio_laji_id
        AND sl.aktiivinen IS TRUE
 GROUP BY sp.id,
          sp.nimi,
          sp.urakkatyyppi,
          sp.hoitovuosi_alku,
          sp.hoitovuosi_loppu,
          sp.alkupvm,
          sp.loppupvm,
          sp.aktiivinen
 ORDER BY sp.aktiivinen DESC,
          sp.urakkatyyppi,
          sp.nimi;

-- name: hae-sanktio-profiili-admin
-- row-fn: muunna-sanktio-profiili-admin-listarivi
SELECT sp.id,
       sp.nimi,
       sp.urakkatyyppi,
       sp.hoitovuosi_alku,
       sp.hoitovuosi_loppu,
       sp.alkupvm,
       sp.loppupvm,
       sp.aktiivinen,
       COUNT(DISTINCT spr.id)                                        AS rivimaara,
       COUNT(DISTINCT sl.id)                                         AS lajimaara,
       COALESCE(ARRAY_AGG(DISTINCT spr.soveltuvuuskonteksti)
                  FILTER (WHERE spr.soveltuvuuskonteksti IS NOT NULL),
                ARRAY[]::TEXT[])                                     AS soveltuvuuskontekstit
  FROM sanktio_profiili sp
       LEFT JOIN sanktio_profiili_rivi spr
         ON spr.sanktio_profiili_id = sp.id
        AND spr.aktiivinen IS TRUE
       LEFT JOIN sanktio_laji sl
         ON sl.id = spr.sanktio_laji_id
        AND sl.aktiivinen IS TRUE
 WHERE sp.id = :sanktio_profiili_id
 GROUP BY sp.id,
          sp.nimi,
          sp.urakkatyyppi,
          sp.hoitovuosi_alku,
          sp.hoitovuosi_loppu,
          sp.alkupvm,
          sp.loppupvm,
          sp.aktiivinen;

-- name: hae-bonus-profiilit-admin
-- row-fn: muunna-bonus-profiili-admin-listarivi
SELECT bp.id,
       bp.nimi,
       bp.urakkatyyppi,
       bp.hoitovuosi_alku,
       bp.hoitovuosi_loppu,
       bp.alkupvm,
       bp.loppupvm,
       bp.aktiivinen,
       COUNT(DISTINCT bpr.id)                                        AS rivimaara,
       COUNT(DISTINCT bl.id)                                         AS lajimaara
  FROM bonus_profiili bp
       LEFT JOIN bonus_profiili_rivi bpr
         ON bpr.bonus_profiili_id = bp.id
        AND bpr.aktiivinen IS TRUE
       LEFT JOIN bonus_laji bl
         ON bl.id = bpr.bonus_laji_id
        AND bl.aktiivinen IS TRUE
 GROUP BY bp.id,
          bp.nimi,
          bp.urakkatyyppi,
          bp.hoitovuosi_alku,
          bp.hoitovuosi_loppu,
          bp.alkupvm,
          bp.loppupvm,
          bp.aktiivinen
 ORDER BY bp.aktiivinen DESC,
          bp.urakkatyyppi,
          bp.nimi;

-- name: hae-bonus-profiili-admin
-- row-fn: muunna-bonus-profiili-admin-listarivi
SELECT bp.id,
       bp.nimi,
       bp.urakkatyyppi,
       bp.hoitovuosi_alku,
       bp.hoitovuosi_loppu,
       bp.alkupvm,
       bp.loppupvm,
       bp.aktiivinen,
       COUNT(DISTINCT bpr.id)                                        AS rivimaara,
       COUNT(DISTINCT bl.id)                                         AS lajimaara
  FROM bonus_profiili bp
       LEFT JOIN bonus_profiili_rivi bpr
         ON bpr.bonus_profiili_id = bp.id
        AND bpr.aktiivinen IS TRUE
       LEFT JOIN bonus_laji bl
         ON bl.id = bpr.bonus_laji_id
        AND bl.aktiivinen IS TRUE
 WHERE bp.id = :bonus_profiili_id
 GROUP BY bp.id,
          bp.nimi,
          bp.urakkatyyppi,
          bp.hoitovuosi_alku,
          bp.hoitovuosi_loppu,
          bp.alkupvm,
          bp.loppupvm,
          bp.aktiivinen;

-- name: hae-bonus-profiilin-rivit-admin
-- row-fn: muunna-bonus-konfiguraatiorivi
SELECT bp.id                    AS profiili_id,
       bp.nimi                  AS profiili_nimi,
       bp.urakkatyyppi          AS profiili_urakkatyyppi,
       bp.hoitovuosi_alku       AS profiili_hoitovuosi_alku,
       bp.hoitovuosi_loppu      AS profiili_hoitovuosi_loppu,
       bp.alkupvm               AS profiili_alkupvm,
       bp.loppupvm              AS profiili_loppupvm,
       bp.aktiivinen            AS profiili_aktiivinen,
       bl.id                    AS laji_id,
       bl.koodi                 AS laji_koodi,
       bl.nimi                  AS laji_nimi,
       bplet.nimi               AS laji_esitystiedot_nimi,
       bplet.kuvaus             AS laji_esitystiedot_kuvaus,
       bl.jarjestys             AS laji_jarjestys,
       bl.kirjaustapa           AS laji_kirjaustapa,
       bl.automaattinen         AS laji_automaattinen,
       bpr.id                   AS profiilirivi_id,
       bpr.jarjestys            AS profiilirivi_jarjestys,
       bpr.toimenpideinstanssi_rajauksen_tyyppi AS profiilirivi_toimenpideinstanssi_rajauksen_tyyppi,
       bpr.toimenpideinstanssi_t2_koodi AS profiilirivi_toimenpideinstanssi_t2_koodi,
       COUNT(DISTINCT bpru.urakka_id)  AS profiilirivi_urakkarajausten_maara,
       ARRAY_REMOVE(ARRAY_AGG(DISTINCT COALESCE(u.lyhyt_nimi, u.nimi)), NULL) AS profiilirivi_urakat
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
       LEFT JOIN bonus_profiili_rivi_urakka bpru
         ON bpru.bonus_profiili_rivi_id = bpr.id
       LEFT JOIN urakka u
         ON u.id = bpru.urakka_id
 WHERE bp.id = :bonus_profiili_id
 GROUP BY bp.id,
          bp.nimi,
          bp.urakkatyyppi,
          bp.hoitovuosi_alku,
          bp.hoitovuosi_loppu,
          bp.alkupvm,
          bp.loppupvm,
          bp.aktiivinen,
          bl.id,
          bl.koodi,
          bl.nimi,
          bplet.nimi,
          bplet.kuvaus,
          bl.jarjestys,
          bl.kirjaustapa,
          bl.automaattinen,
          bpr.id,
          bpr.jarjestys,
          bpr.toimenpideinstanssi_rajauksen_tyyppi,
          bpr.toimenpideinstanssi_t2_koodi
 ORDER BY bl.jarjestys,
          bpr.jarjestys,
          bpr.toimenpideinstanssi_rajauksen_tyyppi,
          bpr.toimenpideinstanssi_t2_koodi;

-- name: hae-sanktio-profiilin-rivit
-- row-fn: muunna-sanktio-konfiguraatiorivi
SELECT sp.id                    AS profiili_id,
       sp.nimi                  AS profiili_nimi,
       sp.urakkatyyppi          AS profiili_urakkatyyppi,
       sp.hoitovuosi_alku       AS profiili_hoitovuosi_alku,
       sp.hoitovuosi_loppu      AS profiili_hoitovuosi_loppu,
       spr.soveltuvuuskonteksti AS soveltuvuuskonteksti,
       sl.id                    AS laji_id,
       sl.koodi                 AS laji_koodi,
       sl.nimi                  AS laji_nimi,
       splet.nimi               AS laji_esitystiedot_nimi,
       splet.kuvaus             AS laji_esitystiedot_kuvaus,
       sl.jarjestys             AS laji_jarjestys,
       spr.id                   AS profiilirivi_id,
       spr.jarjestys            AS profiilirivi_jarjestys,
       spr.voi_puolittaa_omailmoituksella AS profiilirivi_voi_puolittaa_omailmoituksella,
       COALESCE((SELECT ARRAY_AGG(sprls.summa_euroina ORDER BY sprls.jarjestys)
                   FROM sanktio_profiili_rivi_lukittu_summa sprls
                  WHERE sprls.sanktio_profiili_rivi_id = spr.id),
                ARRAY[]::NUMERIC[]) AS profiilirivi_lukitut_summat,
       st.id                    AS sanktiotyyppi_id,
       st.koodi                 AS sanktiotyyppi_koodi,
       st.nimi                  AS sanktiotyyppi_nimi,
       st.toimenpidekoodi       AS sanktiotyyppi_toimenpidekoodi
  FROM sanktio_profiili sp
       JOIN sanktio_profiili_rivi spr
         ON spr.sanktio_profiili_id = sp.id
        AND spr.aktiivinen IS TRUE
       JOIN sanktio_laji sl
         ON sl.id = spr.sanktio_laji_id
        AND sl.aktiivinen IS TRUE
       LEFT JOIN sanktio_profiili_laji_esitystiedot splet
         ON splet.sanktio_profiili_id = sp.id
        AND splet.sanktio_laji_id = sl.id
       JOIN sanktiotyyppi st
         ON st.id = spr.sanktiotyyppi_id
 WHERE sp.id = :sanktio_profiili_id
   AND spr.soveltuvuuskonteksti = :soveltuvuuskonteksti
 ORDER BY sl.jarjestys,
          spr.jarjestys,
          st.koodi;

-- name: hae-sanktio-profiilin-rivit-admin
-- row-fn: muunna-sanktio-konfiguraatiorivi
SELECT sp.id                    AS profiili_id,
       sp.nimi                  AS profiili_nimi,
       sp.urakkatyyppi          AS profiili_urakkatyyppi,
       sp.hoitovuosi_alku       AS profiili_hoitovuosi_alku,
       sp.hoitovuosi_loppu      AS profiili_hoitovuosi_loppu,
       sp.alkupvm               AS profiili_alkupvm,
       sp.loppupvm              AS profiili_loppupvm,
       sp.aktiivinen            AS profiili_aktiivinen,
       spr.soveltuvuuskonteksti AS soveltuvuuskonteksti,
       sl.id                    AS laji_id,
       sl.koodi                 AS laji_koodi,
       sl.nimi                  AS laji_nimi,
       splet.nimi               AS laji_esitystiedot_nimi,
       splet.kuvaus             AS laji_esitystiedot_kuvaus,
       sl.jarjestys             AS laji_jarjestys,
       spr.id                   AS profiilirivi_id,
       spr.jarjestys            AS profiilirivi_jarjestys,
       spr.voi_puolittaa_omailmoituksella AS profiilirivi_voi_puolittaa_omailmoituksella,
       COALESCE((SELECT ARRAY_AGG(sprls.summa_euroina ORDER BY sprls.jarjestys)
                   FROM sanktio_profiili_rivi_lukittu_summa sprls
                  WHERE sprls.sanktio_profiili_rivi_id = spr.id),
                ARRAY[]::NUMERIC[]) AS profiilirivi_lukitut_summat,
       st.id                    AS sanktiotyyppi_id,
       st.koodi                 AS sanktiotyyppi_koodi,
       st.nimi                  AS sanktiotyyppi_nimi,
       st.toimenpidekoodi       AS sanktiotyyppi_toimenpidekoodi
  FROM sanktio_profiili sp
       JOIN sanktio_profiili_rivi spr
         ON spr.sanktio_profiili_id = sp.id
        AND spr.aktiivinen IS TRUE
       JOIN sanktio_laji sl
         ON sl.id = spr.sanktio_laji_id
        AND sl.aktiivinen IS TRUE
       LEFT JOIN sanktio_profiili_laji_esitystiedot splet
         ON splet.sanktio_profiili_id = sp.id
        AND splet.sanktio_laji_id = sl.id
       JOIN sanktiotyyppi st
         ON st.id = spr.sanktiotyyppi_id
 WHERE sp.id = :sanktio_profiili_id
 ORDER BY spr.soveltuvuuskonteksti,
          sl.jarjestys,
          spr.jarjestys,
          st.koodi;
