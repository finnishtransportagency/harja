-- Arvonvähennys-lomakkeen tarvitsemat lisäsarakkeet sanktio-tauluun

ALTER TABLE sanktio
  ADD COLUMN maaraystapa TEXT,
  ADD COLUMN vaikuttaatavoitehintaan BOOLEAN,
  ADD COLUMN tavoitehinnanalennus NUMERIC,
  ADD COLUMN tehtavaryhma INTEGER REFERENCES tehtavaryhma (id),
  ADD COLUMN tehtava INTEGER REFERENCES tehtava (id);

COMMENT ON COLUMN sanktio.maaraystapa IS 'Arvonvähennyksen määräystapa: tyomaakokous tai valikatselmus';
COMMENT ON COLUMN sanktio.vaikuttaatavoitehintaan IS 'Vaikuttaako arvonvähennys tavoitehintaan';
COMMENT ON COLUMN sanktio.tavoitehinnanalennus IS 'Tavoitehinnan alennus euroissa';
COMMENT ON COLUMN sanktio.tehtavaryhma IS 'Arvonvähennyksen tehtäväryhmä';
COMMENT ON COLUMN sanktio.tehtava IS 'Arvonvähennyksen tehtävä (toimenpidekoodi taso 4)';

