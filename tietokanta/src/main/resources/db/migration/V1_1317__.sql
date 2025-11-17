-- Korjataan tehtävien materiaaliluokat 
-- https://extranet.vayla.fi/wiki/spaces/HARJA/pages/285776556/Materiaaleina+raportoitavat+teht%C3%A4v%C3%A4t 


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
