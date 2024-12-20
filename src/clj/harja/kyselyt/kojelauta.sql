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
     (:elyt_annettu IS NOT TRUE OR u.hallintayksikko IN (:ely_idt))
 ORDER BY COALESCE(u.lyhyt_nimi, u.nimi);

-- name: hae-paallystysurakat-kojelautaan
SELECT u.id,
       u.nimi,
       u.hallintayksikko as ely_id,
       :vuosi as hoitokauden_alkuvuosi, -- Käytetään UIn vuoksi tässä samaa termiä kuin hoidossa vaikka kyseessä on vuosi
       COUNT(*) FILTER (WHERE y.id IS NOT NULL) AS yllapitokohteiden_lkm,
       COUNT(*) FILTER (WHERE pot2.tila IN ('valmis', 'lukittu')) AS valmis_hyvaksytty,
       COUNT(*) FILTER (WHERE y.lahetetty IS NOT NULL AND pot2.tila IN ('valmis', 'lukittu')
           AND y.lahetys_onnistunut IS TRUE) AS lahetetty_onnistuneesti,
       COUNT(*) FILTER (WHERE y.lahetetty IS NOT NULL AND pot2.tila IN ('valmis', 'lukittu')
           AND y.lahetys_onnistunut IS FALSE) AS epaonnistuneet_lahetetyt,
       COUNT(*) FILTER (WHERE pot2.tila IN ('valmis', 'lukittu') AND y.lahetetty IS NULL) AS valmiit_ei_lahetetty,
       COUNT(*) FILTER (WHERE y.id IS NOT NULL AND NOT exists (select id from paallystysilmoitus WHERE paallystyskohde = y.id)) AS aloittamatta,
       -- hox tästä puuttuu niitä where ehdon lauseita, Laurin haarassa on oikeat ehdot...
       (SELECT ARRAY_AGG(ROW(y.id::TEXT, y.kohdenumero::TEXT, y.tunnus::TEXT, y.nimi::TEXT)) FROM yllapitokohde y WHERE y.lahetysvirhe is not null and y.urakka = u.id) as virheelliset_kohteet
  FROM urakka u
           JOIN organisaatio o ON u.hallintayksikko = o.id
           JOIN yllapitokohde y ON y.urakka = u.id
           LEFT JOIN paallystysilmoitus pot2 ON y.id = pot2.paallystyskohde AND pot2.poistettu IS NOT TRUE
 WHERE
     u.tyyppi = 'paallystys' AND
     u.urakkanro IS NOT NULL AND -- testiurakat pois
     -- oltava vähintään yksi ylläpitokohde jolle tehdään pot-lomake
     y.urakka = u.id AND y.poistettu IS FALSE AND y.vuodet @> ARRAY[:vuosi]::INTEGER[] AND
     (y.yhaid IS NOT NULL OR
      (y.yhaid IS NULL AND EXISTS (SELECT id FROM paikkauskohde where "pot?" = true and "yllapitokohde-id" = y.id))) AND
     (:vuosi BETWEEN
         EXTRACT (YEAR FROM u.alkupvm) AND
         EXTRACT (YEAR FROM u.loppupvm)) AND
     (:urakat_annettu IS NOT TRUE OR u.id IN (:urakka_idt)) AND
     (:elyt_annettu IS NOT TRUE OR u.hallintayksikko IN (:ely_idt))
 GROUP BY u.id, u.nimi, u.hallintayksikko, hoitokauden_alkuvuosi
 ORDER BY u.nimi;

