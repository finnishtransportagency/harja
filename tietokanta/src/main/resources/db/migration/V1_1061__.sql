-- Päivitetään tehtävän nimi (Tunnelit) vastaamaan käyttöliittymässä rahavarauksen yhteydessä käytettyä nimeä (Tunneleiden hoito).
-- Rahavaraus linkitetään tehtävään taulussa kustannusarvoitu_tyo.
UPDATE tehtava
SET nimi      = 'Tunneleiden hoito',
    muokattu  = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE yksiloiva_tunniste = '4342cd30-a9b7-4194-94ee-00c0ce1f6fc6';
