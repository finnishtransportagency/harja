-- Lisätään uusi tehtävä: Tarkastusajo
INSERT INTO tehtava (nimi, emo, luotu, luoja, poistettu, yksikko, jarjestys, hinnoittelu,
                     api_seuranta, suoritettavatehtava, piilota, tehtavaryhma, "mhu-tehtava?",
                     yksiloiva_tunniste, suunnitteluyksikko, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                     kasin_lisattava_maara, "raportoi-tehtava?", materiaaliluokka_id, materiaalikoodi_id, aluetieto,
                     nopeusrajoitus, linkkitunniste, "maaramitattava?")
VALUES ( 'Tarkastusajo', (select id from toimenpide where koodi = 'VALA_YKSHINT'), NOW(),
         (select id from kayttaja where kayttajanimi = 'Integraatio'), false,
         'jkm', null, '{yksikkohintainen}', true,
         'valaistusurakoiden tarkastusajo', null, null, null,
         '31e23876-f4c2-4dfb-835f-925e5903ff2d', null, null, null,
         false, false, null, null,
         false, 140, null, false);

-- Päivitetaan apitunnus samaksi, kuin tehtävän id
UPDATE tehtava SET api_tunnus = id WHERE suoritettavatehtava = 'valaistusurakoiden tarkastusajo';
