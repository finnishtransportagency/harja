-- Varmistetaan, että rahavaraus_urakka tauluun ei tule duplikaatteja
ALTER TABLE rahavaraus_urakka
    ADD CONSTRAINT rahavaraus_urakka_pk
        UNIQUE (urakka_id, rahavaraus_id);

-- Varmista, että rahavaraus_tehtava tauluun ei tule duplikaatteja
create unique index rahavaraus_tehtava_tehtava_id_rahavaraus_id_uindex
    on rahavaraus_tehtava (tehtava_id, rahavaraus_id);

-- Poistetaan turhaksi jääneitä kolumneita kulu ja kulu_kohdistus tauluista
ALTER TABLE kulu
    DROP COLUMN IF EXISTS tyyppi;
-- Tyypin voi poistaa, koska kaikki on tyyppiä 'laskutettava'

-- Poistetaan turhaksi jäänyt laskutustyyppi
DROP TYPE IF EXISTS laskutyyppi;

-- Kululla voi olla monta kohdistusta ja niiden tyyppi on helpointa hallita kohdistuksessa itsessään
CREATE TYPE kohdistustyyppi AS ENUM ('rahavaraus', 'hankintakulu','muukulu', 'lisatyo', 'paatos');

-- Asetetaan defaultiksi useimmin käytössäoleva hankintakulu.
-- Lopulliset tyypit tulee, kun rahavarausten korjaava systeemi ajetaan kantaan
ALTER TABLE kulu_kohdistus
    ADD COLUMN IF NOT EXISTS tyyppi           kohdistustyyppi DEFAULT 'hankintakulu' NOT NULL,
    ADD COLUMN IF NOT EXISTS tavoitehintainen BOOLEAN         DEFAULT TRUE           NOT NULL,
    DROP COLUMN IF EXISTS suoritus_alku,
    DROP COLUMN IF EXISTS suoritus_loppu;
-- Suoritusajat voi poistaa, koska ne ovat aina samat kuin kulu.erapaiva

-- Päivitetään kulu_kohdistus taulun tyyppi rahavaraukseksi, jos rahavaraus_id on asetettu
UPDATE kulu_kohdistus
   SET tyyppi = 'rahavaraus'
 WHERE rahavaraus_id IS NOT NULL;

-- Päivitetään kulu_kohdistus taulun tyyppi lisatyoksi, jos maksueratyyppi on lisatyo
-- Kaikki lisätyöt, mitä tietokannassa on alunperin on myös ei tavoitehintaisia
UPDATE kulu_kohdistus
   SET tyyppi           = 'lisatyo',
       tavoitehintainen = FALSE
 WHERE kulu_kohdistus.maksueratyyppi = 'lisatyo';

-- Jotta tulevat rahavarausten automaattiset tausta-ajot korjaisivat kulu_kohdistus ja kustannusarvioitu_työ taulujen
-- rivit oikein. Meidän on lisättävä vielä yksi rahavaraus
INSERT INTO rahavaraus (nimi, luoja, luotu)
VALUES ('Muut tavoitehintaan vaikuttavat rahavaraukset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
        NOW());

-- Lisätään muutama pakollinen tehtävä rahavarukselle
INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
SELECT rv.id,
       t.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
       NOW()
  FROM rahavaraus rv,
       tehtava t
 WHERE t.tehtavaryhma IS NOT NULL
   AND t.nimi IN ('Muut tavoitehintaan vaikuttavat rahavaraukset',
                  'Pohjavesisuojaukset',
                  'Pysäkkikatoksen uusiminen',
                  'Pysäkkikatoksen poistaminen',
                  'Pysäkkikatoksen korjaaminen',
                  'Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään',
                  'Digitalisaation edistäminen ja innovaatioiden kehittäminen')
   AND rv.nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset';

--===  Lisätään rahavaraukselle Levähdys ja P-alueet oikea tehtävä ja poistetaan väärät ===--
-- Poistetaan ensin kaikki mahdollinen
DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus D - Levähdys- ja P-alueet');
-- Lisätään oikea tehtävä
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu) VALUES
    ((SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus D - Levähdys- ja P-alueet'),
     (SELECT id FROM tehtava WHERE nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen'),
     (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

--=== Sama jumppa Rahavaraus E - Pysäkkikatoksille - Poistetaan väärä tehtävä ja lisään oikea ===--
-- Poistetaan ensin kaikki mahdollinen
DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus E - Pysäkkikatokset');
-- Ja lisätään oikea
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu) VALUES
    ((SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus E - Pysäkkikatokset'),
     (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen'),
     (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

-- Päivitetään samalla noiden tehtävien tehtäväryhmä kuntoon
UPDATE tehtava SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)')
 WHERE nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen' OR
     nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen';

--== Lisätään puuttuvia tehtäviä rahavaraukselle ==--
-- Lisätään rahavaraukselle tehtäväryhmälle 'ELY-rahoitteiset, ylläpito (E)' kuuluvia tehtäviä
DO
$$
    DECLARE
        rahavaraus_id      INT;
        tehtavaryhma_id      INT;
        tehtava RECORD;
    BEGIN

        rahavaraus_id := (SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus E - Pysäkkikatokset');
        tehtavaryhma_id := (SELECT id FROM harja.public.tehtavaryhma WHERE nimi = 'ELY-rahoitteiset, ylläpito (E)');

        FOR tehtava IN SELECT id, nimi FROM tehtava WHERE tehtavaryhma = tehtavaryhma_id

            LOOP
                INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
                VALUES (rahavaraus_id, tehtava.id, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());
            END LOOP;
    END
$$;


-- Lisätään muutama pakollinen tehtävä rahavarukselle
INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
SELECT rv.id,
       t.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
       NOW()
  FROM rahavaraus rv,
       tehtava t
 WHERE t.nimi IN ('Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne')
   AND rv.nimi = 'Rahavaraus G - Juurakkopuhdistamo ym.';



DO
$$
    DECLARE
        tavoitepalkkioid      INT;
        tavoitehinnanylitysid INT;
        kattohinnanylitysid   INT;
    BEGIN

        tavoitepalkkioid := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio');
        tavoitehinnanylitysid := (SELECT id
                                    FROM tehtavaryhma
                                   WHERE
                                       nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä');
        kattohinnanylitysid := (SELECT id
                                  FROM tehtavaryhma
                                 WHERE nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä');

        -- Kaikki vuoden päättämisen kulut on ei tavoitehintaisia
        UPDATE kulu_kohdistus
           SET tyyppi           = 'paatos',
               tavoitehintainen = FALSE
         WHERE tehtavaryhma IN (tavoitepalkkioid, tavoitehinnanylitysid, kattohinnanylitysid);

    END
$$;


-- Lisätään uusi suunnittelu_osio kustannusten suunnitteluun
ALTER TYPE SUUNNITTELU_OSIO ADD VALUE 'tavoitehintaiset-rahavaraukset';

-- Lisää rahavaraus_id sarakkeet, on olemassa jo parissa taulussa, mutta ei haittaa
ALTER TABLE kulu_kohdistus ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
ALTER TABLE kustannusarvioitu_tyo ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
ALTER TABLE toteutuneet_kustannukset ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);

-- Nimetään taas vähän uusiksi rahavarauksia
UPDATE rahavaraus SET nimi = 'Levähdys- ja P-alueet' WHERE nimi like '%Levähdys- ja P-alueet%';
UPDATE rahavaraus SET nimi = 'Pysäkkikatosten korjaaminen' WHERE nimi like '%Rahavaraus E - Pysäkkikatokset%';
UPDATE rahavaraus SET nimi = 'Meluesteet' WHERE nimi like '%Meluesteet%';
UPDATE rahavaraus SET nimi = 'Juurakkopuhdistamo ym.' WHERE nimi like '%Juurakkopuhdistamo%';
UPDATE rahavaraus SET nimi = 'Aidat' WHERE nimi like '%Aidat%';
UPDATE rahavaraus SET nimi = 'Sillat ja laiturit' WHERE nimi like '%Rahavaraus I - Sillat ja laiturit%';
UPDATE rahavaraus SET nimi = 'Tunnelit' WHERE nimi like '%Rahavaraus J - Tunnelien pienet korjaukset%';
UPDATE rahavaraus SET nimi = 'Vahinkojen korjaukset' WHERE nimi = 'Vahinkojen korvaukset';
UPDATE rahavaraus SET nimi = 'Tilaajan rahavaraus kannustinjärjestelmään' WHERE nimi = 'Kannustinjärjestelmä';

-- Poistetaan turhat rahavaraukset - Ja jos niitä on jollakulla käytössä, niin päivitetään ID:t
DO $$
    DECLARE
        rv_akilliset_id_poistettava INT;
        rv_akilliset_id INT;
        rv_vahingot_id_poistettava INT;
        rv_vahingot_id INT;
        rv_kannustin_id_poistettava INT;
        rv_kannustin_id INT;

    BEGIN
        SELECT id into rv_akilliset_id_poistettava FROM rahavaraus WHERE nimi = 'Rahavaraus B - Äkilliset hoitotyöt';
        SELECT id into rv_akilliset_id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt';
        SELECT id into rv_vahingot_id_poistettava FROM rahavaraus WHERE nimi = 'Rahavaraus C - Vahinkojen korjaukset';
        SELECT id into rv_vahingot_id FROM rahavaraus WHERE nimi = 'Vahinkojen korjaukset';
        SELECT id into rv_kannustin_id_poistettava FROM rahavaraus WHERE nimi = 'Rahavaraus K - Kannustinjärjestelmä';
        SELECT id into rv_kannustin_id FROM rahavaraus WHERE nimi = 'Tilaajan rahavaraus kannustinjärjestelmään';

        -- Äkilliset
        UPDATE rahavaraus_urakka set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        UPDATE kulu_kohdistus set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        UPDATE kustannusarvioitu_tyo set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        UPDATE toteutuneet_kustannukset set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;

        DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        DELETE FROM rahavaraus WHERE id = rv_akilliset_id_poistettava;

        -- Vahingot
        UPDATE rahavaraus_urakka set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        UPDATE kulu_kohdistus set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        UPDATE kustannusarvioitu_tyo set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        UPDATE toteutuneet_kustannukset set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;

        DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        DELETE FROM rahavaraus WHERE id = rv_vahingot_id_poistettava;

        -- Kannustin
        UPDATE rahavaraus_urakka set rahavaraus_id = rv_kannustin_id WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        UPDATE kulu_kohdistus set rahavaraus_id = rv_kannustin_id WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        UPDATE kustannusarvioitu_tyo set rahavaraus_id = rv_kannustin_id WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        UPDATE toteutuneet_kustannukset set rahavaraus_id = rv_kannustin_id WHERE rahavaraus_id = rv_kannustin_id_poistettava;

        DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        DELETE FROM rahavaraus WHERE id = rv_kannustin_id_poistettava;

    END
$$ LANGUAGE plpgsql;

-- Lisätään puuttuva varalaskupaikka rahavaraus
INSERT INTO rahavaraus (nimi, luoja, luotu) VALUES ('Varalaskupaikat', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP);

-- Lisätään tehtävä varalaskupaikalle
-- Ensin se uusi tehtävä
INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, luotu, luoja)
VALUES ('Varalaskupaikkojen hoito', (select id from toimenpide where koodi = '20191'), 'kpl', 'kpl',
        (select id from tehtavaryhma where yksiloiva_tunniste = '4e3cf237-fdf5-4f58-b2ec-319787127b3e'),  -- Tällä hetkellä: Muut, MHU ylläpito (F)
        null, null, FALSE, NULL,
        null, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Lisää varalauskaupaikka tehtävä varalaskupaikka rahavaraukselle
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES ((select id from rahavaraus where nimi = 'Varalaskupaikat'),
        (select id from tehtava where nimi = 'Varalaskupaikkojen hoito'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP);


CREATE OR REPLACE FUNCTION lisaa_urakan_oletus_rahavaraukset() RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja)
    SELECT NEW.id,
           rv.id,
           (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
      FROM rahavaraus rv
     WHERE rv.nimi IN ('Äkilliset hoitotyöt',
                       'Vahinkojen korjaukset',
                       'Kannustinjärjestelmä');

    RETURN NEW;
END
$$ LANGUAGE plpgsql;
