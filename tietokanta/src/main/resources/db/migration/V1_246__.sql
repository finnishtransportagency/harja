-- Poista ylimääräiset integraatiot
DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'elyt-haku';

DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'elyt-muutospaivamaaran-haku';

DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'hoitoluokat-haku';

DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'hoitoluokat-muutospaivamaaran-haku';

DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'hoidon-alueurakat-haku';

DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'hoidon-alueurakat-haku';