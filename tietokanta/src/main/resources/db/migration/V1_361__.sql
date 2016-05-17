-- Lisää laadunalitus boolean
ALTER TABLE tarkastus
  ADD laadunalitus boolean NOT NULL DEFAULT false;

-- Päivitä vanhan säännön mukaisesti: jos tekstiä kirjattu havaintoon -> laadunalitus
UPDATE tarkastus
   SET laadunalitus = true
 WHERE trim(lower(havainnot)) NOT IN ('','ok');