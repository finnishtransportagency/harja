-- Siivotaan pois menetetyt integraatiot
-- Geometrian muutospäivämäärää ei voi enää lukea palvelimelta
DELETE FROM integraatio WHERE jarjestelma = 'ptj' AND nimi like ('%muutospaivamaaran-haku%');
