-- Määrittele erilaisia api-oikeuksia enumin kautta
CREATE TYPE api_oikeudet AS ENUM ('analytiikka','tielupa');
-- Lisää uusi kolumni määrittelemään tarkemmin käyttäjän oikeuksia
ALTER TABLE kayttaja
    ADD COLUMN api_oikeus api_oikeudet;

-- Siivotaan olemassa olevat analytiikka-oikeudet tälle uudelle formaatille
DO $$
    DECLARE
        rivi record;
    BEGIN
        FOR rivi in (SELECT id FROM kayttaja WHERE "analytiikka-oikeus" IS TRUE)
            loop
                update kayttaja set api_oikeus = 'analytiikka';
            end loop;
    end
$$ language plpgsql;

-- Poistetaan turhaksi jäänyt kolumni
ALTER TABLE kayttaja
    DROP COLUMN "analytiikka-oikeus";
