-- Päivitetään pot-lomakkeen ja materiaalikirjaston koodistot vastaamaan uutta YHA-skeemaa
INSERT INTO pot2_mk_massatyyppi (nimi, lyhenne, koodi, jarjestys)
VALUES ('Sirotepintaus', 'SIP', 24, 19);

ALTER TABLE pot2_mk_massatyyppi ADD COLUMN poistettu BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pot2_mk_massatyyppi ADD COLUMN muokkaaja INTEGER REFERENCES kayttaja (id) DEFAULT NULL;
ALTER TABLE pot2_mk_massatyyppi ADD COLUMN muokattu TIMESTAMP DEFAULT NULL;
-- YHA-skeemasta poistettiin 99 ei tietoa. Tuotannossa on kuitenkin kirjauksia, joten ei voida käyttää DELETEä
UPDATE pot2_mk_massatyyppi
SET poistettu = TRUE,
    muokattu = NOW(),
    muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE koodi = 99 AND nimi = 'Ei tietoa';

INSERT INTO pot2_mk_alusta_toimenpide(nimi, lyhenne, koodi)
VALUES ('PAB-B', 'PAB-B', 50),
       ('PAB-V', 'PAB-V', 51),
       ('UREM-TAS', 'UREM-TAS', 52),
       ('Muu stabilointi', 'Muu stab.', 53),
       ('Muu RP', 'Muu RP', 54);

-- Ei rivejä tuotannossa, joten voidaan vaihtaa koodin tilalle eri aine
UPDATE pot2_mk_sideainetyyppi
   SET nimi = 'Muu erikoisbitumi',
       lyhenne = 'Muu erikoisbitumi'
WHERE koodi = 26 AND nimi = 'KF, Kalkkifilleri';
-- Ei rivejä tuotannossa, joten voidaan vaihtaa koodin tilalle eri aine
UPDATE pot2_mk_sideainetyyppi
   SET nimi = 'Bitumia korvaava uusiomateriaali',
       lyhenne = 'Bitumia korvaava uusiomateriaali'
WHERE koodi = 27 AND nimi = 'Muu, erikoisbitumi';
