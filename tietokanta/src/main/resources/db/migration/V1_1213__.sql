-- Muuta rahavaraus-tauluun jarjestys-sarake, joka on automaattisesti kasvava
-- ja täytä sarake nykyisillä arvoilla.
-- Tämä helpottaa jatkossa rahavarauksien järjestämistä käyttöliittymässä
UPDATE rahavaraus
SET jarjestys = subquery.uusi_jarjestys
    FROM (
         SELECT id, ROW_NUMBER() OVER (ORDER BY id) as uusi_jarjestys
         FROM rahavaraus
     ) AS subquery

WHERE rahavaraus.id = subquery.id;
CREATE SEQUENCE rahavaraus_jarjestys_seq;

SELECT setval('rahavaraus_jarjestys_seq', COALESCE((SELECT MAX(jarjestys) FROM rahavaraus), 0));

ALTER TABLE rahavaraus
    ALTER COLUMN jarjestys SET DEFAULT nextval('rahavaraus_jarjestys_seq');
