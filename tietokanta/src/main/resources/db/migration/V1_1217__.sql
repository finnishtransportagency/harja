-- Korjaa reunapalteen poiston yksikkö vastaamaan tehtävä- ja määräluettelon yksikköä
UPDATE tehtava
SET yksikko            = 'jm',
    suunnitteluyksikko = 'jm'
WHERE nimi = 'Reunapalteen poisto'
  AND emo = (select id from toimenpide where koodi = '23116'); -- Liikenneympäristön hoito
