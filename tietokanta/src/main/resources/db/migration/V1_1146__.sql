-- Ajakohtaisempi funktio indeksikorjauksille
-- Otettu huomioon 23 sopimusmuutokset 
CREATE OR REPLACE FUNCTION indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER, urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    arvo NUMERIC;
    vertailu_kk NUMERIC;
    vertailuvuosi NUMERIC;
    indeksikerroin NUMERIC;

    /* Perusluku (2017>= hoitourakat):
       Viime vuoden syys, loka, marras indeksien keskiarvo
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
