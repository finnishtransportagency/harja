-- Päivitetään valaistusurakoiden tehtäville kaikkiin ympäristöihin sama api_tunnus kuin tuotannossa.
-- Rajapintoja kutsuvien järjestelmien ei tämän jälkeen tarvitse huolehtia eri tehtävä-id:n konffaamisesta testiympäristössä.
UPDATE tehtava SET api_tunnus = 28341, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio' ) WHERE nimi = 'Ryhmävaihto';
UPDATE tehtava SET api_tunnus = 28342, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE nimi = 'Huoltokierros';
UPDATE tehtava SET api_tunnus = 28343, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio' ) WHERE nimi = 'Muut toimenpiteet';
