CREATE TABLE IF NOT EXISTS koulutusvideot
( 
	id                SERIAL PRIMARY KEY,
	otsikko           VARCHAR(128),
	linkki            VARCHAR(128),
	pvm               DATE
);

COMMENT ON TABLE koulutusvideot IS 
'Koulutusvideoiden siirto julkiselta sivulta harjan sisäiseen';