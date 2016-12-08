-- Tallenna kaikki yksittäiset työkonehavainnot

DROP TRIGGER tg_paivita_tyokoneen_reitti ON tyokonehavainto;
DROP FUNCTION paivita_tyokoneen_reitti();
DROP FUNCTION tallenna_tai_paivita_tyokonehavainto(
     character varying, character varying, character varying, integer, timestamp,
     integer, character varying, point, real, integer, suoritettavatehtava[]);

-- Puretaan vanha primary key, koska havaintoja on nyt monta per työkone
ALTER TABLE tyokonehavainto DROP CONSTRAINT tyokonehavainto_pkey;

ALTER TABLE tyokonehavainto
 DROP COLUMN reitti,
 DROP COLUMN alkanut,
 DROP COLUMN edellinensijainti;

-- Indeksoi sijainti
CREATE INDEX tyokonehavainto_sijainti_idx ON tyokonehavainto USING GIST (sijainti);
