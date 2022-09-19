
-- Luodaan sanktiotyypit

INSERT
  INTO sanktiotyyppi
       (nimi, sanktiolaji, toimenpidekoodi, urakkatyyppi)
VALUES ('Määräpäivän ylitys', ARRAY['C'::sanktiolaji], NULL, ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Työn tekemättä jättäminen', ARRAY['C'::sanktiolaji], NULL, ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Hallinnolliset laiminlyönnit', ARRAY['C'::sanktiolaji], NULL, ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Toiminta- ja laatusuunnitelman vastainen toiminta', ARRAY['C'::sanktiolaji], NULL, ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Asiakirjamerkintöjen paikkansa pitämättömyys', ARRAY['C'::sanktiolaji], NULL, ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),

       ('Talvihoito, päätiet (talvihoitoluokat Is ja I)', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Talvihoito, muut tiet, ml kevyen liikenteen väylät (talvihoitoluokat TIb, Ib, II, III ja K) ja pysäkkikatosten talvihoito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Liikenneympäristön hoito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'muistutus'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi]),
       ('Sorateiden hoito ja ylläpito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'muistutus'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), ARRAY['hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi])
       ;
       
