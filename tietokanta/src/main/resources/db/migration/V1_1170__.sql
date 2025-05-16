-- laskut taulu harjoitustyötä varten
CREATE TABLE IF NOT EXISTS kulut_laskut (
    id SERIAL PRIMARY KEY,
    toimenpide TEXT,
    maara NUMERIC,
    pvm DATE
);
