-- Poistetaan integraatiotaulusta poistetut rajapinnat 
DELETE FROM integraatio WHERE jarjestelma = 'api' AND nimi = 'ping-ulos';
DELETE FROM integraatio WHERE jarjestelma = 'api' AND nimi = 'hae-paivystajatiedot';
DELETE FROM integraatio WHERE jarjestelma = 'api' AND nimi = 'paivita-yllapitokohde';
DELETE FROM integraatio WHERE jarjestelma = 'api' AND nimi = 'kirjaa-paallystysilmoitus';
