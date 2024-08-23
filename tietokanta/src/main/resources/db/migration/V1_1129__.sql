-- Nimetään taas vähän uusiksi rahavarauksia
UPDATE rahavaraus SET nimi = 'Levähdys- ja P-alueet' WHERE nimi like '%Levähdys- ja P-alueet%';
UPDATE rahavaraus SET nimi = 'Pysäkkikatosten korjaaminen' WHERE nimi like '%Rahavaraus E - Pysäkkikatokset%';
UPDATE rahavaraus SET nimi = 'Meluesteet' WHERE nimi like '%Meluesteet%';
UPDATE rahavaraus SET nimi = 'Juurakkopuhdistamo ym.' WHERE nimi like '%Juurakkopuhdistamo%';
UPDATE rahavaraus SET nimi = 'Aidat' WHERE nimi like '%Aidat%';
UPDATE rahavaraus SET nimi = 'Sillat ja laiturit' WHERE nimi like '%Rahavaraus I - Sillat ja laiturit%';
UPDATE rahavaraus SET nimi = 'Tunnelit' WHERE nimi like '%Rahavaraus J - Tunnelien pienet korjaukset%';
UPDATE rahavaraus SET nimi = 'Tilaajan rahavaraus kannustinjärjestelmään' WHERE nimi like '%Rahavaraus K%';

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
    SELECT into rv_vahingot_id id FROM rahavaraus WHERE nimi = 'Vahinkojen korvaukset';

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
                       'Vahinkojen korvaukset',
                       'Kannustinjärjestelmä');

    RETURN NEW;
END
$$ LANGUAGE plpgsql;
