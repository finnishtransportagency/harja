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
