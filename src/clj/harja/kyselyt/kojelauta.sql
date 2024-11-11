-- name: hae-hoidon-urakat-kojelautaan
SELECT u.id,
       COALESCE(u.lyhyt_nimi, u.nimi) AS nimi,
       u.hallintayksikko as ely_id,
       :hoitokauden_alkuvuosi as hoitokauden_alkuvuosi,
       urakan_kustannussuunnitelman_tila(u.id::INTEGER,
                                         monesko_hoitokausi(u.alkupvm, u.loppupvm,
                                                            :hoitokauden_alkuvuosi::INTEGER))       AS ks_tila,
       (SELECT ARRAY_AGG(DISTINCT (tyyppi::TEXT))
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
           AND up.poistettu IS FALSE
           AND up.tyyppi IN ('tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus')) AS rahapaatokset,
       (SELECT ARRAY_AGG(DISTINCT (tyyppi::TEXT))
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
           AND up.poistettu IS FALSE
           AND up.tyyppi IN ('lupausbonus', 'lupaussanktio'))                                       AS lupauspaatokset,
       sit.pisteet AS lupaus_tavoitepisteet
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
 ORDER BY u.nimi;

-- name: hae-paallystysurakat-kojelautaan
SELECT u.id,
       u.nimi,
       u.hallintayksikko as ely_id,
       :vuosi as hoitokauden_alkuvuosi, -- Käytetään samaa termiä kuin hoidossa vaikka kyseessä on vuosi
       (SELECT count(*) FROM yllapitokohde y
                        WHERE y.urakka = u.id AND
                              y.vuodet @> ARRAY[:vuosi]::INTEGER[] AND
                          -- y.yhaid IS NOT NULL --> kohde on YHA:sta Harjaan haettu päällystyskohde
                            (y.yhaid IS NOT NULL OR
                                -- y.yhaid IS NULL AND paikkauskohde.pot? = TRUE --> kohde on Harjassa luotu paikkauskohde, jolle on merkitty että tehdään päällystysilmoitus (POT)
                             (y.yhaid IS NULL AND EXISTS (SELECT id FROM paikkauskohde where "pot?" = true and "yllapitokohde-id" = y.id)))) as yllapitokohteiden_lkm,
                                COUNT(*) FILTER (WHERE pot2.tila IN ('valmis', 'lukittu')) AS valmis_hyvaksytty,
                                COUNT(*) FILTER (WHERE y.lahetetty IS NOT NULL
                                    AND    pot2.tila IN ('valmis',
                                                         'lukittu')
                                    AND    y.lahetys_onnistunut IS TRUE) AS lahetetty_onnistuneesti,
       COUNT(*) FILTER (WHERE y.lahetetty IS NOT NULL
           AND    pot2.tila IN ('valmis',
                                'lukittu')
           AND    y.lahetys_onnistunut = FALSE) AS epaonnistuneet_lahetetyt
  FROM urakka u
           join organisaatio o ON u.hallintayksikko = o.id
           join yllapitokohde y ON y.urakka = u.id
           left join paallystysilmoitus pot2 ON y.id = pot2.paallystyskohde

 WHERE
     u.tyyppi = 'paallystys' AND
     u.urakkanro IS NOT NULL AND -- testiurakat pois
     -- oltava vähintään yksi ylläpitokohde jolle tehdään pot-lomake
     y.urakka = u.id AND
           y.vuodet @> ARRAY[:vuosi]::INTEGER[] AND
           (y.yhaid IS NOT NULL OR
            (y.yhaid IS NULL AND EXISTS (SELECT id FROM paikkauskohde where "pot?" = true and "yllapitokohde-id" = y.id))) AND
     (:vuosi BETWEEN
         EXTRACT (YEAR FROM u.alkupvm) AND
         EXTRACT (YEAR FROM u.loppupvm)) AND
     (:urakat_annettu IS NOT TRUE OR u.id IN (:urakka_idt)) AND
     (:ely_id::INTEGER IS NULL OR u.hallintayksikko = :ely_id)
 GROUP BY u.id, u.nimi, u.hallintayksikko, hoitokauden_alkuvuosi
 ORDER BY u.nimi;

