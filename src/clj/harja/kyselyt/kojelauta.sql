-- name: hae-hoidon-urakat-kojelautaan
SELECT u.id,
       COALESCE(u.lyhyt_nimi, u.nimi) AS nimi,
       u.hallintayksikko as ely_id,
       EXTRACT (YEAR FROM u.alkupvm) AS urakan_alkuvuosi,
       :hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       urakan_kustannussuunnitelman_tila(u.id::INTEGER,
                                         monesko_hoitokausi(u.alkupvm, u.loppupvm,
                                                            :hoitokauden_alkuvuosi::INTEGER))       AS ks_tila,
       (SELECT tyyppi::TEXT
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
           AND up.poistettu IS FALSE
           AND up.tyyppi IN ('tavoitehinnan-ylitys', 'tavoitehinnan-alitus') LIMIT 1) AS tavoitehintapaatos,
       (SELECT tyyppi::TEXT
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
           AND up.poistettu IS FALSE
           AND up.tyyppi IN ('kattohinnan-ylitys') LIMIT 1) AS kattohintapaatos,
       (SELECT ARRAY_AGG(DISTINCT (tyyppi::TEXT))
         FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
           AND up.poistettu IS FALSE
           AND up.tyyppi IN ('lupausbonus', 'lupaussanktio')) AS lupauspaatokset,
       sit.pisteet AS lupaus_tavoitepisteet,
       (SELECT count(*) FROM laatupoikkeama lp WHERE lp.urakka = u.id AND lp.paatos IS NULL AND lp.poistettu IS FALSE AND
           lp.aika BETWEEN make_date(:hoitokauden_alkuvuosi::INTEGER, 10, 1) AND
               make_date(:hoitokauden_alkuvuosi::INTEGER + 1, 9, 30) + interval '23 hours 59 minutes 59 seconds') AS avoimet_laatupoikkeamat,
       (SELECT count(*) FROM turvallisuuspoikkeama tp WHERE tp.urakka = u.id AND
           tp.tila IN ('avoin', 'taydennetty') AND
           tp.tapahtunut BETWEEN make_date(:hoitokauden_alkuvuosi::INTEGER, 10, 1) AND
               make_date(:hoitokauden_alkuvuosi::INTEGER + 1, 9, 30) + interval '23 hours 59 minutes 59 seconds') AS avoimet_turvallisuuspoikkeamat
  FROM urakka u
           JOIN organisaatio o ON u.hallintayksikko = o.id
           LEFT JOIN lupaus_sitoutuminen sit ON
      -- varmistetaan tasan yksi rivi MAX-funktion avulla
      sit.id = (SELECT MAX(id) FROM lupaus_sitoutuminen ls WHERE ls."urakka-id" = u.id AND ls.pisteet IS NOT NULL AND ls.poistettu IS FALSE)
 WHERE
     u.tyyppi = 'teiden-hoito' AND
     u.urakkanro IS NOT NULL AND -- testiurakat pois
     (:hoitokauden_alkuvuosi BETWEEN
         EXTRACT (YEAR FROM u.alkupvm) AND
         EXTRACT (YEAR FROM u.loppupvm) - 1) AND
     (:urakat_annettu IS NOT TRUE OR u.id IN (:urakka_idt)) AND
     (:ely_id::INTEGER IS NULL OR u.hallintayksikko = :ely_id)
 ORDER BY COALESCE(u.lyhyt_nimi, u.nimi);

-- name: hae-paallystysurakat-kojelautaan
SELECT u.id,
       u.nimi,
       u.hallintayksikko as ely_id,
       :vuosi as vuosi,
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

