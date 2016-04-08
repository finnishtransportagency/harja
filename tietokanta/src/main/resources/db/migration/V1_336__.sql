<<<<<<< HEAD
CREATE TABLE tarkastus_laatupoikkeama (
  tarkastus INTEGER REFERENCES tarkastus (id)      NOT NULL,
  laatupoikkeama INTEGER REFERENCES laatupoikkeama (id) NOT NULL,
  CONSTRAINT uniikki_tarkastuksen_laatupoikkeama UNIQUE (tarkastus, laatupoikkeama)
);
=======
CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle_noex(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
BEGIN
   RETURN tierekisteriosoite_pisteelle(piste, treshold);
EXCEPTION
   WHEN OTHERS THEN RETURN NULL;
END;
$$ LANGUAGE plpgsql;
>>>>>>> develop
