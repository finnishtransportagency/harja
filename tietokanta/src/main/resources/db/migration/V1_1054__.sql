-- Lisätään neljä tehtävää, jotka ovat voimassa vain joissakin MH-urakoissa.
-- Koska varsinaisia urakkakohtaisia tehtäviä ei Harjassa tueta, nämä ovat voimassa kaikissa urakoissa.
-- Listataan ne Tehtävät ja Määrät sivulla osionsa loppuun.

INSERT INTO tehtava (nimi, tehtavaryhma, emo, yksikko, suunnitteluyksikko, jarjestys, hinnoittelu, api_seuranta, ensisijainen, voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja) VALUES
    ('Kaiteiden poisto ja uusiminen',
     (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'),
     (select id from toimenpide where nimi = 'LIIKENNEYMPÄRISTÖN HOITO'),
     'jm',
     'jm',
     1645,
     '{kokonaishintainen}',
     FALSE,
     TRUE,
     2019,
     TRUE,
     TRUE,
     FALSE,
     current_timestamp,
     (select id from kayttaja where kayttajanimi = 'Integraatio') );

INSERT INTO tehtava (nimi, tehtavaryhma, emo, yksikko, suunnitteluyksikko, jarjestys, hinnoittelu, api_seuranta, ensisijainen, voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja) VALUES
    ('Kaiteiden kunnostaminen',
     (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'),
     (select id from toimenpide where nimi = 'LIIKENNEYMPÄRISTÖN HOITO'),
     'jm',
     'jm',
     1646,
     '{kokonaishintainen}',
     FALSE,
     TRUE,
     2019,
     TRUE,
     TRUE,
     FALSE,
     current_timestamp,
     (select id from kayttaja where kayttajanimi = 'Integraatio') );

INSERT INTO tehtava (nimi, tehtavaryhma, emo, yksikko, suunnitteluyksikko, jarjestys, hinnoittelu, api_seuranta, ensisijainen, voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja) VALUES
    ('Kaiteiden rakentaminen',
     (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'),
     (select id from toimenpide where nimi = 'LIIKENNEYMPÄRISTÖN HOITO'),
     'jm',
     'jm',
     1647,
     '{kokonaishintainen}',
     FALSE,
     TRUE,
     2019,
     TRUE,
     TRUE,
     FALSE,
     current_timestamp,
     (select id from kayttaja where kayttajanimi = 'Integraatio') );

-- Muutetaan HJ-urakoissa käytössä olleen tehtävän nimi, jotta seuraava insert menee läpi.
-- Tehtävät on linkitetty eri tehtäväryhmään, siksi ei voida käyttää tätä jo olemassa olevaa tehtävää.
-- On loogisempaa ilmoittaa yksikkö tiekm HJ-urakan tehtävässä, koska siihen on ollut APIn kautta kirjaaminen mahdollinen.
-- Yleensä kun Harjassa on tehtävän nimessä ilmoitettu yksikkö, joka on kilometrejä, kyseessä on tehtävä, jolle kirjataan työkoneelta raportoituja toteumia.
-- Uuteen reunapaalutehtävään kirjataan käsin.
UPDATE tehtava
SET nimi      = 'Reunapaalujen uusiminen (tiekm)',
    muokattu  = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Reunapaalujen uusiminen' AND
        emo = (select id from toimenpide where nimi = 'LIIKENNEYMPÄRISTÖN HOITO');

INSERT INTO tehtava (nimi, tehtavaryhma, emo, yksikko, suunnitteluyksikko, jarjestys, hinnoittelu, api_seuranta, ensisijainen, voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja) VALUES
    ('Reunapaalujen uusiminen',
     (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'),
     (select id from toimenpide where nimi = 'LIIKENNEYMPÄRISTÖN HOITO'),
     'tiekm',
     'tiekm',
     1649,
     '{kokonaishintainen}',
     FALSE,
     TRUE,
     2019,
     TRUE,
     TRUE,
     FALSE,
     current_timestamp,
     (select id from kayttaja where kayttajanimi = 'Integraatio') );
