-- Korjataan tehtävien materiaaliluokat testidataan
-- https://extranet.vayla.fi/wiki/spaces/HARJA/pages/285776556/Materiaaleina+raportoitavat+teht%C3%A4v%C3%A4t 

-- Tämä on tuotannossa jo OK, ei haittaa jos ajetaan sinnekin, mutta siellä ei muutu mikään
-- Liikenteen varmistaminen kelirikkokohteessa (materiaali) 
UPDATE tehtava
SET materiaalikoodi_id  = (SELECT id
                           FROM materiaalikoodi
                           WHERE yksiloiva_tunniste = '6bbe4261-1e22-43ec-a4c4-ae63ecc46b5d'), -- Kelirikkomurske
    materiaaliluokka_id = (SELECT id
                           FROM materiaaliluokka
                           WHERE materiaalityyppi = 'murske'),
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi =  'Liikenteen varmistaminen kelirikkokohteessa (materiaali)'; -- Tällä ei ole yksilöivää tunnistetta 


-- Ennalta arvaamattomien kuljetusten avustaminen (materiaali)
-- Tämä on tuotannossa jo OK, ei haittaa jos ajetaan sinnekin, mutta siellä ei muutu mikään
UPDATE tehtava
SET materiaalikoodi_id  = (SELECT id
                           FROM materiaalikoodi
                           WHERE yksiloiva_tunniste = '378bc7d7-4ec2-4fb9-96ca-29584cfd09fe'), -- Hiekoitushiekka, ennalta arvaamattomien kuljetusten avustaminen
    materiaaliluokka_id = (SELECT id
                           FROM materiaaliluokka
                           WHERE materiaalityyppi = 'hiekoitushiekka'),
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE yksiloiva_tunniste =  'ae67d2b5-a9d9-4880-a7ee-b3870737a177'; -- Ennalta arvaamattomien kuljetusten avustaminen (materiaali)


-- Sorastus on määrämitattava
-- Korjaa tämä testidataan, tuotannossa on jo OK.
UPDATE tehtava
SET "maaramitattava?"   = true,
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi =  'Sorastus'; -- Sorastuksella ei ole yksilöivää tunnistetta 




UPDATE materiaalikoodi 
SET nimi  = 'Talvisuolaliuos CaCl2, päällystettyjen teiden pölynsidonta',
    materiaalityyppi = (SELECT materiaalityyppi FROM materiaaliluokka WHERE nimi = 'Talvisuola'),
    materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola')
WHERE nimi = 'Kesäsuola päällystettyjen teiden pölynsidonta';


UPDATE tehtava
SET nimi                = 'Kesäsuola (CaCl2, materiaali) (2018)',
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi =  'Kesäsuola (CaCl2, materiaali)';


UPDATE tehtava
SET nimi                = 'Kesäsuola (CaCl2, materiaali)',
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi = 'Sorateiden pölynsidonta (materiaali)';


UPDATE tehtava
SET nimi                = 'Liukkaudentorjunta suolaamalla (materiaali)',
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi = 'Suolaus';


UPDATE tehtava
SET materiaalikoodi_id  = NULL,
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi = 'Kesäsuola (CaCl2, materiaali)';
