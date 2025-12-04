-- Taulu elinvoimakeskuksille
-- Nimestä myös lyhyt versio, joka on käytössä ainakin tieluparajapinnassa
CREATE TABLE elinvoimakeskus
(
    id        SERIAL PRIMARY KEY,
    nimi      VARCHAR(128), -- esim. Sisä-Suomen elinvoimakeskus
    lyhytnimi VARCHAR(128), -- esim. Sisä-Suomi
    lyhenne   VARCHAR(16),  -- esim. SIS
    numero    INTEGER,      -- esim. 4
    alue      GEOMETRY
);

-- Lisää elivoimakeskustiedoille oma sarakkeet tielupatauluun.
-- Ely ei voi enää olla pakollinen tielupataulussa, koska tieto jää vähitellen pois kokonaan.
ALTER TABLE tielupa
ADD COLUMN elinvoimakeskus_id INTEGER REFERENCES elinvoimakeskus(id),
ADD COLUMN "mainoslupa-tiedoksi-elinvoimakeskukselle" BOOLEAN,
ALTER COLUMN ely DROP NOT NULL;

-- Lisätään samalla urakka-tauluun elinvoimakeskussarake, että urakka voidaan kytkeä niihinkin.
-- Urakka kytkeytyy elyyn hallintoyksikko-sarakkeesta.
ALTER TABLE urakka
    ADD COLUMN elinvoimakeskus_id INTEGER REFERENCES elinvoimakeskus(id);

