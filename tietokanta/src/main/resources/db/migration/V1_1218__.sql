-- Lisää elivoimakeskustiedolle oma sarake tielupatauluun.
-- Jatkossa ely- ja elinvoimakeskus-sarakkeet voidaan ehkä yhdistää yhdeksi, organisaatio.id-tauluun
-- viittaavaksi hallintoyksikko-sarakkeeksi. Siirtymävaiheessa rajapinta vastaanottaa tiedon
-- molemmista organisaatioista. Ely ei voi enää olla pakollinen tielupataulussa, koska tieto jää vähitellen
-- pois kokonaan.
ALTER TABLE tielupa
ADD COLUMN elinvoimakeskus INTEGER REFERENCES organisaatio(id),
ADD COLUMN "mainoslupa-tiedoksi-elinvoimakeskukselle" BOOLEAN,
ALTER COLUMN ely DROP NOT NULL;

ALTER TABLE organisaatio
    ADD COLUMN elinvoimakeskusnumero INTEGER;
COMMENT ON COLUMN organisaatio.elinvoimakeskusnumero IS E'Elinvoimakeskusten lyhenteissä on päällekkäisyyttä elyjen kanssa. Erillisen elynumero- ja elinvoimanumerosarakkeen avulla voidaan tunnistaa hallintoyksiköt riittävästi.';

-- Lisätään samalla urakka-tauluun elinvoimakeskussarake, että urakka voidaan kytkeä niihinkin.
-- Urakka kytkeytyy elyyn hallintoyksikko-sarakkeesta.
ALTER TABLE urakka
    ADD COLUMN elinvoimakeskus INTEGER REFERENCES organisaatio(id);

