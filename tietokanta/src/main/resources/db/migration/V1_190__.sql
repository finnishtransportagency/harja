-- Nimi: Varustetoteumalle puuttuvat tiedot
ALTER TABLE varustetoteuma ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE varustetoteuma ADD COLUMN luotu timestamp DEFAULT current_timestamp;