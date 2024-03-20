CREATE TABLE rahavaraus
(
    id        SERIAL PRIMARY KEY,
    nimi      TEXT      NOT NULL,
    luotu     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja     INT       NOT NULL REFERENCES kayttaja (id),
    muokattu  TIMESTAMP,
    muokkaaja INT REFERENCES kayttaja (id)
);

CREATE TABLE rahavaraus_tehtava
(
    id            SERIAL PRIMARY KEY,
    rahavaraus_id INT REFERENCES rahavaraus (id),
    tehtava_id    INT REFERENCES tehtava (id),
    luotu         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja         INT       NOT NULL REFERENCES kayttaja (id),
    muokattu      TIMESTAMP,
    muokkaaja     INT REFERENCES kayttaja (id)
);

CREATE TABLE rahavaraus_urakka
(
    id                   SERIAL PRIMARY KEY,
    rahavaraus_id        INT REFERENCES rahavaraus (id),
    urakka_id            INT REFERENCES urakka (id),
    urakkakohtainen_nimi TEXT,
    luotu                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                INT       NOT NULL REFERENCES kayttaja (id),
    muokattu             TIMESTAMP,
    muokkaaja            INT REFERENCES kayttaja (id)
);

ALTER TABLE kustannusarvioitu_tyo
    ADD COLUMN rahavaraus_id INT REFERENCES rahavaraus (id);
ALTER TABLE kulu_kohdistus
    ADD COLUMN rahavaraus_id INT REFERENCES rahavaraus (id);

/* ### Rahavaraukset vanhoille urakoille ### */

INSERT INTO rahavaraus (nimi, luoja)
VALUES ('Äkilliset hoitotyöt', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Vahinkojen korvaukset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Kannustinjärjestelmä', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));


INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE yksiloiva_tunniste IN (
                             '1f12fe16-375e-49bf-9a95-4560326ce6cf', -- Äkillinen hoitotyö (talvihoito)
                             '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974', -- Äkillinen hoitotyö (l.ymp.hoito)
                             'd373c08b-32eb-4ac2-b817-04106b862fb1' -- Äkillinen hoitotyö (soratiet)
    )
  AND rv.nimi = 'Äkilliset hoitotyöt';

INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE yksiloiva_tunniste IN (
                             '49b7388b-419c-47fa-9b1b-3797f1fab21d', -- Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito),
                             '63a2585b-5597-43ea-945c-1b25b16a06e2', --Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito),
                             'b3a7a210-4ba6-4555-905c-fef7308dc5ec' --Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet),
    )
  AND rv.nimi = 'Vahinkojen korvaukset';

INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'
  AND rv.nimi = 'Kannustinjärjestelmä';

/* ### Uusien tehtävien lisääminen Espoolle ja Vantaan vuonna 24 alkaville urakoille (MH+) ### */

-- Näitä ei enää suunnitella erikseen johtuen velhon muutoksista
UPDATE tehtava
SET voimassaolo_loppuvuosi = 2023
WHERE nimi IN ('Ise ohituskaistat', 'Is ohituskaistat', 'Ib ohituskaistat', 'Ic ohituskaistat');


-- Uusia tehtäviä ja tehtäväryhmä rahavarauksia varten
INSERT INTO tehtavaryhma (nimi, jarjestys, nakyva, luotu, luoja, tehtavaryhmaotsikko_id)
VALUES ('Päällysteiden paikkaus (Y)', 101, FALSE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
        (SELECT id FROM tehtavaryhmaotsikko WHERE otsikko = '4 PÄÄLLYSTEIDEN PAIKKAUS'));

INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko,
                     hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Liikennemerkkipylvään tehostamismerkkien uusiminen', 'kpl', (SELECT id
                                                                      FROM tehtavaryhma
                                                                      WHERE nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),
        480, 2024, NULL, ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, FALSE, TRUE, FALSE,
        CURRENT_TIMESTAMP, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Maakivien (< 1 m3) poisto päällystetyltä tieltä', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Saumojen juottaminen bitumilla (Y6)'), 1348,
        2024, NULL, ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, FALSE, TRUE, FALSE,
        CURRENT_TIMESTAMP, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Muut päällysteiden paikkaukseen liittyvät työt', '-',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Päällysteiden paikkaus (Y)'), 1349, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, FALSE, TRUE, FALSE,
        CURRENT_TIMESTAMP, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

-- Rahavaraukset
INSERT INTO rahavaraus (nimi, luoja)
VALUES ('Rahavaraus A', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus B - Äkilliset hoitotyöt', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus C - Vahinkojen korjaukset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus D - Levähdys- ja P-alueet', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus E - Pysäkkikatokset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus F - Meluesteet', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus G - Juurakkopuhdistamo', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus H - Aidat', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus I - Sillat ja laiturit', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus J - Tunnelien pienet korjaukset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')),
       ('Rahavaraus K - Kannustinjärjestelmä', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM tehtava,
     rahavaraus rv
WHERE tehtava.nimi IN ('AB-paikkaus levittäjällä',
                       'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)',
                       'Avo-ojitus/soratiet (kaapeli kaivualueella)',
                       'Avo-ojitus/soratiet',
                       'KT-reikävaluasfalttipaikkaus',
                       'KT-valuasfalttipaikkaus K',
                       'KT-valuasfalttipaikkaus T',
                       'KT-valuasfalttisaumaus',
                       'Kaiteiden poisto ja uusiminen',
                       'Kalliokynsien louhinta ojituksen yhteydessä',
                       'Kannukaatosaumaus',
                       'Katupölynsidonta',
                       'Käsin tehtävät paikkaukset pikapaikkausmassalla',
                       'Laskuojat/päällystetyt tiet',
                       'Laskuojat/soratiet',
                       'Muut päällystettyjen teiden sorapientareiden kunnossapitoon liittyvät työt',
                       'Opastustaulujen ja opastusviittojen uusiminen portaaliin',
                       'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)',
                       'Opastustaulun/-viitan uusiminen',
                       'PAB-paikkaus käsin',
                       'Pysäkkikatoksen uusiminen',
                       'Pysäkkikatoksen poistaminen',
                       'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm',
                       'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm',
                       'Reunantäyttö',
                       'Reunapalteen poisto kaiteen alta',
                       'Reunapalteen poisto',
                       'Runkopuiden poisto',
                       'Sorateitä kaventava ojitus',
                       'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm',
                       'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm',
                       'Soratien runkokelirikkokorjaukset',
                       'Töherrysten estokäsittely',
                       'Töherrysten poisto',
                       'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)',
                       'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)',
                       'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki',
                       'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet',
                       'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet',
                       'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet',
                       'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet')
  AND rv.nimi = 'Rahavaraus A'
  AND "mhu-tehtava?" = TRUE
  AND tehtava.poistettu = FALSE;

-- Tilaajan rahavaraus B
INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rahavaraus.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus,
     tehtava
-- Äkilliset hoitotyöt
WHERE yksiloiva_tunniste = '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974';


-- Tilaajan rahavaraus C
INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rahavaraus.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus,
     tehtava
-- Kolmansien osapuolien vahingot
WHERE yksiloiva_tunniste = '63a2585b-5597-43ea-945c-1b25b16a06e2';


-- Tilaajan rahavaraus D + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Puhtaanapito (P)'), 501, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen'
  AND rv.nimi = 'Rahavaraus D - Levähdys- ja P-alueet';

-- Tilaajan rahavaraus E + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Puhtaanapito (P)'), 491, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen'
  AND rv.nimi = 'Rahavaraus E - Pysäkkikatokset';

-- Tilaajan rahavaraus F + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Meluesteiden pienten vaurioiden korjaaminen', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Puhtaanapito (P)'), 511, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Meluesteiden pienten vaurioiden korjaaminen'
  AND rv.nimi = 'Rahavaraus F - Meluesteet';

-- Tilaajan rahavaraus G + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'), 1080, 2024,
        NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne'
  AND rv.nimi = 'Rahavaraus G - Juurakkopuhdistamo';

-- Tilaajan rahavaraus H + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Aitojen vaurioiden korjaukset', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Kaiteet, aidat ja kivetykset (U)'), 971, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Aitojen vaurioiden korjaukset'
  AND rv.nimi = 'Rahavaraus H - Aidat';

-- Tilaajan rahavaraus I + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Siltakeilojen sidekiveysten purkaumien, suojaverkkojen ja kosketussuojaseinien pienet korjaukset',
        'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Sillat ja laiturit (I)'), 971, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Siltakeilojen sidekiveysten purkaumien, suojaverkkojen ja kosketussuojaseinien pienet korjaukset'
  AND rv.nimi = 'Rahavaraus I - Sillat ja laiturit';

-- Tilaajan rahavaraus J + rahavarauksen uudet tehtävät
INSERT INTO tehtava (nimi, yksikko, tehtavaryhma, jarjestys, voimassaolo_alkuvuosi, suunnitteluyksikko, hinnoittelu,
                     "mhu-tehtava?", kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, luotu, luoja)
VALUES ('Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt', 'kpl',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Muut, liikenneympäristön hoito (F)'), 1070, 2024, NULL,
        ARRAY ['kokonaishintainen']::hinnoittelutyyppi[], TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus rv,
     tehtava
WHERE tehtava.nimi = 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt'
  AND rv.nimi = 'Rahavaraus J - Tunnelien pienet korjaukset';


-- Tilaajan rahavaraus K
INSERT
INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rahavaraus.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM rahavaraus,
     tehtava
WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30';

-- Nimetään vanha tehtävä täsmäämään tehtävä- ja määräluetteloa. Vastaa kirjoitusasua vanhemmissakin urakoissa.
UPDATE tehtava
SET nimi = 'Tie- ja levähdysalueiden puhtaanapito ja jätehuolto'
WHERE nimi = 'Levähdysalueen puhtaanapito';


/* ### Populoidaan rahavaraus_urakka-taulu. Uudet urakat joudutaan populoimaan kun ne alkavat. ### */
INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
SELECT u.id,
       rv.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
FROM urakka u,
     rahavaraus rv
WHERE u.tyyppi = 'teiden-hoito'
  AND rv.nimi IN ('Äkilliset hoitotyöt',
                  'Vahinkojen korvaukset',
                  'Kannustinjärjestelmä');

CREATE OR REPLACE FUNCTION lisaa_urakan_oletus_rahavaraukset() RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
    SELECT NEW.id,
           rv.id,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
    FROM rahavaraus rv
    WHERE rv.nimi IN ('Äkilliset hoitotyöt',
                      'Vahinkojen korvaukset',
                      'Kannustinjärjestelmä');

    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_urakan_oletus_rahavaraukset
    AFTER INSERT
    ON urakka
    FOR EACH ROW
EXECUTE FUNCTION lisaa_urakan_oletus_rahavaraukset();

/*
 Uudet urakat tulevat toistaiseksi käyttämään samoja rahavarauksia kuin vanhatkin urakat, mutta tulossa on muutama, jotka käyttää uusia.
 Nämä urakat pitää populoida poistamalla ensin vanhat rahavaraukset ja lisäämällä uudet samaan tapaan kuin aiemmat, eli lisäämällä rahavaraus_urakka-tauluun
 rivit, joissa on urakan id ja uusien rahavarausten id:t, esim:

 INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id)
 SELECT :urakka_id,
        rahavaraus.id
 FROM rahavaraus
 WHERE rahavaraus.nimi IN ('Rahavaraus A', 'Rahavaraus B - Äkilliset hoitotyöt'...)
 */



