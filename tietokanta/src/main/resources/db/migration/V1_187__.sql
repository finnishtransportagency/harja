-- Kuvaus: Tieosoiteverkon taulut ja indeksit
CREATE TABLE tieverkko (
       osoite3 integer NOT NULL,
       tie integer NOT NULL,
       ajorata integer NOT NULL,
       osa integer NOT NULL,
       tiepiiri integer NOT NULL,
       tr_pituus integer NOT NULL,
       hoitoluokka VARCHAR(4),
       geometria geometry NOT NULL,
       update_hash integer NOT NULL,
       
       PRIMARY KEY(osoite3, tie, ajorata, osa, tiepiiri, tr_pituus)
);

CREATE INDEX tieverkko_geom_index ON tieverkko USING GIST ( geometria ); 
