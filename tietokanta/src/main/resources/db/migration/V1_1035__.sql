-- Lisätään uusia tehtäviä ja päivitetään vanhoja.
-- Näiden tehtävien toteumat halutaan saada reittitoeumarajapintaan ja joissakin tapauksissa myös työkoneseurantaan.

-- TALVIHOITO
-- Talvihoito (A)
-- Sohjo-ojien teko. Kerätään reittitoteuma ja seurataan reaaliaikaisesti työkoneseurannassa.
INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, ensisijainen, luotu, luoja)
VALUES ('Sohjo-ojien teko', (select id from toimenpide where koodi = '23104'), 'jkm', 'jkm',
        (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), 330, '{kokonaishintainen}', TRUE, NULL,
        'sohjo-ojien teko', TRUE, current_timestamp, (select id from kayttaja where kayttajanimi = 'LX377737'));
UPDATE tehtava SET api_tunnus = id WHERE nimi = 'Sohjo-ojien teko';

-- LIIKENNEYMPÄRISTÖN HOITO
-- Puhtaanapito (P)

-- Roskien keruu, tiet. Kerätään reittitoteuma.
UPDATE tehtava set nimi = 'Roskien keruu, tiet',
                   jarjestys = 505,
                   tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),
                   muokattu = current_timestamp,
                   muokkaaja = (select id from kayttaja where kayttajanimi = 'LX377737')
WHERE nimi = 'Roskien poisto ja kevätsiivoukset taajamat, kevarit ja päätiet';

-- Päällystettyjen teiden pölynsidonta. Kerätään reittitoteuma ja seurataan reaaliaikaisesti työkoneseurannassa.

INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, ensisijainen, aluetieto, luotu, luoja)
VALUES ('Päällystettyjen teiden pölynsidonta (jkm)', (select id from toimenpide where koodi = '23116'), 'jkm', 'jkm',
        (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), 551, '{kokonaishintainen}', TRUE, NULL,
        'paallystetyn tien polynsidonta', TRUE, TRUE, current_timestamp, (select id from kayttaja where kayttajanimi = 'LX377737'));
UPDATE tehtava SET api_tunnus = id WHERE nimi = 'Päällystettyjen teiden pölynsidonta (jkm)';

-- Päällystettyjen teiden pölynsidonta. Päivitetään tiedot myös vastaavan käsin seurattavaan tehtävään.
UPDATE tehtava SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),
                   suunnitteluyksikko = 'krt/vuosi',
                   jarjestys = 550,
                   kasin_lisattava_maara = true,
                   aluetieto = true,
                   api_seuranta = false,
                   muokattu = current_timestamp,
                   muokkaaja = (select id from kayttaja where kayttajanimi = 'LX377737')
WHERE nimi = 'Päällystettyjen teiden pölynsidonta';

-- Päällystettyjen teiden palteiden poisto
INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, ensisijainen, aluetieto, luotu, luoja)
VALUES ('Reunapalteen poisto', (select id from toimenpide where koodi = '23116'), 'jkm', 'jkm',
        (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'), 551, '{kokonaishintainen}', TRUE, NULL,
        'palteen poisto', TRUE, TRUE, current_timestamp, (select id from kayttaja where kayttajanimi = 'LX377737'));
UPDATE tehtava SET api_tunnus = id WHERE nimi = 'Reunapalteen poisto';

-- Päällystettyjen teiden palteiden poisto, kaiteen alta
UPDATE tehtava
SET suoritettavatehtava = 'palteen poisto kaiteen alta',
    api_seuranta = true,
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'LX377737')
WHERE nimi = 'Reunapalteen poisto kaiteen alta';

-- Reunapaalujen uusiminen
UPDATE tehtava
SET api_seuranta = true,
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'LX377737')
WHERE nimi = 'Reunapaalujen uusiminen' AND emo = (select id from toimenpide where koodi = '23116');

-- Liikenteen varmistaminen kelirikkokohteessa
UPDATE tehtava
SET suoritettavatehtava = 'liikenteen varmistaminen kelirikkokohteessa',
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'LX377737')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa';

-- SORATEIDEN HOITO
-- Ojitus
INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, ensisijainen, aluetieto, luotu, luoja)
VALUES ('Ojitus', (select id from toimenpide where koodi = '23124'), 'jkm', 'jkm',
        (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), 551, '{kokonaishintainen}', TRUE, NULL,
        'ojitus', TRUE, TRUE, current_timestamp, (select id from kayttaja where kayttajanimi = 'LX377737'));
UPDATE tehtava SET api_tunnus = id WHERE nimi = 'Ojitus';


