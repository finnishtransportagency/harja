CREATE TABLE kan_toimenpide_hinta (
  toimenpide INTEGER
             NOT NULL
             REFERENCES kan_toimenpide(id),
  hinta      INTEGER
             NOT NULL
             REFERENCES kan_hinta(id),
  CONSTRAINT uniikki_kan_toimenpiteen_hinta UNIQUE (toimenpide,  hinta));
