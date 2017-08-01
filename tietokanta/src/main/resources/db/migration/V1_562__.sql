-- VV-urakan sanktiolajit

INSERT INTO sanktiotyyppi (nimi, sanktiolaji, urakkatyyppi)
VALUES
  ('Vesiväylän sakko', ARRAY['vesivayla_sakko'::sanktiolaji], ARRAY['vesivayla-hoito', 'vesivayla-ruoppaus', 'vesivayla-turvalaitteiden-korjaus', 'vesivayla-kanavien-hoito', 'vesivayla-kanavien-korjaus']::urakkatyyppi[]);