-- Lisätään uusi tunneleihin liittyvä tehtävä MHU Ylläpito tehtäväryhmälle, jotta Tunneleiden hoito -rahavaraukselle olisi
-- mahdollista valita MHY Ylläpito tehtäväryhmä
insert into tehtava (nimi, emo, luotu, muokattu, luoja, poistettu, yksikko, jarjestys, hinnoittelu, api_seuranta,
                     suoritettavatehtava, piilota, api_tunnus, tehtavaryhma, "mhu-tehtava?", yksiloiva_tunniste,
                     suunnitteluyksikko, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, kasin_lisattava_maara,
                     "raportoi-tehtava?", materiaaliluokka_id, materiaalikoodi_id, aluetieto, nopeusrajoitus)
values  ( 'Tunneleiden ylläpito',
          (SELECT id FROM toimenpide WHERE koodi = '20191'), NOW(), null,
          (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), false, 'euroa',
          1531, null, null, null, null, null,
          (SELECT id FROM tehtavaryhma WHERE nimi = 'Muut, MHU ylläpito (F)'), true,
          null, 'euroa', null, null,
          false, false, null, null, false,
          108);

-- Lisätään uusi tehtävä rahavaraukselle
-- 'Rahavaraus E - Pysäkkikatokset'
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
VALUES ((SELECT id from rahavaraus WHERE nimi = 'Tunneleiden hoito'),
        (SELECT id from tehtava WHERE nimi = 'Tunneleiden ylläpito'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'));


SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Tunneleiden ylläpito'
   AND rv.nimi = 'Tunneleiden hoito';
