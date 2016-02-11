-- Talvihoitomittauksen muutospyynnöt (HAR-1709)

ALTER TABLE talvihoitomittaus ADD COLUMN lampotila_tie NUMERIC(6, 2);
ALTER TABLE talvihoitomittaus ADD COLUMN lampotila_ilma NUMERIC(6, 2);
UPDATE talvihoitomittaus SET lampotila_ilma = lampotila;
ALTER TABLE talvihoitomittaus DROP COLUMN lampotila;