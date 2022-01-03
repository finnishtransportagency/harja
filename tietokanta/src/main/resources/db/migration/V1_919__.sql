/* VHAR-5354: Laske indeksikorjaukset uusiksi tietokantatauluhin mikäli indeksiä muutetaan tai uusi indeksi lisätään
   admin-käyttöliittymässä
*/

-- Muuta indeksikorjaa-funktiota niin, että se palauttaa tarkalleen samoja arvoja kuin CLJ-toteutus
CREATE OR REPLACE FUNCTION indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER,
                                         urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    -- Perusluku on urakalle sama riippumatta kuluvasta hoitokaudesta
    perusluku      NUMERIC := indeksilaskennan_perusluku(urakka_id);
    indeksin_nimi  TEXT    := (SELECT indeksi
                               FROM urakka u
                               WHERE u.id = urakka_id);
    arvo NUMERIC;
    vertailuvuosi NUMERIC;
    indeksikerroin NUMERIC;
BEGIN
    -- Indeksikerroin on hoitokausikohtainen, katsotaan aina edellisen hoitokauden syyskuun indeksiä.
    IF kuukausi_ BETWEEN 1 AND 9
    THEN
        vertailuvuosi := vuosi_ - 1;
    ELSE
        vertailuvuosi := vuosi_;
    END IF;

    arvo := (SELECT i.arvo
             FROM indeksi i
             WHERE i.vuosi = vertailuvuosi
               AND i.kuukausi = 9
               AND nimi = indeksin_nimi);

    -- Indeksikerroin pyöristetään 3 desimaaliin CLJ-puolella (budjettisuunnittelu/hae-urakan-indeksikertoimet)
    indeksikerroin := round((arvo / perusluku), 3);

    --RAISE NOTICE 'vuosi: %, kuukausi: %, arvo: %, indeksikerroin: %, korjattava arvo: %', vuosi_, kuukausi_, arvo, indeksikerroin, korjattava_arvo;

    -- Tallennettava arvo pyöristetään 6 desimaaliin CLJ-puolella (budjettisuunnittelu/indeksikorjaa)
    return round(korjattava_arvo * indeksikerroin, 6);
END ;
$$ language plpgsql;
