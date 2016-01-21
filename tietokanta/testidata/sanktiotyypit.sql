
-- Luodaan sanktiotyypit

INSERT
  INTO sanktiotyyppi
       (nimi, sanktiolaji, toimenpidekoodi)
VALUES ('Määräpäivän ylitys', ARRAY['C'::sanktiolaji], NULL),
       ('Työn tekemättä jättäminen', ARRAY['C'::sanktiolaji], NULL),
       ('Hallinnolliset laiminlyönnit', ARRAY['C'::sanktiolaji], NULL),
       ('Toiminta- ja laatusuunnitelman vastainen toiminta', ARRAY['C'::sanktiolaji], NULL),
       ('Asiakirjamerkintöjen paikkansa pitämättömyys', ARRAY['C'::sanktiolaji], NULL),

       ('Talvihoito, päätiet (talvihoitoluokat Is ja I)', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23104')),
       ('Talvihoito, muut tiet, ml kevyen liikenteen väylät (talvihoitoluokat TIb, Ib, II, III ja K) ja pysäkkikatosten talvihoito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23104')),
       ('Liikenneympäristön hoito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23116')),
       ('Sorateiden hoito ja ylläpito', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji], (SELECT id FROM toimenpidekoodi WHERE koodi='23124'))
       ;
       
