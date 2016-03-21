<<<<<<< HEAD
ALTER TABLE tarkastusreitti ADD COLUMN kuva bytea;
ALTER TABLE tarkastusreitti ADD COLUMN mimetype varchar(32);
=======
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tyontekijanammatti_muu VARCHAR(512);
UPDATE turvallisuuspoikkeama SET tyontekijanammatti_muu = turvallisuuspoikkeama.tyontekijanammatti;

CREATE TYPE tyontekijanammatti AS ENUM (
  'aluksen_paallikko',
  'asentaja',
  'asfalttityontekija',
  'harjoittelija',
  'hitsaaja',
  'kunnossapitotyontekija',
  'kansimies',
  'kiskoilla_liikkuvan_tyokoneen_kuljettaja',
  'konemies',
  'kuorma-autonkuljettaja',
  'liikenteenohjaaja',
  'mittamies',
  'panostaja',
  'peramies',
  'porari',
  'rakennustyontekija',
  'ratatyontekija',
  'ratatyosta_vastaava',
  'sukeltaja',
  'sahkotoiden_ammattihenkilo',
  'tilaajan_edustaja',
  'turvalaiteasentaja',
  'turvamies',
  'tyokoneen_kuljettaja',
  'tyonjohtaja',
  'valvoja',
  'veneenkuljettaja',
  'vaylanhoitaja',
  'muu_tyontekija',
  'tyomaan_ulkopuolinen'
);

ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyontekijanammatti;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tyontekijanammatti tyontekijanammatti;
UPDATE turvallisuuspoikkeama SET tyontekijanammatti = 'muu_tyontekija'::tyontekijanammatti WHERE tyontekijanammatti_muu IS NOT NULL;
>>>>>>> HAR-1909
