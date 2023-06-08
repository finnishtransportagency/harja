CREATE TABLE IF NOT EXISTS ilmoitus_kuvat
( 
  -- Kuva idtä ei ehkä tarvitse 
	id       SERIAL PRIMARY KEY,
	ilmoitus INTEGER REFERENCES ilmoitus (id),
	linkki   VARCHAR(512)
);
