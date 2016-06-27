<<<<<<< HEAD
ALTER TABLE toimenpidekoodi ADD COLUMN api_seuranta BOOLEAN;

UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Auraus ja sohjonpoisto';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Liikennemerkkien puhdistus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Linjahiekoitus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Lumivallien madaltaminen';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Pinnan tasaus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Pistehiekoitus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Sulamisveden haittojen torjunta';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Suolaus';
UPDATE toimenpidekoodi SET api_seuranta = TRUE WHERE nimi = 'Suolaus';

-- todo: tee loppuun, kun saadaan valmis lista
=======
-- Laatupoikkeamalle ja tarkastukselle viittaus ylläpitokohteeseen.
-- Uudet tarkastustyypit
ALTER TABLE laatupoikkeama ADD COLUMN yllapitokohde INTEGER REFERENCES yllapitokohde(id);
ALTER TABLE tarkastus ADD COLUMN yllapitokohde INTEGER REFERENCES yllapitokohde(id);

ALTER TYPE tarkastustyyppi RENAME TO _tartyyppi;
CREATE TYPE tarkastustyyppi AS ENUM ('tiesto','talvihoito','soratie','laatu','pistokoe',
  -- Uutuudet:
  'katselmus', 'vastaanotto', 'takuu');

ALTER TABLE tarkastus RENAME COLUMN tyyppi TO _tyyppi;
ALTER TABLE tarkastus ADD tyyppi tarkastustyyppi;
UPDATE tarkastus SET tyyppi = _tyyppi::text::tarkastustyyppi;
ALTER TABLE tarkastus DROP COLUMN _tyyppi;

ALTER TABLE vakiohavainto RENAME COLUMN tarkastustyyppi TO _tarkastustyyppi;
ALTER TABLE vakiohavainto ADD tarkastustyyppi tarkastustyyppi;
UPDATE vakiohavainto SET tarkastustyyppi = _tarkastustyyppi::text::tarkastustyyppi;
ALTER TABLE vakiohavainto DROP COLUMN _tarkastustyyppi;

DROP TYPE _tartyyppi;
>>>>>>> develop
