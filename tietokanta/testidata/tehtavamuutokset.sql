UPDATE tehtava
SET   materiaalikoodi_id   = (SELECT id
                                FROM materiaalikoodi
                               WHERE yksiloiva_tunniste = '378bc7d7-4ec2-4fb9-96ca-29584cfd09fe'), -- MATERIAALI: Hiekoitushiekka, ennalta arvaamattomien kuljetusten avustaminen
      materiaaliluokka_id  = (SELECT id
                                FROM materiaaliluokka
                               WHERE materiaalityyppi = 'hiekoitushiekka'),
      muokattu             = current_timestamp,
      muokkaaja            = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE yksiloiva_tunniste   = 'ae67d2b5-a9d9-4880-a7ee-b3870737a177'; -- TEHTÄVÄ: Ennalta arvaamattomien kuljetusten avustaminen (materiaali)


UPDATE materiaalikoodi
SET nimi  = 'Talvisuolaliuos CaCl2, päällystettyjen teiden pölynsidonta',
    materiaalityyppi = (SELECT materiaalityyppi FROM materiaaliluokka WHERE nimi = 'Talvisuola'),
    materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola')
WHERE nimi = 'Kesäsuola päällystettyjen teiden pölynsidonta'; -- Vaihdetaan kesäsuolasta talvisuolaan 


UPDATE tehtava
SET materiaalikoodi_id  = NULL,
    "maaramitattava?"   = true, -- Tämä tehtävä on määrämitattava 
    yksikko             = 'tonni',
    suunnitteluyksikko  = 'tonni',
    muokattu            = current_timestamp,
    muokkaaja           = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi = 'Kesäsuola (CaCl2, materiaali)'; -- Anna null materiaalikoodi, halutaan kohdistaa kaikki luokan materiaalit  
