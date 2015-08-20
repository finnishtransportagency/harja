-- Liikennevirasto on myös organisaatio, joka pitää tunnistaa
-- jvh roolin henkilöt ovat livin

-- enum arvon lisääminen ei postgresilta onnistu: ks. workaround
-- http://stackoverflow.com/questions/1771543/postgresql-updating-an-enum-type

ALTER TYPE organisaatiotyyppi rename to _orgty;

CREATE TYPE organisaatiotyyppi AS ENUM ('liikennevirasto','hallintayksikko','urakoitsija');

ALTER TABLE organisaatio RENAME COLUMN tyyppi to _tyyppi;

ALTER TABLE organisaatio ADD tyyppi organisaatiotyyppi;

UPDATE organisaatio SET tyyppi = _tyyppi::text::organisaatiotyyppi;

ALTER TABLE organisaatio DROP COLUMN _tyyppi;
DROP TYPE _orgty;


      
