-- Lisätään MH-urakkaan uusi tehtäväryhmä ja linkitetään siihen liittyvät tehtävät
INSERT INTO tehtavaryhma (otsikko, nimi, jarjestys, tehtavaryhmaotsikko_id, luotu, luoja) VALUES ('3 SORATEIDEN HOITO', 'Liikenteen varmistaminen kelirikkokohteessa (M)', 122, 9, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

UPDATE tehtava
SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikenteen varmistaminen kelirikkokohteessa (M)'),
    muokattu     = current_timestamp,
    muokkaaja    = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi IN ('Liikenteen varmistaminen kelirikkokohteessa', 'Liikenteen varmistaminen kelirikkokohteessa (tonni)');
