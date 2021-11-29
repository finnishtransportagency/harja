-- Testidatan generointia varten halutaan helppo tapa indeksikorjata MHU:iden kuluja.
CREATE OR REPLACE FUNCTION testidata_indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER,
                                                   urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    -- Perusluku on urakalle sama riippumatta kuluvasta hoitokaudesta
    perusluku      NUMERIC := indeksilaskennan_perusluku(urakka_id);
    indeksin_nimi  TEXT    := (SELECT indeksi
                               FROM urakka u
                               WHERE u.id = urakka_id);
    -- Indeksikerroin pyöristetään kolmeen desimaaliin.
    indeksikerroin NUMERIC;
BEGIN
    -- Indeksikerroin on hoitokausikohtainen, katsotaan aina edellisen hoitokauden syyskuun indeksiä.
    IF kuukausi_ BETWEEN 1 AND 9 THEN
        indeksikerroin := (SELECT round((arvo / perusluku), 3)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_ - 1
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    ELSE
        indeksikerroin := (SELECT round((arvo / perusluku), 3)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    END IF;
    -- Ja tallennettava arvo kuuteen.
    return round(korjattava_arvo * indeksikerroin, 6);
END ;
$$ language plpgsql;