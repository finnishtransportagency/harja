-- Kuvaus: Lisää varustetoteumaan puuttuvat sarakkeet

SELECT * FROM varustetoteuma;

ALTER TABLE varustetoteuma ADD COLUMN karttapvm date;
ALTER TABLE varustetoteuma ADD COLUMN tr_puoli integer;
ALTER TABLE varustetoteuma ADD COLUMN tr_ajorata integer;
ALTER TABLE varustetoteuma ADD COLUMN alkupvm date;
ALTER TABLE varustetoteuma ADD COLUMN loppupvm date;
ALTER TABLE varustetoteuma ADD COLUMN arvot varchar(4096);
ALTER TABLE varustetoteuma ADD COLUMN tierekisteriurakkakoodi integer;
UPDATE varustetoteuma SET arvot=ominaisuudet;
ALTER TABLE varustetoteuma DROP COLUMN ominaisuudet;