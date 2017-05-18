-- Muuta ylläpitokohde omaksi linkkitauluksi

CREATE TABLE tarkastus_yllapitokohde (
  tarkastus integer PRIMARY KEY, -- ei voi olla viiteavain, koska tarkastus taulu shardattu
  yllapitokohde integer REFERENCES yllapitokohde (id)
);

CREATE INDEX tarkastus_yllapitokohde_ypk_idx ON tarkastus_yllapitokohde (yllapitokohde);

INSERT INTO tarkastus_yllapitokohde (tarkastus, yllapitokohde)
  SELECT id, yllapitokohde FROM tarkastus
   WHERE yllapitokohde IS NOT NULL AND
         yllapitokohde IN (SELECT id FROM yllapitokohde);
