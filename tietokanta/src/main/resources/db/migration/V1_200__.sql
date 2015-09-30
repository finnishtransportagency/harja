-- Kuvaus: Lisää varustetoteumaan puuttuvat sarakkeet

ALTER TABLE varustetoteuma ADD COLUMN karttapvm date;
ALTER TABLE varustetoteuma ADD COLUMN tr_puoli integer;
ALTER TABLE varustetoteuma ADD COLUMN tierekisteriurakkakoodi integer;
ALTER TABLE varustetoteuma ADD COLUMN arvot varchar(4096);
UPDATE varustetoteuma SET arvot=ominaisuudet;
ALTER TABLE varustetoteuma DROP COLUMN ominaisuudet;