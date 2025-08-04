-- Korjaa tilannekuvan toteumien näkyminen lisäämällä suoritettavatehtava tehtavalle
UPDATE tehtava
SET suoritettavatehtava = 'liik. opast. ja ohjausl. hoito seka reunapaalujen kun.pito',
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Liikennemerkkien, opasteiden ja liikenteenohjauslaitteiden hoito sekä reunapaalujen kp';
