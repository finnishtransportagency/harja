ALTER TABLE organisaatio ADD COLUMN sampo_ely_hash VARCHAR(128);

UPDATE organisaatio SET sampo_ely_hash = 'KP911303' WHERE nimi = 'Uusimaa';
UPDATE organisaatio SET sampo_ely_hash = 'KP921303' WHERE nimi = 'Varsinais-Suomi';
UPDATE organisaatio SET sampo_ely_hash = 'KP931303' WHERE nimi = 'Pirkanmaa';
UPDATE organisaatio SET sampo_ely_hash = 'KP941303' WHERE nimi = 'Kaakkois-Suomi';
UPDATE organisaatio SET sampo_ely_hash = 'KP951303' WHERE nimi = 'Pohjois-Savo';
UPDATE organisaatio SET sampo_ely_hash = 'KP961303' WHERE nimi = 'Keski-Suomi';
UPDATE organisaatio SET sampo_ely_hash = 'KP971303' WHERE nimi = 'Etelä-Pohjanmaa';
UPDATE organisaatio SET sampo_ely_hash = 'KP981303' WHERE nimi = 'Pohjois-Pohjanmaa';
UPDATE organisaatio SET sampo_ely_hash = 'KP991303' WHERE nimi = 'Lappi';