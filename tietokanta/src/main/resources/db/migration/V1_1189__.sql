-- Lisätään uusi liukkauden torjuntaan liittyvä tehtäväryhmä Talvihoito-toimenpiteen alle
INSERT INTO tehtavaryhma (nimi, jarjestys, nakyva, poistettu, luotu, luoja, yksiloiva_tunniste, tehtavaryhmaotsikko_id,
                          voimassaolo_alkuvuosi, toimenpide_id)
VALUES ('B4 - Ennalta arvaamattomien kuljetusten avustaminen hiekoituksella (materiaali)', 25, false, false,
        current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'),
        '3a5cb840-11a7-438f-bdae-a87da64bf98a', 1, 2025, (select id from toimenpide where koodi = '23104'));

-- Lisätään tehtäväryhmään uusi tehtävä, jolle 2025 urakoista lähtien raportoidaan ennalta arvaamattomiin kuljetuksiin liittyvät toteumat.
-- Erona vanhaan tehtävään on tehtäväryhmä, johon tehtävä linkitetään (ennen A - Talvihoito, jatkossa B4 - Ennalta arvaamattomien kuljetusten avustaminen (materiaali)).
-- Käytetään vanhan tehtävän apitunnusta uudessa tehtävässä, jotta urakoitsijajärjestelmät voivat tarvittaessa lähettää toteumaa eri tehtäviin, riippuen siitä minä vuonna urakka on alkanut.
-- Harja huolehtii siitä, että toteuma tallentuu oikealle tehtävälle.
-- Linkitetään vanha ja uusi tehtävä toisiinsa
INSERT INTO tehtava(nimi, emo, yksikko, jarjestys, hinnoittelu, api_tunnus, tehtavaryhma, "mhu-tehtava?",
                    suunnitteluyksikko, voimassaolo_alkuvuosi, kasin_lisattava_maara, "raportoi-tehtava?",
                    materiaaliluokka_id, materiaalikoodi_id, aluetieto,
                    nopeusrajoitus, linkkitunniste, luotu, luoja)
VALUES ('Ennalta arvaamattomien kuljetusten avustaminen (materiaali)',
        (select id from toimenpide where koodi = '23104'),
        'tonni', 280, '{kokonaishintainen}',
        (select api_tunnus
         from tehtava
         where nimi = 'Ennalta arvaamattomien kuljetusten avustaminen'
           and tehtavaryhma = (select id
                               from tehtavaryhma
                               where yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac')),
        (select id from tehtavaryhma where yksiloiva_tunniste = '3a5cb840-11a7-438f-bdae-a87da64bf98a'),
        true, 'tonni', 2025, false, false, (select id from materiaaliluokka where nimi = 'Hiekoitushiekka'), (select id from materiaalikoodi where nimi = 'Hiekoitushiekka'), true, 108,
        '2d27d7d0-7240-412d-b8fc-67e669087803',
        current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Lisätään vanhan tehtävän käytön päättymisvuosi.
-- Käytön päättyminen tarkoittaa sitä, että voimassaolo_loppuvuosi-kentän mukaisena vuonna
-- aloittaneet urakat (tässä 2024 aloittaneet) käyttävät tehtävää urakkakautensa loppuun saakka,
-- mutta uudemmat urakat eivät raportoi toteumia kyseiselle tehtävälle.
UPDATE tehtava
SET voimassaolo_loppuvuosi = 2024,
    linkkitunniste         = '2d27d7d0-7240-412d-b8fc-67e669087803', -- yhteinen tunniste vanhalle ja uudelle Ennalta arvaamattomien kuljetusten avustaminen-tehtävälle
    muokattu               = current_timestamp,
    muokkaaja              = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen'
  and tehtavaryhma = (select id
                      from tehtavaryhma
                      where yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac');
