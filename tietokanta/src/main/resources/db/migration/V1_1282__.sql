-- Uusi tehtävä vuodesta 2027 alkaen: Avo-ojitus koneohjauksella (soratiet)
-- Tehtäväryhmä: Z - Avo-ojitus, soratiet
-- Toimenpide: YLLÄPITO
-- Ei API-seurantaa.
INSERT INTO tehtava (nimi, yksikko, suunnitteluyksikko, jarjestys, "mhu-tehtava?", "maaramitattava?", aluetieto,
                     voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, kasin_lisattava_maara, "raportoi-tehtava?",
                     tehtavaryhma, emo, hinnoittelu, luotu, luoja, nopeusrajoitus, yksiloiva_tunniste)
VALUES ('Avo-ojitus koneohjauksella, soratiet', 'jm', 'jm', 1420, true, true, false,
        2027, null, true, true,
        (select id from tehtavaryhma where yksiloiva_tunniste = '82ecc58a-f96c-46f0-9c70-d29bb6cd4266'),
        (select id from toimenpide where koodi = '20191'), '{kokonaishintainen}', current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'), 108, '2a89a917-fab8-4483-8063-683a066d3f07');

-- Selkiytetään samalla muiden samassa osiossa näkyvien nimien visuaalista ilmettä
UPDATE tehtava SET nimi = 'Avo-ojitus (kaapeli kaivualueella), päällystetyt tiet' WHERE nimi =  'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)';
UPDATE tehtava SET nimi = 'Avo-ojitus (kaapeli kaivualueella), soratiet' WHERE nimi = 'Avo-ojitus/soratiet (kaapeli kaivualueella)';
UPDATE tehtava SET nimi = 'Avo-ojitus, soratiet' WHERE nimi = 'Avo-ojitus/soratiet';
UPDATE tehtava SET nimi = 'Avo-ojitus, päällystetyt tiet' WHERE nimi = 'Avo-ojitus/päällystetyt tiet';
UPDATE tehtava SET nimi = 'Laskuojat, soratiet' WHERE nimi = 'Laskuojat/soratiet';
UPDATE tehtava SET nimi = 'Laskuojat, päällystetyt tiet' WHERE nimi = 'Laskuojat/päällystetyt tiet';
