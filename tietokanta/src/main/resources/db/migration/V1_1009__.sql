CREATE TABLE IF NOT EXISTS kan_lt_ketjutus_aktivointi
( 
	sopimus_id          SERIAL PRIMARY KEY REFERENCES sopimus (id) UNIQUE,
	ketjutus_kaytossa  	boolean
);

COMMENT ON TABLE kan_lt_ketjutus_aktivointi IS 
'Kanavakohteiden liikennetapahtumien ketjutuksen aktivointi'
