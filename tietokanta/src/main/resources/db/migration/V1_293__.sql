-- Varmista että kaikki Liikenneympäristön hoidon tehtävät ovat yksikkö- ja muutoshinnoiteltuja
UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['yksikkohintainen'::hinnoittelutyyppi, 'muutoshintainen'::hinnoittelutyyppi]
WHERE taso = 4 AND emo IN (SELECT id FROM toimenpidekoodi WHERE koodi = '23116');


-- Lisää uusi tehtävä Liikenneympäristön hoidon alle: Maastopalvelu, kokonaishintainen
INSERT INTO toimenpidekoodi
(nimi,koodi,emo,taso,yksikko,
 tuotenumero,hinnoittelu)
VALUES
  ('Maastopalvelu',NULL,(SELECT id FROM toimenpidekoodi WHERE koodi = '23116'),4,'h',
   NULL, ARRAY['kokonaishintainen'::hinnoittelutyyppi]);