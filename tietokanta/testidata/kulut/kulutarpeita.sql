-- Lisätään tehtävä, jotta digitalisaatio tehtäväryhmä saadaan näkyviin, kun sitä tarvitaan toimenpiteen kautta
-- Emo 612 = toimenpide.id ja viittaa Liikenneympäirstön hoitoon
insert into tehtava (nimi, emo, luotu, muokattu, luoja, poistettu, yksikko, jarjestys, hinnoittelu, api_seuranta, suoritettavatehtava, piilota, api_tunnus, tehtavaryhma, "mhu-tehtava?", yksiloiva_tunniste, suunnitteluyksikko, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", materiaaliluokka_id, materiaalikoodi_id, aluetieto, nopeusrajoitus)
values  ( 'Digitalisaation edistäminen ja innovaatioiden kehittäminen', 612, '2024-10-10 08:46:02.742654', null,
          (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), false, 'euroa', 1475, '{yksikkohintainen}', null, null, null, null,
         (SELECT id FROM tehtavaryhma WHERE nimi = 'Digitalisaatio ja innovaatiot (T4)'), true, null, 'euroa', 2018, 2018, true, false, null, null, false, 108);
