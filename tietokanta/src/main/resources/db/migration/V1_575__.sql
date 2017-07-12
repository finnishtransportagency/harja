CREATE TABLE reimari_toimenpide_liite (
  "toimenpide-id" INTEGER REFERENCES reimari_toimenpide (id) NOT NULL,
  liite INTEGER REFERENCES liite (id) NOT NULL,
  poistettu BOOLEAN NOT NULL DEFAULT FALSE
);
