-- Ensisijainen tehtävä viittaa mh-urakoiden tehtäviin. Nimetään se kuvaavammin
ALTER TABLE tehtava
    RENAME ensisijainen TO "mhu-tehtava?";

-- Luodaan uusi taulu, jolla ryhmitellään tehtäväryhmät
CREATE TABLE tehtavaryhmaotsikko (
    id serial primary key,
    otsikko  text,
    luotu timestamp,
    luoja integer references kayttaja(id),
    muokattu timestamp,
    muokkaaja integer references kayttaja(id),
    unique (otsikko));

-- Lisätään tehtäväryhmiltä otsikot tehtäväryhmäotsikko tauluun
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('1.0 TALVIHOITO',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.3 LIIKENNEYMPÄRISTÖN HOITO / Viheralueiden hoito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.4 LIIKENNEYMPÄRISTÖN HOITO / Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.6 LIIKENNEYMPÄRISTÖN HOITO / Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällystettyjen teiden sorapientareen kunnossapito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('2.8 LIIKENNEYMPÄRISTÖN HOITO / Siltojen ja laitureiden hoito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('3 SORATEIDEN HOITO',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('4 PÄÄLLYSTEIDEN PAIKKAUS',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('5 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('6.1 YLLÄPITO / Rumpujen uusiminen',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('6.2 YLLÄPITO / Avo-ojien kunnossapito',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('7 KORVAUSINVESTOINTI',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('8 MUUTA',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO tehtavaryhmaotsikko (otsikko, luotu, luoja) VALUES ('9 LISÄTYÖT',now(),(select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Lisätään tehtäväryhmä-taululle linkitys uuteen tehtäväryhmäotsikko tauluun
ALTER TABLE tehtavaryhma
    ADD COLUMN tehtavaryhmaotsikko_id integer;

UPDATE tehtavaryhma tr
   SET tehtavaryhmaotsikko_id = (select id from tehtavaryhmaotsikko where otsikko = tr.otsikko);

ALTER TABLE tehtavaryhma
    ALTER COLUMN tehtavaryhmaotsikko_id SET NOT NULL;
