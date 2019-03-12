UPDATE kayttaja
set sahkoposti = REPLACE(sahkoposti, '@liikennevirasto.fi', '@vayla.fi'),
    muokattu = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE sahkoposti like ('%@liikennevirasto.fi');