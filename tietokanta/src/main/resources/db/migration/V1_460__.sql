-- Tarkastusajolla ei ole enää tyyppiä (säilytetään sarake vanhojen ajojen takia)
ALTER TABLE tarkastusajo ALTER COLUMN tyyppi DROP NOT NULL;

-- Reittimerkinälle tasauspuute (oli aiemmin virheellisesti sama kuin soratien tasaisuus)
ALTER TABLE tarkastusreitti ADD COLUMN soratie_tasaisuus INTEGER;
ALTER TABLE tarkastusreitti RENAME COLUMN tasaisuus TO talvihoito_tasaisuus;

-- Lisää puuttuvat vakiohavainnot
INSERT INTO vakiohavainto (nimi, jatkuva, avain)
VALUES ('Liikennemerkki likainen', false, 'liikennemerkki-likainen'),
('P- tai L-alueet hoitamatta', false, 'pl-alue-hoitamatta'),
('Päällysteessä vaurioita', false, 'sillan-paallysteessa-vaurioita'),
('Kaidevauroita', false, 'sillassa-kaidevaurioita'),
('Maakivi', false, 'maakivi'),
('Reunapaalu vaurioitunut', false, 'reunapaalut-vaurioitunut'),
('Liikennemerkki vaurioitunut', false, 'liikennemerkki-vaurioitunut'),
('Reunapalkkivaurioita likainen', false, 'sillassa-reunapalkkivaurioita');

-- Vakiohavainnon avain uniikki
ALTER TABLE vakiohavainto ADD CONSTRAINT uniikki_vakiohavainto UNIQUE (avain);

-- Päivitä vakiohavaintojen jatkuvuustieto
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Vesakko raivaamatta';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Niittämättä';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Reunapalletta';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Reunatäyttö puutteellinen';