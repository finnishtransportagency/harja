-- name: hae-hoidon-urakat-kojelautaan
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

-- name: hae-paallystysurakat-kojelautaan
SELECT u.id,
       u.nimi,
       u.hallintayksikko as ely_id,
       :vuosi as hoitokauden_alkuvuosi,
       (SELECT count(*) FROM yllapitokohde y
                        WHERE y.urakka = u.id AND
                              y.vuodet @> ARRAY[:vuosi]::INTEGER[] AND
                          -- y.yhaid IS NOT NULL --> kohde on YHA:sta Harjaan haettu päällystyskohde
                            (y.yhaid IS NOT NULL OR
                                -- y.yhaid IS NULL AND paikkauskohde.pot? = TRUE --> kohde on Harjassa luotu paikkauskohde, jolle on merkitty että tehdään päällystysilmoitus (POT)
                             (y.yhaid IS NULL AND EXISTS (SELECT id FROM paikkauskohde where "pot?" = true and "yllapitokohde-id" = y.id)))) as yllapitokohteiden_lkm
  FROM urakka u
           join organisaatio o ON u.hallintayksikko = o.id

 WHERE
     u.tyyppi = 'paallystys' AND
     u.urakkanro IS NOT NULL AND -- testiurakat pois
     -- oltava vähintään yksi ylläpitokohde jolle tehdään pot-lomake
     (SELECT count(*) FROM yllapitokohde y
       WHERE y.urakka = u.id AND
           y.vuodet @> ARRAY[:vuosi]::INTEGER[] AND
           (y.yhaid IS NOT NULL OR
            (y.yhaid IS NULL AND EXISTS (SELECT id FROM paikkauskohde where "pot?" = true and "yllapitokohde-id" = y.id)))) > 0 AND
     (:vuosi BETWEEN
         EXTRACT (YEAR FROM u.alkupvm) AND
         EXTRACT (YEAR FROM u.loppupvm)) AND
     (:urakat_annettu IS NOT TRUE OR u.id IN (:urakka_idt)) AND
     (:ely_id::INTEGER IS NULL OR u.hallintayksikko = :ely_id)
 ORDER BY u.nimi;

