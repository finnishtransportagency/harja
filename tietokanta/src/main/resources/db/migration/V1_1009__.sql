CREATE TABLE IF NOT EXISTS kan_lt_ketjutus_aktivointi
( 
	sopimus_id          SERIAL PRIMARY KEY REFERENCES sopimus (id) UNIQUE,
	sampoid           	VARCHAR(32) REFERENCES sopimus (sampoid),
	ketjutus_kaytossa  	boolean
);

COMMENT ON TABLE kan_lt_ketjutus_aktivointi IS 
'Kanavakohteiden liikennetapahtumien ketjutuksen aktivointi'
