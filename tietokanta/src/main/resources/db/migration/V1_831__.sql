ALTER TABLE harja.public.toimenpidekoodi
ADD column "voimassaolo_alku" TIMESTAMP,
ADD column "voimassaolo_loppu" TIMESTAMP;

COMMENT ON COLUMN toimenpidekoodi.voimassaolo_alku IS E'Ajankohta jolloin tehtävä on otettu käyttöön uusissa urakoissa. Tehtävää ei oteta käyttöön vanhoissa urakoissa.';
COMMENT ON COLUMN toimenpidekoodi.voimassaolo_loppu IS E'Ajankohta jolloin tehtävä ei ole enää käytössä uusissa urakoissa. Tehtävä on kuitenkin käytössä jo alkaneissa urakoissa.';

