-- name: hae-urakat-kojelautaan
SELECT u.id,
       u.nimi,
       u.hallintayksikko as ely_id,
       :hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       urakan_kustannussuunnitelman_tila(u.id::INTEGER,
                                         monesko_hoitokausi(u.alkupvm, u.loppupvm, :hoitokauden_alkuvuosi::INTEGER)) as ks_tila
  FROM urakka u
           join organisaatio o ON u.hallintayksikko = o.id
 WHERE
     u.tyyppi = 'teiden-hoito' AND
     u.urakkanro IS NOT NULL AND -- testiurakat pois
     (:hoitokauden_alkuvuosi BETWEEN
         EXTRACT (YEAR FROM u.alkupvm) AND
         EXTRACT (YEAR FROM u.loppupvm) - 1) AND
     (:urakat_annettu IS NOT TRUE OR u.id IN (:urakka_idt)) AND
     (:ely_id::INTEGER IS NULL OR u.hallintayksikko = :ely_id)
 ORDER BY u.nimi;

