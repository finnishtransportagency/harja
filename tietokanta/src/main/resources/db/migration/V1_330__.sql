<<<<<<< HEAD
ALTER TABLE liite ADD COLUMN kuvaus VARCHAR(254);
=======
ALTER TABLE vakiohavainto ADD COLUMN jatkuva BOOLEAN;

INSERT INTO vakiohavainto (nimi, jatkuva) VALUES
('Liukasta', true),
('Tasauspuute', true),
('Lumista', true),
('Liikennemerkki luminen', false),
('Pysäkillä epätasainen polanne', false),
('Aurausvalli', false),
('Sulamisvesihaittoja', false),
('Polanteessa jyrkät urat', false),
('Hiekoittamatta', false),
('Pysäkki auraamatta', false),
('Pysäkki hiekoittamatta', false),
('PL epätasainen polanne', false),
('PL-alue auraamatta', false),
('PL-alue hiekoittamatta', false),
('Sohjoa', false),
('Irtolunta', false),
('Lumikielekkeitä', false);
>>>>>>> develop
