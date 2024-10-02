-- name: hae-urakat-kojelautaan
SELECT u.id,
       u.nimi,
       :hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       urakan_kustannussuunnitelman_tila(u.id::INTEGER,
                                         monesko_hoitokausi(u.alkupvm, u.loppupvm, :hoitokauden_alkuvuosi::INTEGER)) as ks_tila
FROM urakka u
WHERE u.tyyppi = 'teiden-hoito' AND
      (:urakat_annettu IS NOT TRUE OR u.id IN (:urakka_idt));
