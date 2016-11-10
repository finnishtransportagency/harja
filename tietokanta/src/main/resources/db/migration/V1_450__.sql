-- Reittimerkinälle tasauspuute (oli aiemmin virheellisesti sama kuin soratien tasaisuus)
ALTER TABLE tarkastusreitti ADD COLUMN soratie_tasaisuus INTEGER;
ALTER TABLE tarkastusreitti RENAME COLUMN tasaisuus TO talvihoito_tasaisuus;

-- Lisää puuttuvat vakiohavainnot
INSERT INTO public.vakiohavainto (nimi, jatkuva, avain)
VALUES ('Liikennemerkki likainen', false, 'liikennemerkki-likainen'),
('Liikennemerkki likainen', false, 'liikennemerkki-likainen'),
('P- tai L-alueet hoitamatta', false, 'p-tai-l-alue-hoitamatta'),
('Päällysteessä vaurioita', false, 'sillan-paallysteessa-vaurioita'),
('Kaidevauroita', false, 'sillassa-kaidevaurioita'),
('Reunapalkkivaurioita likainen', false, 'sillassa-reunapalkkivaurioita');

-- Päivitä vakiohavaintojen jatkuvuustieto
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Vesakko raivaamatta';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Niittämättä';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Reunapalletta';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Reunatäyttö puutteellinen';






SELECT * FROM vakiohavainto WHERE nimi = 'Vesakko raivaamatta';