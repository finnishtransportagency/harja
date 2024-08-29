-- name: hae-urakat-kojelautaan
SELECT u.id,
       u.nimi,
       hoitovuoden_alkuvuosi AS hoitovuosi_alkuvuosi,
       (select urakan_kustannussuunnitelman_tila(u.id, hoitovuoden_alkuvuosi)) as "hoitovuosi_kustannussuunnitelma-ok?",
       (select urakan_tehtavamaarien_suunnittelun_tila(u.id, hoitovuoden_alkuvuosi)) as "hoitovuosi_tehtavamaarat-ok?",
       (select urakan_rajoitusalueiden_suunnittelun_tila(u.id, hoitovuoden_alkuvuosi)) as "hoitovuosi_rajoitusalueet-ok?"
FROM urakka u
         JOIN LATERAL GENERATE_SERIES(EXTRACT(YEAR FROM u.alkupvm)::INTEGER,
                                      (EXTRACT(YEAR FROM u.loppupvm)::INTEGER - 1)) AS hoitovuoden_alkuvuosi ON TRUE
WHERE u.tyyppi='teiden-hoito';
