-- Ajakohtaisempi funktio indeksikorjauksille, otettu huomioon 23 sopimusmuutokset 
CREATE OR REPLACE FUNCTION indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER, urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    arvo NUMERIC;
    vertailu_kk NUMERIC;
    vertailuvuosi NUMERIC;
    indeksikerroin NUMERIC;

    /* Perusluku (2017>= hoitourakat):
       Alkuvuodesta edellisen vuoden syys, loka, marras indeksien keskiarvo
       Muissa tapauksissa voi katsoa funktiosta 
    */
    perusluku NUMERIC := indeksilaskennan_perusluku(urakka_id);
    
    -- Kaikilla 2019>= urakoilla indeksi yleensä 'MAKU 2015'
    indeksin_nimi TEXT := (
        SELECT indeksi
        FROM urakka u
        WHERE u.id = urakka_id
    );
    
    alku_vuosi NUMERIC := (
        SELECT EXTRACT(YEAR FROM u.alkupvm)
        FROM urakka u
        WHERE u.id = urakka_id
    );

    urakka_tyyppi TEXT := (
        SELECT tyyppi
        FROM urakka u
        WHERE u.id = urakka_id
    );
BEGIN
    /* Indeksikertoimen laskenta (teiden-hoito): 
      >= 2023 urakat: Kuluvan hk:n elokuun indeksi / perusluku  
      < 2023 urakat: Kuluvan hk:n syyskuun indeksi / perusluku  
    */
    IF urakka_tyyppi = 'teiden-hoito' AND alku_vuosi >= 2023 THEN
        vertailu_kk := 8; 
    ELSE
        vertailu_kk := 9;
    END IF;

    /* Jos HK alkaa vaikka 1.10.2023 
         1) vuosi_ = 2024, kuukausi_ = 9 (Kyseessä 1. hoitokausi)
         -> Verrataan 2023 vuotta, kuukautta 8.

         2) vuosi_ = 2024, kuukausi_ = 10 (Kyseessä 2. hoitokausi)
         -> Verrataan 2024 vuotta, kuukautta 8.
    */
    IF kuukausi_ BETWEEN 1 AND 9 THEN
        vertailuvuosi := vuosi_ - 1;
    ELSE
        vertailuvuosi := vuosi_;
    END IF;

    -- Hae indeksi, jolla jaetaan perusluku ja lasketaan indeksikerroin
    -- Jos indeksiä ei ole, funktio palauttaa null, mikä on OK
    arvo := (
        SELECT i.arvo
        FROM indeksi i
        WHERE i.vuosi = vertailuvuosi
        AND i.kuukausi = vertailu_kk
        AND nimi = indeksin_nimi
    );

    -- Indeksikerroin pyöristetään 3 desimaaliin CLJ-puolella (budjettisuunnittelu/hae-urakan-indeksikertoimet)
    -- Tämä sääntö myös asiakirjoissa 
    indeksikerroin := round((arvo / perusluku), 3);

    -- RAISE NOTICE 'vuosi: %, kuukausi: %, arvo: %, indeksikerroin: %, korjattava arvo: %', vuosi_, kuukausi_, arvo, indeksikerroin, korjattava_arvo;
    -- RAISE NOTICE 'vertailuvuosi: % vertailu_kk: %', vertailuvuosi, vertailu_kk;

    -- Tallennettava arvo pyöristetään 6 desimaaliin CLJ-puolella (budjettisuunnittelu/indeksikorjaa)
    RETURN round(korjattava_arvo * indeksikerroin, 6);
END ;
$$ language plpgsql;


----------------------------------------------------
-- Korjaa indeksit Kustannusarvioiduille töille
----------------------------------------------------
WITH korjatut_indeksit AS (
    SELECT 
        kt.id                                                       AS id,
        kt.vuosi,
        kt.kuukausi,
        kt.summa                                                    AS summa,
        kt.summa_indeksikorjattu                                    AS summa_indeksikorjattu,
        indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id)        AS korjattu_arvo
    FROM kustannusarvioitu_tyo kt
        JOIN toimenpideinstanssi tpi 
          ON kt.toimenpideinstanssi = tpi.id
        JOIN urakka u 
          ON tpi.urakka = u.id
        LEFT JOIN tehtavaryhma tr 
          ON kt.tehtavaryhma = tr.id
    -- Etsitään rivit joilla ei ole indeksikorjausta, mutta on summa 
    WHERE kt.summa_indeksikorjattu IS NULL
    -- Summa täytyy olla olemassa 
    AND kt.summa IS NOT NULL 
    AND kt.summa != 0
    -- Katsotaan vielä varmuuden vuoksi että päätöstä hoitokaudelle ei ole tehty 
    AND NOT EXISTS (
        SELECT 1
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up.poistettu IS NOT TRUE
           -- Osuuko kustannuksen kk ja vuosi päätöksen hoitokaudelle 
           AND (
              (kt.vuosi = up."hoitokauden-alkuvuosi" AND kt.kuukausi BETWEEN 10 AND 12) OR
              (kt.vuosi = up."hoitokauden-alkuvuosi" + 1 AND kt.kuukausi BETWEEN 1 AND 9)
          )
    )
    -- Alkanut 2019 jälkeen
    AND u.alkupvm >= '2019-09-30'
    -- Vain käynnissä olevat  (Tuloksena tulee  vain 23 -> urakoita)
    AND u.loppupvm >= '2024-10-01'
    -- Päivitetään vain rivit joille indeksikorjaus saatavilla 
    AND indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) IS NOT NULL 
    -- Tavoitehinnan ulkopuoliset rahavaraukset 
    -- Kutsutaan vanhalla termillä: "Tilaajan rahavaraukset", näille ei lasketa indeksikorjauksia
    -- Tämä on eri kun "Tilaajan rahavaraus kannustinjärjestelmään" 
    AND NOT (
        tr.yksiloiva_tunniste IS NOT NULL 
        -- Johto- ja hallintokorvaus (J)
        AND tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'
        -- MHU ja HJU Hoidon johto
        AND tpi.toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151')
    )
  -- Syötä indeksikorjattu arvo sisään
) UPDATE kustannusarvioitu_tyo
     SET summa_indeksikorjattu = korjatut_indeksit.korjattu_arvo,
         muokkaaja             = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
         muokattu              = NOW()
    FROM korjatut_indeksit
   WHERE korjatut_indeksit.id = kustannusarvioitu_tyo.id;


----------------------------------------------------
-- Korjaa johto ja hallinto indeksit 
----------------------------------------------------
WITH korjatut_indeksit AS (
    SELECT 
        jk.id                                                       AS id,
        jk.tuntipalkka                                              AS summa,
        jk.tuntipalkka_indeksikorjattu                              AS vanha_arvo,
        indeksikorjaa(jk.tuntipalkka, jk.vuosi, jk.kuukausi, u.id)  AS korjattu_arvo
    FROM johto_ja_hallintokorvaus jk
        JOIN urakka u 
          ON jk."urakka-id" = u.id
    -- Indeksikorjausta ei ole olemassa
    WHERE tuntipalkka_indeksikorjattu IS NULL 
    -- Summa täytyy olla olemassa 
    AND jk.tuntipalkka IS NOT NULL 
    AND jk.tuntipalkka != 0
    -- Katsotaan vielä varmuuden vuoksi että päätöstä hoitokaudelle ei ole tehty 
    AND NOT EXISTS (
        SELECT 1
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up.poistettu IS NOT TRUE
           -- Osuuko kustannuksen kk ja vuosi päätöksen hoitokaudelle 
           AND (
              (jk.vuosi = up."hoitokauden-alkuvuosi" AND jk.kuukausi BETWEEN 10 AND 12) OR
              (jk.vuosi = up."hoitokauden-alkuvuosi" + 1 AND jk.kuukausi BETWEEN 1 AND 9)
          )
    )
    -- Alkanut 2019 jälkeen
    AND u.alkupvm >= '2019-09-30'
    -- Vain käynnissä olevat (Tuloksena tulee  vain 23 -> urakoita)
    AND u.loppupvm >= '2024-10-01'
    -- Päivitetään vain rivit joille indeksikorjaus saatavilla 
    AND indeksikorjaa(jk.tuntipalkka, jk.vuosi, jk.kuukausi, u.id) IS NOT NULL 
  -- Syötä korjatut indeksit johto ja hallintokorjauksiin
) UPDATE johto_ja_hallintokorvaus
    SET  tuntipalkka_indeksikorjattu = korjatut_indeksit.korjattu_arvo,
         muokkaaja                   = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
         muokattu                    = NOW()
    FROM korjatut_indeksit
   WHERE korjatut_indeksit.id = johto_ja_hallintokorvaus.id;


----------------------------------------------------
-- Korjaa Kiinteähintaiset työt 
----------------------------------------------------
WITH korjatut_indeksit AS (
    SELECT  
        kt.id                                                AS id,
        indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) AS korjattu_arvo,
        kt.summa_indeksikorjattu                             AS vanha_arvo, 
        kt.summa
    FROM kiinteahintainen_tyo kt
        JOIN toimenpideinstanssi tpi 
          ON kt.toimenpideinstanssi = tpi.id
        JOIN urakka u 
          ON tpi.urakka = u.id
    WHERE summa_indeksikorjattu IS NULL 
    -- Summa täytyy olla olemassa 
    AND kt.summa IS NOT NULL 
    AND kt.summa != 0
    -- Katsotaan vielä varmuuden vuoksi että päätöstä hoitokaudelle ei ole tehty 
    AND NOT EXISTS (
        SELECT 1
          FROM urakka_paatos up
         WHERE up."urakka-id" = u.id
           AND up.poistettu IS NOT TRUE
           -- Osuuko kustannuksen kk ja vuosi päätöksen hoitokaudelle 
           AND (
              (kt.vuosi = up."hoitokauden-alkuvuosi" AND kt.kuukausi BETWEEN 10 AND 12) OR
              (kt.vuosi = up."hoitokauden-alkuvuosi" + 1 AND kt.kuukausi BETWEEN 1 AND 9)
          )
    )
    -- Alkanut 2019 jälkeen
    AND u.alkupvm >= '2019-09-30'
    -- Vain käynnissä olevat
    AND u.loppupvm >= '2024-10-01'
    -- Päivitetään vain rivit joille indeksikorjaus saatavilla 
    AND indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) IS NOT NULL  
  -- Syötä korjaukset kantaan 
) UPDATE kiinteahintainen_tyo
     SET summa_indeksikorjattu = korjatut_indeksit.korjattu_arvo,
         muokkaaja             = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
         muokattu              = NOW()
    FROM korjatut_indeksit
   WHERE korjatut_indeksit.id = kiinteahintainen_tyo.id;


----------------------------------------------------
-- Korjaa tavoitehintojen indeksiluvut
----------------------------------------------------
WITH korjatut_indeksit AS (
    SELECT * FROM (
        SELECT 
            hoitokausi,
            ut.id                                     AS id,
            ut.tavoitehinta_indeksikorjattu           AS tavoitehinta_indeksikorjattu_vanha,
            indeksikorjaa(
                ut.tavoitehinta,
                EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                10,
                u.id
            )                                         AS tavoitehinta_indeksikorjattu_uusi,
            ut.tavoitehinta_siirretty_indeksikorjattu AS tavoitehinta_siirretty_indeksikorjattu_vanha,
            indeksikorjaa(
                ut.tavoitehinta_siirretty,
                EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                10,
                u.id
            )                                         AS tavoitehinta_siirretty_indeksikorjattu_uusi,
            ut.kattohinta_indeksikorjattu             AS kattohinta_indeksikorjattu_vanha,
            indeksikorjaa(
                ut.kattohinta,
                EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                10,
                u.id
            )                                         AS kattohinta_indeksikorjattu_uusi
        FROM urakka_tavoite ut
            JOIN urakka u 
              ON ut.urakka = u.id
        WHERE u.tyyppi = 'teiden-hoito'
        -- Katsotaan vielä varmuuden vuoksi että päätöstä hoitokaudelle ei ole tehty 
        AND NOT EXISTS (
            SELECT 1
              FROM urakka_paatos up
            WHERE up."urakka-id" = u.id
              AND up.poistettu IS NOT TRUE
              AND (
                  ((EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1) = up."hoitokauden-alkuvuosi" AND 10 BETWEEN 10 AND 12) OR
                  ((EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1) = up."hoitokauden-alkuvuosi" + 1 AND 10 BETWEEN 1 AND 9)
              )
        )
    ) indeksikorjaus
    WHERE (
        tavoitehinta_indeksikorjattu_vanha IS DISTINCT FROM tavoitehinta_indeksikorjattu_uusi
        OR tavoitehinta_siirretty_indeksikorjattu_vanha IS DISTINCT FROM tavoitehinta_siirretty_indeksikorjattu_uusi
        OR kattohinta_indeksikorjattu_vanha IS DISTINCT FROM kattohinta_indeksikorjattu_uusi
    )
    AND tavoitehinta_indeksikorjattu_uusi IS NOT NULL 
    AND kattohinta_indeksikorjattu_uusi IS NOT NULL 
) 
UPDATE urakka_tavoite
   SET tavoitehinta_indeksikorjattu           = korjatut_indeksit.tavoitehinta_indeksikorjattu_uusi,
       tavoitehinta_siirretty_indeksikorjattu = korjatut_indeksit.tavoitehinta_siirretty_indeksikorjattu_uusi,
       kattohinta_indeksikorjattu             = korjatut_indeksit.kattohinta_indeksikorjattu_uusi,
       muokkaaja                              = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
       muokattu                               = NOW()
  FROM korjatut_indeksit
 WHERE korjatut_indeksit.id = urakka_tavoite.id;
