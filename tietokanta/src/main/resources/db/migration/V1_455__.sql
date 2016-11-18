<<<<<<< HEAD
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
('Reunapalkkivaurioita likainen', false, 'sillassa-reunapalkkivaurioita');

-- Vakiohavainnon avain uniikki
ALTER TABLE vakiohavainto ADD CONSTRAINT uniikki_vakiohavainto UNIQUE (avain);

-- Päivitä vakiohavaintojen jatkuvuustieto
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Vesakko raivaamatta';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Niittämättä';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Reunapalletta';
UPDATE vakiohavainto SET jatkuva = TRUE WHERE nimi = 'Reunatäyttö puutteellinen';
=======
-- Tiemerkintöjen valtakunnalliset välitavoitteet (välitavoitepohjat)

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Massavaatimusteiden keskiviistaton merkinnät kunnostettu', 'tiemerkinta', 31, 7, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Massavaatimusteiden keskiviistaton reunaviivat kunnostettu', 'tiemerkinta', 31, 8, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Maalivaatimusteiden pituussuuntaiset merkinnät kunnostettu', 'tiemerkinta', 30, 9, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Suojatiet ja pyörätiet kunnostettu', 'tiemerkinta', 31, 7, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Pienmerkinnät kunnostettu', 'tiemerkinta', 30, 9, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Kevään kuntoarvo, massavaatimustiet, pituussuuntaiset merkinnät raportoitu tilaajalle', 'tiemerkinta', 31, 5, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Syksyn kuntoarvo, maalivaatimustiet, pituussuuntaiset merkinnät raportoitu tilaajalle', 'tiemerkinta', 15, 10, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Suojatiet raportoitu tilaajalle', 'tiemerkinta', 31, 8, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Pienmerkinnät raportoitu tilaajalle', 'tiemerkinta', 15, 10, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Massavaatimustiet, pituussuuntaiset merkinnät tulee olla raportoitu tilaajalle', 'tiemerkinta', 30, 9, 'toistuva'::valitavoite_tyyppi, false);

INSERT INTO valitavoite (nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES ('Massavaatimustiet, pituussuuntaiset merkinnät tulee olla raportoitu tilaajalle', 'tiemerkinta', 15, 10, 'toistuva'::valitavoite_tyyppi, false);
>>>>>>> develop
