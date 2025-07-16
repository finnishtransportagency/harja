-- Päivitetään valaistusurakoiden tehtäville kaikkiin ympäristöihin sama api_tunnus kuin tuotannossa.
-- Rajapintoja kutsuvien järjestelmien ei tämän jälkeen tarvitse huolehtia eri tehtävä-id:n konffaamisesta testiympäristössä.
UPDATE tehtava SET api_tunnus = 28341, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio' ) WHERE nimi = 'Ryhmävaihto' and emo = (select id from toimenpide where koodi = 'VALA_YKSHINT');
UPDATE tehtava SET api_tunnus = 28342, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE nimi = 'Huoltokierros' and emo = (select id from toimenpide where koodi = 'VALA_YKSHINT');
UPDATE tehtava SET api_tunnus = 28343, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio' ) WHERE nimi = 'Muut toimenpiteet' and emo = (select id from toimenpide where koodi = 'VALA_YKSHINT');
