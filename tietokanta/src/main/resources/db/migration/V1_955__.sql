-- Sanktiotyyppien refaktorointi VHAR-6717


ALTER TABLE sanktiotyyppi
    -- Dropataan sanktiolaji ja urakkatyyppi.
    -- Hierarkia ja eri urakkatyypien / urakkavuosikertojen erikoisehdot toteutetaan yhteiskäyttöisessä domain-koodissa.
    DROP COLUMN sanktiolaji,
    DROP COLUMN urakkatyyppi,
    -- Uniikki numero, jolla sanktiotyypit voi erottaa toisistaan.
    -- Kälissä käsitellään primary-key:tä (id), mutta esimerkiksi migraatioista tulee ikäviä, kun viitataan vain nimiin.
    -- Koodeihin on myös helpompi viitata, kun rakennetaan laji->tyyppi hierarkiaa domain-koodissa.
    -- Lisäksi tyypin nimeä voi huoletta muuttaa, koska koodi pysyy aina samana.
    ADD COLUMN koodi     SMALLINT UNIQUE,
    -- Sanktiotyyppi voidaan merkitä poistetuksi, mutta sitä ei oikeasti poisteta, jotta historiatiedot säilyvät.
    ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE;


-- Lisätään uniikit koodit jokaiselle sanktiotyypille?
UPDATE sanktiotyyppi
   SET koodi = 0
 WHERE nimi = 'Ei tarvita sanktiotyyppiä';

UPDATE sanktiotyyppi
   SET koodi = 1
 WHERE nimi = 'Muu tuote';

UPDATE sanktiotyyppi
   SET koodi = 2
 WHERE nimi = 'Talvihoito';
UPDATE sanktiotyyppi
   SET koodi = 3
 WHERE nimi = 'Ylläpidon sakko';
UPDATE sanktiotyyppi
   SET koodi = 4
 WHERE nimi = 'Ylläpidon bonus';
UPDATE sanktiotyyppi
   SET koodi = 5
 WHERE nimi = 'Ylläpidon muistutus';
UPDATE sanktiotyyppi
   SET koodi = 6
 WHERE nimi = 'Vesiväylän sakko';
UPDATE sanktiotyyppi
   SET koodi = 7
 WHERE nimi = 'Suolasakko';
UPDATE sanktiotyyppi
   SET koodi = 8
 WHERE nimi = 'Määräpäivän ylitys';
UPDATE sanktiotyyppi
   SET koodi = 9
 WHERE nimi = 'Työn tekemättä jättäminen';
UPDATE sanktiotyyppi
   SET koodi = 10
 WHERE nimi = 'Hallinnolliset laiminlyönnit';
UPDATE sanktiotyyppi
   SET koodi = 11
 WHERE nimi = 'Toiminta- ja laatusuunnitelman vastainen toiminta';
UPDATE sanktiotyyppi
   SET koodi = 12
 WHERE nimi = 'Asiakirjamerkintöjen paikkansa pitämättömyys';
UPDATE sanktiotyyppi
   SET koodi = 13
 WHERE nimi = 'Talvihoito, päätiet (talvihoitoluokat Is ja I)';
UPDATE sanktiotyyppi
   SET koodi = 14
 WHERE nimi =
       'Talvihoito, muut tiet, ml kevyen liikenteen väylät (talvihoitoluokat TIb, Ib, II, III ja K) ja pysäkkikatosten talvihoito';
UPDATE sanktiotyyppi
   SET koodi = 15
 WHERE nimi = 'Liikenneympäristön hoito';
UPDATE sanktiotyyppi
   SET koodi = 16
 WHERE nimi = 'Sorateiden hoito ja ylläpito';


-- Nimetään sanktiotyyppejä uudelleen
UPDATE sanktiotyyppi
   SET nimi = 'Muu sopimuksen vastainen toiminta'
 WHERE koodi = 11;

UPDATE sanktiotyyppi
   SET nimi = 'Talvihoito, päätiet'
 WHERE koodi = 13;

UPDATE sanktiotyyppi
   SET nimi = 'Talvihoito, muut tiet'
 WHERE koodi = 14;


-- Lisätään uusi sanktiotyyppi (MHU 2020 ->)
INSERT INTO sanktiotyyppi (nimi, toimenpidekoodi, koodi)
VALUES ('Muut hoitourakan tehtäväkokonaisuudet', NULL, 17)
    ON CONFLICT DO NOTHING;


-- Poistetaan sanktiotyyppejä (Ei poisteta oikeasti, joten historiatieto säilytetään)
UPDATE sanktiotyyppi
   SET poistettu = TRUE
 WHERE koodi = 0;

UPDATE sanktiotyyppi
   SET poistettu = TRUE
 WHERE koodi = 1;

