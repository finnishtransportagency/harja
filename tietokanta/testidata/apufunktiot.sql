-- Testidatan generointia varten halutaan helppo tapa indeksikorjata MHU:iden kuluja.
CREATE OR REPLACE FUNCTION testidata_indeksikorjaa(korjattava_arvo NUMERIC, urakka_vuosi INTEGER, urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    perusluku      NUMERIC := indeksilaskennan_perusluku(urakka_id);
    indeksin_nimi  TEXT    := (SELECT indeksinimi
                               FROM urakkatyypin_indeksi ui
                               WHERE ui.urakkatyyppi = (SELECT tyyppi FROM urakka WHERE id = urakka_id));
    -- Indeksikerroin pyöristetään kahdeksaan desimaaliin.
    indeksikerroin NUMERIC := (SELECT round((arvo / perusluku), 8)
                               FROM indeksi i
                               WHERE i.vuosi = urakka_vuosi
                                 AND i.kuukausi = 9
                                 AND nimi = indeksin_nimi);
BEGIN
    -- Ja tallennettava arvo kuuteen. TODO: Tarkistettava, pitääkö sekin olla 8.
    return round(korjattava_arvo * indeksikerroin, 6);
END;
$$ language plpgsql;
