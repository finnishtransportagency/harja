-- Luodaan sanktiotyypit

INSERT INTO sanktiotyyppi (koodi, nimi, toimenpidekoodi)
VALUES (8, 'Määräpäivän ylitys', NULL),
       (9, 'Työn tekemättä jättäminen', NULL),
       (10, 'Hallinnolliset laiminlyönnit', NULL),
       (11, 'Muu sopimuksen vastainen toiminta', NULL),
       (12, 'Asiakirjamerkintöjen paikkansa pitämättömyys', NULL),
       (13, 'Talvihoito, päätiet', (SELECT id FROM toimenpide WHERE koodi = '23104')),
       (14, 'Talvihoito, muut tiet', (SELECT id FROM toimenpide WHERE koodi = '23104')),
       (15, 'Liikenneympäristön hoito', (SELECT id FROM toimenpide WHERE koodi = '23116')),
       (16, 'Sorateiden hoito ja ylläpito', (SELECT id FROM toimenpide WHERE koodi = '23124'));
       
