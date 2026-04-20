-- name: hae-urakan-sanktio-profiilit
-- row-fn: muunna-sanktio-profiili
WITH valittu_urakka AS (
    SELECT u.id,
           u.tyyppi::TEXT AS urakkatyyppi,
           u.alkupvm
      FROM urakka u
     WHERE u.id = :urakka_id
)
SELECT sp.id,
       sp.nimi,
       sp.urakkatyyppi,
       sp.hoitovuosi_alku,
       sp.hoitovuosi_loppu
  FROM sanktio_profiili sp
       JOIN valittu_urakka vu
         ON vu.urakkatyyppi = sp.urakkatyyppi
 WHERE sp.aktiivinen IS TRUE
   AND vu.alkupvm >= sp.alkupvm
   AND (sp.loppupvm IS NULL OR vu.alkupvm <= sp.loppupvm)
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
       sl.jarjestys             AS laji_jarjestys,
       spr.id                   AS profiilirivi_id,
       spr.jarjestys            AS profiilirivi_jarjestys,
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
       sl.jarjestys             AS laji_jarjestys,
       spr.id                   AS profiilirivi_id,
       spr.jarjestys            AS profiilirivi_jarjestys,
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
       JOIN sanktiotyyppi st
         ON st.id = spr.sanktiotyyppi_id
 WHERE sp.id = :sanktio_profiili_id
 ORDER BY spr.soveltuvuuskonteksti,
          sl.jarjestys,
          spr.jarjestys,
          st.koodi;
