-- Ensisijainen tehtävä viittaa mh-urakoiden tehtäviin. Nimetään se kuvaavammin
ALTER TABLE tehtava
    RENAME ensisijainen TO "mhu-tehtava?";

-- Luodaan uusi taulu, jolla ryhmitellään tehtäväryhmät
CREATE TABLE tehtavaryhmaotsikko
(
    id        SERIAL PRIMARY KEY,
    otsikko   TEXT NOT NULL UNIQUE,
    luotu     TIMESTAMP,
    luoja     INTEGER REFERENCES kayttaja (id),
    muokattu  TIMESTAMP,
    muokkaaja INTEGER REFERENCES kayttaja (id)
);

-- Lisätään tehtäväryhmiltä otsikot tehtäväryhmäotsikko tauluun
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('1.0 TALVIHOITO', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen',
        NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.3 LIIKENNEYMPÄRISTÖN HOITO / Viheralueiden hoito', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.4 LIIKENNEYMPÄRISTÖN HOITO / Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.5 LIIKENNEYMPÄRISTÖN HOITO / Rumpujen kunnossapito ja uusiminen', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.6 LIIKENNEYMPÄRISTÖN HOITO / Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällystettyjen teiden sorapientareen kunnossapito', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.7 LIIKENNEYMPÄRISTÖN HOITO', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus paikkaukset siirretään lymp:sta korjaukseen / ASIAKIRJAMUUTOKSET',
        NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('2.8 LIIKENNEYMPÄRISTÖN HOITO / Siltojen ja laitureiden hoito', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('3 SORATEIDEN HOITO', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('4 PÄÄLLYSTEIDEN PAIKKAUS', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('5 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA', NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('6.1 YLLÄPITO / Rumpujen uusiminen', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('6.2 YLLÄPITO / Avo-ojien kunnossapito', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('7 KORVAUSINVESTOINTI', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('8 MUUTA', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja)
VALUES ('9 LISÄTYÖT', NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

-- Lisätään tehtäväryhmä-taululle linkitys uuteen tehtäväryhmäotsikko tauluun
ALTER TABLE tehtavaryhma
    ADD COLUMN tehtavaryhmaotsikko_id INTEGER;

UPDATE tehtavaryhma tr
   SET tehtavaryhmaotsikko_id = (SELECT id FROM tehtavaryhmaotsikko WHERE otsikko = tr.otsikko);

ALTER TABLE tehtavaryhma
    ALTER COLUMN tehtavaryhmaotsikko_id SET NOT NULL;
