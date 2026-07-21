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

-- name: hae-urakan-bonus-profiilit
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
       JOIN urakka u
         ON u.id = :urakka_id
       LEFT JOIN bonus_profiili_rivi bpr
         ON bpr.bonus_profiili_id = bp.id
        AND bpr.aktiivinen IS TRUE
       LEFT JOIN bonus_laji bl
         ON bl.id = bpr.bonus_laji_id
        AND bl.aktiivinen IS TRUE
 WHERE bp.urakkatyyppi = u.tyyppi::TEXT
   AND bp.aktiivinen IS TRUE
   AND u.alkupvm >= bp.alkupvm
   AND (bp.loppupvm IS NULL OR u.alkupvm <= bp.loppupvm)
   AND :hoitovuosi BETWEEN bp.hoitovuosi_alku AND bp.hoitovuosi_loppu
 GROUP BY bp.id,
          bp.nimi,
          bp.urakkatyyppi,
          bp.hoitovuosi_alku,
          bp.hoitovuosi_loppu,
          bp.alkupvm,
          bp.loppupvm,
          bp.aktiivinen
 ORDER BY bp.alkupvm DESC,
          bp.id DESC;

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

-- name: hae-bonus-profiilin-rivit
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
       bpr.toimenpiderajauksen_tyyppi AS profiilirivi_toimenpiderajauksen_tyyppi,
       bpr.toimenpide_t2_koodi AS profiilirivi_toimenpide_t2_koodi
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
       LEFT JOIN toimenpideinstanssi tpi
         ON tpi.id = :toimenpideinstanssi_id
        AND tpi.urakka = :urakka_id
       LEFT JOIN toimenpide t3
         ON t3.id = tpi.toimenpide
       LEFT JOIN toimenpide t2
         ON t2.id = t3.emo
 WHERE bp.id = :bonus_profiili_id
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
             AND bpru.urakka_id = :urakka_id))
 ORDER BY bl.jarjestys,
          bpr.jarjestys,
          bpr.id;

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
       bpr.toimenpiderajauksen_tyyppi AS profiilirivi_toimenpiderajauksen_tyyppi,
       bpr.toimenpide_t2_koodi AS profiilirivi_toimenpide_t2_koodi,
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
          bpr.toimenpiderajauksen_tyyppi,
          bpr.toimenpide_t2_koodi
 ORDER BY bl.jarjestys,
          bpr.jarjestys,
          bpr.toimenpiderajauksen_tyyppi,
          bpr.toimenpide_t2_koodi;
