-- Korjaa oikea tehtävä 'Liikenteen varmistaminen kelirikkokohteessa (materiaali)' määrämitattavaksi
UPDATE tehtava
SET "maaramitattava?"      = true,
    muokattu               = current_timestamp,
    muokkaaja              = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa (materiaali)';