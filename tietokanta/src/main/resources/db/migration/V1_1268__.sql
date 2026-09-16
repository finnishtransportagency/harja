-- Tehtävään pitää voida kirjata apin kautta
UPDATE tehtava
SET api_seuranta = true,
    muokattu     = current_date,
    muokkaaja    = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa (materiaali)'
  and poistettu is not true
  and "mhu-tehtava?" = true;
