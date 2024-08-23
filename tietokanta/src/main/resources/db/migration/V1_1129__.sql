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

-- Lisätään puuttuva tehtäväryhmä "Pysäkkikatosten korjaaminen (E)"
INSERT INTO tehtavaryhma (nimi, tehtavaryhmaotsikko_id, luoja, luotu)
VALUES ('Pysäkkikatosten korjaaminen (E)',
        (SELECT id FROM tehtavaryhmaotsikko WHERE otsikko LIKE '%MUUTA%'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

-- Lokaalisti puuttuu tehtävä 'Pysäkkikatoksen korjaaminen' , joten varmistetaan, ettei sitä ole ja jos ei , niin sitten lisätään
INSERT INTO tehtava (nimi, tehtavaryhma, yksikko, luoja, luotu)
VALUES ('Pysäkkikatoksen korjaaminen',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Pysäkkikatosten korjaaminen (E)'),
        'euroa',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW())
    ON CONFLICT DO NOTHING;


-- Lisää tehtävälle "Pysäkkikatoksen korjaaminen" tehtäväryhmä "Pysäkkikatosten korjaaminen (E)"
UPDATE tehtava
   SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Pysäkkikatosten korjaaminen (E)')
 WHERE nimi = 'Pysäkkikatoksen korjaaminen';

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

INSERT INTO rahavaraus (nimi, luoja, luotu)
VALUES ('Rahavaraus G - Juurakkopuhdistamo ym.', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

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

-- Päivitetään vuoden päätöstyyppiset kulu_kohdistukset vuoden päätös tyypille
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
UPDATE rahavaraus SET nimi = 'Tilaajan rahavaraus kannustinjärjestelmään' WHERE nimi like '%Rahavaraus K%';
UPDATE rahavaraus SET nimi = 'Vahinkojen korjaukset' WHERE nimi = 'Vahinkojen korvaukset';

-- Poistetaan turhat rahavaraukset - Ja jos niitä on jollakulla käytössä, niin päivitetään ID:t
DO $$
DECLARE
    rv_akilliset_id_poistettava INT;
    rv_akilliset_id INT;
    rv_vahingot_id_poistettava INT;
    rv_vahingot_id INT;

BEGIN
    SELECT into rv_akilliset_id_poistettava id FROM rahavaraus WHERE nimi = 'Rahavaraus B - Äkilliset hoitotyöt';
    SELECT into rv_akilliset_id id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt';
    SELECT into rv_vahingot_id_poistettava id FROM rahavaraus WHERE nimi = 'Rahavaraus C - Vahinkojen korjaukset';
    SELECT into rv_vahingot_id id FROM rahavaraus WHERE nimi = 'Vahinkojen korjaukset';

    -- Äkilliset
    UPDATE rahavaraus_urakka set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
    UPDATE kulu_kohdistus set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
    UPDATE kustannusarvioitu_tyo set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
    UPDATE toteutuneet_kustannukset set rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;

    UPDATE rahavaraus_urakka set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
    UPDATE kulu_kohdistus set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
    UPDATE kustannusarvioitu_tyo set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
    UPDATE toteutuneet_kustannukset set rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;


    DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_akilliset_id_poistettava;
    DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_akilliset_id_poistettava;
    DELETE FROM rahavaraus WHERE id = rv_akilliset_id_poistettava;

    DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_vahingot_id_poistettava;
    DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_vahingot_id_poistettava;
    DELETE FROM rahavaraus WHERE id = rv_vahingot_id_poistettava;

END
$$ LANGUAGE plpgsql;

-- Lisätään puuttuva varalaskupaikka rahavaraus
INSERT INTO rahavaraus (nimi, luoja, luotu) VALUES ('Varalaskupaikat', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP);

-- Lisätään tehtävä varalaskupaikalle
-- Ensin se uusi tehtävä
INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, luotu, luoja)
VALUES ('Varalaskupaikan hoito', (select id from toimenpide where koodi = '23110'), 'kpl', 'kpl',
        (select id from tehtavaryhma where yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6'),  -- Tällä hetkellä Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään (T3)
        null, null, FALSE, NULL,
        null, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Lisää varalauskaupaikka tehtävä varalaskupaikka rahavaraukselle
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES ((select id from rahavaraus where nimi = 'Varalaskupaikat'),
        (select id from tehtava where nimi = 'Varalaskupaikan hoito'),
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
