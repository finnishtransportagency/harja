-- Määrittele erilaisia api-oikeuksia enumin kautta
CREATE TYPE apioikeus AS ENUM ('analytiikka','tielupa');
-- Lisää uusi kolumni määrittelemään tarkemmin käyttäjän oikeuksia
ALTER TABLE kayttaja
    ADD COLUMN api_oikeus apioikeus DEFAULT NULL;;

-- Siirretään olemassa olevat analytiikka-oikeudet tälle uudelle formaatille
DO $$
    DECLARE
        rivi record;
    BEGIN
        FOR rivi in (SELECT id FROM kayttaja WHERE "analytiikka-oikeus" IS TRUE)
            loop
                update kayttaja set api_oikeus = 'analytiikka' where id = rivi.id;
            end loop;
    end
$$ language plpgsql;
