-- HARJA-825 : Päivitetään tehtävän 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt' väärin määritelty tehtäväryhmä
UPDATE tehtava
SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Muut, liikenneympäristön hoito (F)'),
    muokattu     = current_timestamp,
    muokkaaja    = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt'
  AND emo = (select id from toimenpide where koodi = '23116');
