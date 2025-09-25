-- Muutoksia tehtäviin, tehtäväryhmiin ja rahavarauksiin

-- Rahavarausten muutokset
-- Käyttöliittymässä lisätty uudet rahavaraukset: Jääteiden hoito, Reikäpaikkaukset

-- Jääteiden hoito linkittyy A - Talvihoito-tehtäväryhmään ja Talvihoito-toimenpiteeseen
UPDATE tehtava SET yksiloiva_tunniste = '74692bc3-a780-4a9f-8124-4e48ae7472ef',
                   tehtavaryhma = (select id from tehtavaryhma where yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac'), -- A - Talvihoito
                   "mhu-tehtava?" = true,
                   muokattu = current_timestamp,
                   muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Jäätien hoito';

-- Pysäkkikatosten uusinen linkittyy E-tehtäväryhmään
UPDATE tehtava SET yksiloiva_tunniste = '17e1f66b-6dde-4caf-ab5b-48aa0ba924a8',
                   tehtavaryhma = (select id from tehtavaryhma where yksiloiva_tunniste = 'c8c65700-7178-4de0-b298-a715d6552840'), -- E - ELY-rahoitteiset, ylläpito
                   muokattu = current_timestamp,
                   muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Pysäkkikatoksen uusiminen' and "mhu-tehtava?" = true and emo = (select id from toimenpide where koodi = '20191');

-- Uusi tehtväryhmä T5 - KT-reikävaluasfalttipaikkaus
-- Tehtäväryhmälle kohdistetut kustannukset kuuluvat MHU Ylläpito-toimenpiteelle
-- Jotta tehtäväryhmän voi linkittää rahavarauksiin ja jotta tietomalli on kokonainen, tarivitaan tehäväryhmälle tehtävä, vaikka sille ei vaadittaisikaan toteumakirjauksia

INSERT INTO tehtavaryhma (nimi, jarjestys, nakyva, poistettu, luotu, luoja, yksiloiva_tunniste,
                          tehtavaryhmaotsikko_id, voimassaolo_alkuvuosi, toimenpide_id)
VALUES ('T5 - KT-reikävaluasfalttipaikkaus', 208, true, false, current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'), '3d9772fb-3c52-4310-9976-db3b260cc235',
        (select id from tehtavaryhmaotsikko where otsikko = '8 MUUTA'), 2025,
        (select id from toimenpide where koodi = '20191'));

INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, hinnoittelu, api_seuranta, tehtavaryhma, "mhu-tehtava?",
                     yksiloiva_tunniste, suunnitteluyksikko,
                     voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto,
                     "maaramitattava?")
VALUES ('KT-reikävaluasfalttipaikkaus (ELY-rahoitus)', (select id from toimenpide where koodi = '20191'),
        current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'kpl', '{kokonaishintainen}',
        false, (select id from tehtavaryhma where yksiloiva_tunniste = '3d9772fb-3c52-4310-9976-db3b260cc235'), true,
        'f5f1dde9-93ea-47be-9f5d-aff9ead7add9', 'kpl', 2025, true, false, false, true);

-- Linkitetään uusi tehtäväryhmä Reikävalu-rahavaraukseen edellä luodun tehtävän kautta
INSERT INTO rahavaraus_tehtava(rahavaraus_id, tehtava_id, luotu, luoja)
VALUES ((select id from rahavaraus where nimi = 'Reikävalu'),
        (select id from tehtava where yksiloiva_tunniste = 'f5f1dde9-93ea-47be-9f5d-aff9ead7add9'), current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Päivitetään parempi nimi reikävalurahavaraukselle
UPDATE rahavaraus SET nimi = 'Tilaajan rahavaraus KT-reikävaluasfalttipaikkaus' WHERE nimi = 'Reikävalu';
