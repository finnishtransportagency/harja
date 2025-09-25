-- HIEKOITUSHIEKKA-MATERIAALIN JAKAMINEN KAHDEKSI

-- Päivitetään ensin materiaalikoodeille yksilöivät tunnisteet
-- Siirrytään käyttämään yksilöiviä tunnisteita, kun koodissa täytyy hakea tietty materiaalikoodi, niin nimen muuttaminen ei vaadi jatkossa yhtä paljon koodimuutoksia.
-- Nimen muuttaminen voi silti aiheuttaa koodimuutostarpeita jatkossakin esimerkiksi käyttöliittymässä käyttäjille esitettäviin teksteihin ellei toteutustapaa niiden kohdalla muuteta.
-- Uuden materiaalin lisääminen aiheuttaa myös tarpeita koodin päivittämiseen.
UPDATE materiaalikoodi SET yksiloiva_tunniste = '8d753e69-c074-4f22-9bbe-2a737133496e' WHERE nimi = 'Talvisuola, rakeinen NaCl';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '002266c5-d26d-4986-a8a2-f80d1d04fa68' WHERE nimi = 'Talvisuolaliuos CaCl2';
UPDATE materiaalikoodi SET yksiloiva_tunniste = 'd665f6cb-4df2-44e9-b3aa-ffd8c9bb3333' WHERE nimi = 'Talvisuolaliuos NaCl';
UPDATE materiaalikoodi SET yksiloiva_tunniste = 'e84c45bd-431e-427c-b50f-cf03bcaac6f1' WHERE nimi = 'Erityisalueet CaCl2-liuos';
UPDATE materiaalikoodi SET yksiloiva_tunniste = 'c0d99173-0b01-431e-9df7-3dc6b8e561cf' WHERE nimi = 'Erityisalueet NaCl';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '6cd4941f-92f9-4725-8403-691d56c3baaa' WHERE nimi = 'Erityisalueet NaCl-liuos';
UPDATE materiaalikoodi SET yksiloiva_tunniste = 'c396c247-b041-4b54-8e5c-70439556e894' WHERE nimi = 'Hiekoitushiekan suola';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '750f41d3-08e5-4533-b585-5698e165bd28' WHERE nimi = 'Kaliumformiaattiliuos';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '4aa0f5ae-8950-4881-b3af-c86916f75041' WHERE nimi = 'Natriumformiaatti';
UPDATE materiaalikoodi SET yksiloiva_tunniste = 'a0b81002-9062-4495-bf91-98025318be1f' WHERE nimi = 'Natriumformiaattiliuos';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '35b5ae59-d430-419d-9706-b3d9e3493dda' WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '710231cf-42a0-4c10-8151-013f71b899ea' WHERE nimi = 'Kesäsuola sorateiden pölynsidonta';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '73bdf468-b17f-4a7c-9ac6-cb67555c8af0' WHERE nimi = 'Kesäsuola päällystettyjen teiden pölynsidonta';
UPDATE materiaalikoodi SET yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5' WHERE nimi = 'Hiekoitushiekka'; -- Hiekoitushiekka, liukkaudentorjunta
UPDATE materiaalikoodi SET yksiloiva_tunniste = '66d82a9d-b5e5-4d42-8546-99e37f5b939c' WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '7f440adb-6e36-429a-ab15-4b3b34c5030f' WHERE nimi = 'Rikkaruohojen torjunta-aineet';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '4eb4e506-8edd-4fdc-b274-b8d03a1b9080' WHERE nimi = 'Sorastusmurske';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '0c991ac5-4221-42e5-8a88-bacddb78b9de' WHERE nimi = 'Reunantäyttömurske';
UPDATE materiaalikoodi SET yksiloiva_tunniste = '6bbe4261-1e22-43ec-a4c4-ae63ecc46b5d' WHERE nimi = 'Kelirikkomurske';

-- UUSI MATERIAALI: Hiekoitushiekka - ennalta arvaamattomien kuljetusten avustaminen.
-- VANHAN MATERIAALIN NIMIMUUTOS: Hiekoitushiekka - liukkaudentorjunta

-- Lisätään uusi materiaali
INSERT INTO materiaalikoodi (nimi, yksikko, kohdistettava, materiaalityyppi, urakkatyyppi, jarjestys,
                             materiaaliluokka_id, yksiloiva_tunniste)
VALUES ('Hiekoitushiekka, ennalta arvaamattomien kuljetusten avustaminen', 't', false, 'hiekoitushiekka', 'hoito', 17,
        (select id from materiaaliluokka where materiaalityyppi = 'hiekoitushiekka'), '378bc7d7-4ec2-4fb9-96ca-29584cfd09fe');

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
