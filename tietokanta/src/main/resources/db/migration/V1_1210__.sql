
-- TEHTÄVIEN TIETOJEN PÄIVITYS
-- Varmistetaan että asiaan liittyvät tehtävät linkittyvät materiaaliin ja materiaaliluokkaan oikein.
-- Lisätään tehtävälle samalla yksilöivä tunnista.

-- Liukkaudentorjunta hiekoituksella (materiaali)
UPDATE tehtava
SET yksiloiva_tunniste  = 'b3e39662-5bb3-4dc1-9d30-95d5692118f4',
    materiaalikoodi_id  = (select id
                           from materiaalikoodi
                           where yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5'), -- Hiekoitushiekka, liukkaudentorjunta
    materiaaliluokka_id = (select id
                           from materiaaliluokka
                           where materiaalityyppi = 'hiekoitushiekka'),
    muokattu = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Liukkaudentorjunta hiekoituksella (materiaali)';

-- Täydennetään ja varmistetaan tietojen oikeellisuus: Ennalta arvaamattomien kuljetusten avustaminen (materiaali)
UPDATE tehtava
SET yksiloiva_tunniste  = 'ae67d2b5-a9d9-4880-a7ee-b3870737a177',
    materiaalikoodi_id  = (select id
                           from materiaalikoodi
                           where yksiloiva_tunniste = '378bc7d7-4ec2-4fb9-96ca-29584cfd09fe'), -- Hiekoitushiekka, ennalta arvaamattomien kuljetusten avustaminen
    materiaaliluokka_id = (select id
                           from materiaaliluokka
                           where materiaalityyppi = 'hiekoitushiekka'),
    muokattu = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen (materiaali)';

-- TEHTÄVÄRYHMÄKOJRAUS

-- Päivitä Ennalta arvaamattomien kuljetusten avustamisen kilometritoteumaa seuraavan tehtävän tehtäväryhmä
-- A - Talvihoito => B4 - Ennalta arvaamattomien kuljetusten avustaminen hiekoituksella (materiaali)

-- (materiaali) tehtäväryhmän nimessä tarkoittaa, että kuluja kirjatessa ollaan kiinnostuneita käytetyn materiaalin kuluista.
-- Kilometritoteumatehtävääkään ei kannata pitää talvihoidon alla. Se on vain kylkiäistieto, kun seuranta kohdistuu
-- ensisijaisesti materiaaliin. Pidetään samaan työsuoritteeseen liittyvät tehtävät saman tehtäväryhmän alla.

-- Lisätään tehtävän nimeen täsmenne (km). Materiaalimäärää seuraavalla tehtävällä täsmenne on (materiaali)

-- Muutoksella ei ole vaikutusta historiatietoihin, koska ennalta arvaamattomien kuljetusten toteumia ei ole kirjattu aiemmin.

UPDATE tehtava
SET nimi = 'Ennalta arvaamattomien kuljetusten avustaminen (km)',
    yksiloiva_tunniste  = 'c3ada25e-70f2-407b-8dff-2c1a303578be',
    tehtavaryhma = (select id from tehtavaryhma where tehtava.yksiloiva_tunniste = '3a5cb840-11a7-438f-bdae-a87da64bf98a'), -- B4 - Ennalta arvaamattomien kuljetusten avustaminen hiekoituksella (materiaali)
    muokattu = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen';
