-- MH-urakat. Vuoden 2026 sopimusmuutosten tehtäväpäivitykset.

-- Päivitetään vanhat viherhoidon tehtävät päättymään vuoden 2025 urakoihin.
UPDATE tehtava
SET voimassaolo_loppuvuosi = 2025
WHERE NIMI IN ('Nurmetuksen hoito / niitto T1/E1',
               'Nurmetuksen hoito / niitto T2/E2',
               'Puiden ja pensaiden hoito T1/E1',
               'Puiden ja pensaiden hoito T2/E2/N1');

-- Lisätään viherhoitoon vuodesta 2026 lähtien voimassa olevia uusia tehtäviä luokitusmuutoksen takia.
INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                     voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, nopeusrajoitus,
                     "maaramitattava?")
VALUES ('Nurmetuksen hoito / niitto VH1',
        (select id from toimenpide where koodi = '23116'),
        current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'),
        '-', 680, '{kokonaishintainen}',
        (select id from tehtavaryhma where nimi = 'N - Nurmetukset ja muut vihertyöt'),
        true,
        2026,
        false,
        false,
        false,
        108,
        false);

INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                     voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, nopeusrajoitus,
                     "maaramitattava?")
VALUES ('Nurmetuksen hoito / niitto VH2',
        (select id from toimenpide where koodi = '23116'),
        current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'),
        '-', 690, '{kokonaishintainen}',
        (select id from tehtavaryhma where nimi = 'N - Nurmetukset ja muut vihertyöt'),
        true,
        2026,
        false,
        false,
        false,
        108,
        false);

INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                     voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, nopeusrajoitus,
                     "maaramitattava?")
VALUES ('Puiden ja pensaiden hoito VH1',
        (select id from toimenpide where koodi = '23116'),
        current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'),
        '-', 700, '{kokonaishintainen}',
        (select id from tehtavaryhma where nimi = 'N - Nurmetukset ja muut vihertyöt'),
        true,
        2026,
        false,
        false,
        false,
        108,
        false);

INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                     voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", aluetieto, nopeusrajoitus,
                     "maaramitattava?")
VALUES ('Puiden ja pensaiden hoito VH2',
        (select id from toimenpide where koodi = '23116'),
        current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'),
        '-', 710, '{kokonaishintainen}',
        (select id from tehtavaryhma where nimi = 'N - Nurmetukset ja muut vihertyöt'),
        true,
        2026,
        false,
        false,
        false,
        108,
        false);


