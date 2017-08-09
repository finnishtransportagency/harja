-- Päivitä vesiväylien hallintayksiköiden nimet (HAR-5826)

UPDATE organisaatio SET nimi = 'Kanavat ja avattavat sillat' WHERE nimi = 'Kanava';
UPDATE organisaatio SET nimi = 'Sisävesiväylät' WHERE nimi = 'Sisävesi';