-- Luo lähde enum
CREATE TYPE lahde AS ENUM ('harja-ls-mobiili', 'harja-ui', 'harja-api');

-- ****
-- TARKASTUKSET

-- Lisää tarkastuksille lähde. Aluksi default arvo on harja-ui
ALTER TABLE tarkastus
    ADD COLUMN lahde lahde NOT NULL DEFAULT 'harja-ui'::lahde;

-- Lähde on mobiili, jos tarkastusajo löytyy
UPDATE tarkastus
  SET lahde='harja-ls-mobiili'::lahde
WHERE id IN
(SELECT tarkastus.id
FROM tarkastus
JOIN tarkastusajo ON tarkastus.tarkastusajo = tarkastusajo.id);

-- Lähde on harja-api, jos käyttäjä on järjestelmä
UPDATE tarkastus
  SET lahde='harja-api'::lahde
WHERE id IN
      (SELECT t.id FROM tarkastus t
      JOIN kayttaja k ON t.luoja = k.id
      WHERE k.jarjestelma IS TRUE);

-- Ei tarvetta lisätä eksplisiittisesti harja-ui lähdettä; on default arvo.

-- Pudota pois harja-ui default arvo. Nyt kaikilla tarkastuksilla on lähde
ALTER TABLE tarkastus
  ALTER COLUMN lahde DROP DEFAULT;

-- ******
-- TOTEUMAT

ALTER TABLE toteuma
    ADD COLUMN lahde lahde NOT NULL DEFAULT 'harja-ui'::lahde;

UPDATE toteuma
SET lahde='harja-api'::lahde
WHERE id IN
      (SELECT t.id FROM toteuma t
        JOIN kayttaja k ON t.luoja = k.id
      WHERE k.jarjestelma IS TRUE);

ALTER TABLE toteuma
    ALTER COLUMN lahde DROP DEFAULT;

-- ******
-- LAATUPOIKKEAMAT

ALTER TABLE laatupoikkeama
  ADD COLUMN lahde lahde NOT NULL DEFAULT 'harja-ui'::lahde;

UPDATE laatupoikkeama
SET lahde='harja-api'::lahde
WHERE id IN
      (SELECT t.id FROM laatupoikkeama t
        JOIN kayttaja k ON t.luoja = k.id
      WHERE k.jarjestelma IS TRUE);

ALTER TABLE laatupoikkeama
  ALTER COLUMN lahde DROP DEFAULT;

-- ******
-- SILTATARKASTUKSET

ALTER TABLE siltatarkastus
  ADD COLUMN lahde lahde NOT NULL DEFAULT 'harja-ui'::lahde;

UPDATE siltatarkastus
SET lahde='harja-api'::lahde
WHERE id IN
      (SELECT t.id FROM siltatarkastus t
        JOIN kayttaja k ON t.luoja = k.id
      WHERE k.jarjestelma IS TRUE);

ALTER TABLE siltatarkastus
  ALTER COLUMN lahde DROP DEFAULT;

-- ******
-- TURPOT

ALTER TABLE turvallisuuspoikkeama
  ADD COLUMN lahde lahde NOT NULL DEFAULT 'harja-ui'::lahde;

UPDATE turvallisuuspoikkeama
SET lahde='harja-api'::lahde
WHERE id IN
      (SELECT t.id FROM turvallisuuspoikkeama t
        JOIN kayttaja k ON t.luoja = k.id
      WHERE k.jarjestelma IS TRUE);

ALTER TABLE turvallisuuspoikkeama
  ALTER COLUMN lahde DROP DEFAULT;

-- ******
-- LIITTEET

ALTER TABLE liite
  ADD COLUMN lahde lahde NOT NULL DEFAULT 'harja-ui'::lahde;

-- Liitteen lähde on mobiili, jos liite liittyy tarkastukseen jolla on tarkastusajo
UPDATE liite
  SET lahde='harja-ls-mobiili'::lahde
WHERE id IN
      (SELECT l.id FROM
        liite l
        JOIN tarkastus_liite ON l.id = tarkastus_liite.liite
        JOIN tarkastus t ON tarkastus_liite.tarkastus = t.id
        JOIN tarkastusajo ta ON t.tarkastusajo = ta.id);

UPDATE liite
SET lahde='harja-api'::lahde
WHERE id IN
      (SELECT t.id FROM liite t
        JOIN kayttaja k ON t.luoja = k.id
      WHERE k.jarjestelma IS TRUE);

ALTER TABLE liite
  ALTER COLUMN lahde DROP DEFAULT;
