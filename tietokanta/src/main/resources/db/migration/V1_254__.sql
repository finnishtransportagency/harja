DELETE FROM integraatio
WHERE jarjestelma = 'ptj' AND nimi = 'hoidon-alueurakat-muutospaivamaaran-haku';

DELETE FROM geometriapaivitys
WHERE nimi = 'hoidon-alueurakat';

INSERT INTO geometriapaivitys (nimi) VALUES ('urakat');