-- Tehdään vain sellaisia rahavarauksiin liittyviä muutoksia, jotka eivät aiheuta muutostarpeita muualla koodissa
-- Aluksi kaikki rahavaraus -tauluihin liittyvät muutokset
-- Lisätään Rahavarauksille järjestys
ALTER TABLE rahavaraus
    ADD COLUMN jarjestys INTEGER NOT NULL DEFAULT 0;

-- Varmistetaan, että rahavaraus_urakka tauluun ei tule duplikaatteja
ALTER TABLE rahavaraus_urakka
    ADD CONSTRAINT rahavaraus_urakka_pk
        UNIQUE (urakka_id, rahavaraus_id);

-- Varmista, että rahavaraus_tehtava tauluun ei tule duplikaatteja
CREATE UNIQUE INDEX rahavaraus_tehtava_tehtava_id_rahavaraus_id_uindex
    ON rahavaraus_tehtava (tehtava_id, rahavaraus_id);

-- Lisää rahavaraus_id sarakkeet, on olemassa jo parissa taulussa, mutta ei haittaa
ALTER TABLE kulu_kohdistus
    ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
ALTER TABLE kustannusarvioitu_tyo
    ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);
ALTER TABLE toteutuneet_kustannukset
    ADD COLUMN IF NOT EXISTS rahavaraus_id INT REFERENCES rahavaraus (id);

-- Nimetään uusiksi rahavarauksia
UPDATE rahavaraus
   SET nimi = 'Pysäkkikatosten korjaaminen'
 WHERE nimi LIKE '%Rahavaraus E - Pysäkkikatokset%';
UPDATE rahavaraus
   SET nimi = 'Tunneleiden hoito'
 WHERE nimi LIKE '%Rahavaraus J - Tunnelien pienet korjaukset%';
UPDATE rahavaraus
   SET nimi = 'Vahinkojen korjaukset'
 WHERE nimi = 'Vahinkojen korvaukset';
UPDATE rahavaraus
   SET nimi = 'Tilaajan rahavaraus kannustinjärjestelmään'
 WHERE nimi = 'Kannustinjärjestelmä';

-- Poistetaan turhat rahavaraukset - Ja jos niitä on jollakulla käytössä, niin päivitetään ID:t
DO
$$
    DECLARE
        rv_akilliset_id_poistettava INT;
        rv_akilliset_id             INT;
        rv_vahingot_id_poistettava  INT;
        rv_vahingot_id              INT;
        rv_kannustin_id_poistettava INT;
        rv_kannustin_id             INT;

    BEGIN
        SELECT id INTO rv_akilliset_id_poistettava FROM rahavaraus WHERE nimi = 'Rahavaraus B - Äkilliset hoitotyöt';
        SELECT id INTO rv_akilliset_id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt';
        SELECT id INTO rv_vahingot_id_poistettava FROM rahavaraus WHERE nimi = 'Rahavaraus C - Vahinkojen korjaukset';
        SELECT id INTO rv_vahingot_id FROM rahavaraus WHERE nimi = 'Vahinkojen korjaukset';
        SELECT id INTO rv_kannustin_id_poistettava FROM rahavaraus WHERE nimi = 'Rahavaraus K - Kannustinjärjestelmä';
        SELECT id INTO rv_kannustin_id FROM rahavaraus WHERE nimi = 'Tilaajan rahavaraus kannustinjärjestelmään';

        -- Äkilliset
        UPDATE rahavaraus_urakka SET rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        UPDATE kulu_kohdistus SET rahavaraus_id = rv_akilliset_id WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        UPDATE kustannusarvioitu_tyo
           SET rahavaraus_id = rv_akilliset_id
         WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        UPDATE toteutuneet_kustannukset
           SET rahavaraus_id = rv_akilliset_id
         WHERE rahavaraus_id = rv_akilliset_id_poistettava;

        DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_akilliset_id_poistettava;
        DELETE FROM rahavaraus WHERE id = rv_akilliset_id_poistettava;

        -- Vahingot
        UPDATE rahavaraus_urakka SET rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        UPDATE kulu_kohdistus SET rahavaraus_id = rv_vahingot_id WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        UPDATE kustannusarvioitu_tyo
           SET rahavaraus_id = rv_vahingot_id
         WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        UPDATE toteutuneet_kustannukset
           SET rahavaraus_id = rv_vahingot_id
         WHERE rahavaraus_id = rv_vahingot_id_poistettava;

        DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_vahingot_id_poistettava;
        DELETE FROM rahavaraus WHERE id = rv_vahingot_id_poistettava;

        -- Kannustin
        UPDATE rahavaraus_urakka SET rahavaraus_id = rv_kannustin_id WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        UPDATE kulu_kohdistus SET rahavaraus_id = rv_kannustin_id WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        UPDATE kustannusarvioitu_tyo
           SET rahavaraus_id = rv_kannustin_id
         WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        UPDATE toteutuneet_kustannukset
           SET rahavaraus_id = rv_kannustin_id
         WHERE rahavaraus_id = rv_kannustin_id_poistettava;

        DELETE FROM rahavaraus_tehtava WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        DELETE FROM rahavaraus_urakka WHERE rahavaraus_id = rv_kannustin_id_poistettava;
        DELETE FROM rahavaraus WHERE id = rv_kannustin_id_poistettava;

    END
$$ LANGUAGE plpgsql;

-- Lisätään puuttuva varalaskupaikka rahavaraus
INSERT INTO rahavaraus (nimi, luoja, luotu)
VALUES ('Varalaskupaikat', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP);

-- Jotta tulevat rahavarausten automaattiset tausta-ajot korjaisivat kulu_kohdistus ja kustannusarvioitu_työ taulujen
-- rivit oikein. Meidän on lisättävä vielä yksi rahavaraus
INSERT INTO rahavaraus (nimi, luoja, luotu)
VALUES ('Muut tavoitehintaan vaikuttavat rahavaraukset', (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
        NOW());

-- Aseta järjestysnumero jokaiselle rahavaraukselle
UPDATE rahavaraus
   SET jarjestys = 1
 WHERE nimi = 'Rahavaraus A';
UPDATE rahavaraus
   SET jarjestys = 2
 WHERE nimi = 'Äkilliset hoitotyöt';
UPDATE rahavaraus
   SET jarjestys = 3
 WHERE nimi = 'Vahinkojen korjaukset';
UPDATE rahavaraus
   SET jarjestys = 4
 WHERE nimi = 'Rahavaraus D - Levähdys- ja P-alueet';
UPDATE rahavaraus
   SET jarjestys = 5
 WHERE nimi = 'Pysäkkikatosten korjaaminen';
UPDATE rahavaraus
   SET jarjestys = 6
 WHERE nimi = 'Rahavaraus F - Meluesteet';
UPDATE rahavaraus
   SET jarjestys = 7
 WHERE nimi = 'Rahavaraus G - Juurakkopuhdistamo';
UPDATE rahavaraus
   SET jarjestys = 8
 WHERE nimi = 'Rahavaraus H - Aidat';
UPDATE rahavaraus
   SET jarjestys = 9
 WHERE nimi = 'Rahavaraus I - Sillat ja laiturit';
UPDATE rahavaraus
   SET jarjestys = 10
 WHERE nimi = 'Tilaajan rahavaraus kannustinjärjestelmään';
UPDATE rahavaraus
   SET jarjestys = 11
 WHERE nimi = 'Tunneleiden hoito';
UPDATE rahavaraus
   SET jarjestys = 12
 WHERE nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset';
UPDATE rahavaraus
   SET jarjestys = 13
 WHERE nimi = 'Varalaskupaikat';

-- Lisätään tehtävä varalaskupaikalle
-- Ensin se uusi tehtävä
INSERT INTO tehtava (nimi, emo, yksikko, suunnitteluyksikko, tehtavaryhma, jarjestys, hinnoittelu, api_seuranta,
                     api_tunnus, suoritettavatehtava, luotu, luoja)
VALUES ('Varalaskupaikkojen hoito', (SELECT id FROM toimenpide WHERE koodi = '20191'), 'kpl', 'kpl',
        (SELECT id
           FROM tehtavaryhma
          WHERE yksiloiva_tunniste = '4e3cf237-fdf5-4f58-b2ec-319787127b3e'), -- Tällä hetkellä: Muut, MHU ylläpito (F)
        NULL, NULL, FALSE, NULL,
        NULL, CURRENT_TIMESTAMP, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));

-- Lisää varalauskaupaikka tehtävä varalaskupaikka rahavaraukselle
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES ((SELECT id FROM rahavaraus WHERE nimi = 'Varalaskupaikat'),
        (SELECT id FROM tehtava WHERE nimi = 'Varalaskupaikkojen hoito'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP),
       ((SELECT id FROM rahavaraus WHERE nimi = 'Varalaskupaikat'),
        (SELECT id
           FROM tehtava
          WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'), -- Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP),
       ((SELECT id FROM rahavaraus WHERE nimi = 'Varalaskupaikat'),
        (SELECT id FROM tehtava WHERE nimi = 'Digitalisaation edistäminen ja innovaatioiden kehittäminen'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), CURRENT_TIMESTAMP);

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
DELETE
  FROM rahavaraus_tehtava
 WHERE rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus D - Levähdys- ja P-alueet');
-- Lisätään oikea tehtävä
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES ((SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus D - Levähdys- ja P-alueet'),
        (SELECT id FROM tehtava WHERE nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

--=== Sama jumppa Rahavaraus E - Pysäkkikatoksille - Poistetaan väärä tehtävä ja lisään oikea ===--
-- Poistetaan ensin kaikki mahdollinen
DELETE
  FROM rahavaraus_tehtava
 WHERE rahavaraus_id = (SELECT id FROM rahavaraus WHERE nimi = 'Pysäkkikatosten korjaaminen');
-- Ja lisätään oikea
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES ((SELECT id FROM rahavaraus WHERE nimi = 'Pysäkkikatosten korjaaminen'),
        (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

-- Lisätään rahavaraus A:lle puuttuva tehtävä
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES ((SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus A'),
        (SELECT id FROM tehtava WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());

-- Päivitetään  tehtävien tehtäväryhmät kuntoon
UPDATE tehtava
   SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)')
 WHERE nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen'
    OR nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen'
    OR nimi = 'Meluesteiden pienten vaurioiden korjaaminen'
    OR nimi = 'Aitojen vaurioiden korjaukset';

UPDATE tehtava
   SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Muut, MHU ylläpito (F)')
 WHERE nimi = 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt';

-- Korjataan myös yhden tehtäväryhmän nimi
UPDATE tehtavaryhma
   SET nimi = 'Tilaajan rahavaraus (T3)'
 WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään (T3)';
UPDATE tehtavaryhma
   SET nimi = 'Päällysteiden paikkaus, muut työt (Y)'
 WHERE nimi = 'Päällysteiden paikkaus (Y)';

--== Lisätään puuttuvia tehtäviä rahavaraukselle ==--
-- Lisätään rahavaraukselle tehtäväryhmälle 'ELY-rahoitteiset, ylläpito (E)' kuuluvia tehtäviä
DO
$$
    DECLARE
        rahavaraus_id   INT;
        tehtavaryhma_id INT;
        tehtava         RECORD;
    BEGIN

        rahavaraus_id := (SELECT id FROM rahavaraus WHERE nimi = 'Pysäkkikatosten korjaaminen');
        tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'ELY-rahoitteiset, ylläpito (E)');

        FOR tehtava IN SELECT id, nimi FROM tehtava WHERE tehtavaryhma = tehtavaryhma_id

            LOOP
                INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
                VALUES (rahavaraus_id, tehtava.id, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), NOW());
            END LOOP;
    END
$$;

--== Ajetaan uutta urakkaa lisättäessä ==--
-- Lisätään oletusrahavaraukset kaikille uusille urakoille
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
                       'Tilaajan rahavaraus kannustinjärjestelmään');

    RETURN NEW;
END
$$ LANGUAGE plpgsql;
