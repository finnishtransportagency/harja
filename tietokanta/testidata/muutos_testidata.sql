CREATE OR REPLACE FUNCTION luo_mhu_muutoksia(urakka_id INTEGER, alkaen_pvm DATE)
    RETURNS BOOLEAN AS
$$
declare
    _versio INTEGER := 1;
    _toimenpide_id_paall_paikk INTEGER := (SELECT id FROM toimenpide WHERE koodi = '20107'); -- Päällystepaikkaukset
    _toimenpide_id_sorateiden_hoito INTEGER := (SELECT id FROM toimenpide WHERE koodi = '23124'); -- Sorateiden hoito
    _toimenpide_id_mhu_yllapito INTEGER := (SELECT id FROM toimenpide WHERE koodi = '20191'); -- MHU Ylläpito
    _toimenpideinstassi_id_hoidon_johto INTEGER := (SELECT id FROM toimenpideinstanssi WHERE urakka = urakka_id AND toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151'));
    _johto_ja_hallintokorvaus_tehtavaryhma_id INTEGER := (SELECT id FROM tehtavaryhma WHERE nimi = 'J - Johto- ja hallintokorvaus');
    _tehtava_id_ab_paikkaus INTEGER := (SELECT id FROM tehtava WHERE nimi = 'AB-paikkaus levittäjällä');
    _tehtava_id_soratien_rummut_alle_600mm INTEGER := (SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm');
    _tehtava_id_soratien_rummut_600_1000mm INTEGER := (SELECT id FROM tehtava WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm');
    -- jotta ao. logiikka toimii, alkaen_pvm oltava tammi-syyskuun aikana
    ensimmainen_tayden_hkn_alkuvuosi INTEGER := (SELECT EXTRACT(YEAR FROM alkaen_pvm) :: INTEGER);
    viimeinen_tayden_hkn_alkuvuosi INTEGER := (SELECT EXTRACT(YEAR FROM (SELECT loppupvm FROM urakka WHERE id = urakka_id)) :: INTEGER);

BEGIN
-- itse muutoksia on vain yksi, ja siitä tallennetaan kaikille tuleville hoitokausille kustannus- ja määrävaikutus
-- muutos 1: päällysteiden paikkausta enemmän
    INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
    VALUES (_versio,urakka_id, alkaen_pvm, 'pysyva', 'Päällysteen paikkausmuutos',
            'Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.', (select id from kayttaja where kayttajanimi = 'tero'),
            NOW());

-- muutos 2: erillisrahoitettu sorastus
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
VALUES (_versio,urakka_id, alkaen_pvm, 'erillisrahoitettu', 'Erillisrahoitettu sorastusmuutos',
        'Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.', (select id from kayttaja where kayttajanimi = 'tero'),
        NOW());
INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpide, hoitokauden_alkuvuosi, summa)
VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Erillisrahoitettu sorastusmuutos'), 'hankintakustannukset',
        _toimenpide_id_sorateiden_hoito, ensimmainen_tayden_hkn_alkuvuosi, 3000);

-- muutos 3: poikkeama tehtävä- ja määräluettelon määrästä yksittäisen hoitovuoden osalta, ei tehdäkään sorateiden rumpuja
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
VALUES (_versio,urakka_id, alkaen_pvm, 'maarapoikkeama', 'Tämän hoitovuoden määräpoikkeamamuutos',
        'Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.', (select id from kayttaja where kayttajanimi = 'tero'),
        NOW());
INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpide, hoitokauden_alkuvuosi, summa)
VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Tämän hoitovuoden määräpoikkeamamuutos'), 'hankintakustannukset',
        _toimenpide_id_mhu_yllapito, ensimmainen_tayden_hkn_alkuvuosi, 1000);
INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi, edellinen_maara, maaramuutos, uusi_maara)
VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Tämän hoitovuoden määräpoikkeamamuutos'), _tehtava_id_soratien_rummut_alle_600mm,
        ensimmainen_tayden_hkn_alkuvuosi,
        30, -30, 0);
INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi, edellinen_maara, maaramuutos, uusi_maara)
VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Tämän hoitovuoden määräpoikkeamamuutos'), _tehtava_id_soratien_rummut_600_1000mm,
        ensimmainen_tayden_hkn_alkuvuosi,
        40, -40, 0);
INSERT INTO liite (nimi, tyyppi, lahde, urakka, luotu, luoja)
VALUES ('rumpu.jpg', 'image/png', 'harja-ui'::lahde,
        urakka_id, NOW(), (select id from kayttaja where kayttajanimi = 'tero'));
INSERT INTO mhu_muutos_liite (muutos, liite)
VALUES ((SELECT id FROM mhu_muutos WHERE nimi = 'Tämän hoitovuoden määräpoikkeamamuutos'),
        (SELECT id FROM liite WHERE nimi = 'rumpu.jpg'));

FOR vuosi IN ensimmainen_tayden_hkn_alkuvuosi..viimeinen_tayden_hkn_alkuvuosi LOOP
            -- muutos 1: päällysteiden paikkausta enemmän - kustannusvaikutus
            INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpide, hoitokauden_alkuvuosi, summa)
            VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Päällysteen paikkausmuutos'), 'hankintakustannukset',
                    _toimenpide_id_paall_paikk, vuosi, 1000);
            -- muutos 1: päällysteiden paikkausta enemmän - tehtävä- ja määräluettelon muutokset
            INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi, edellinen_maara, maaramuutos, uusi_maara)
            VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Päällysteen paikkausmuutos'), _tehtava_id_ab_paikkaus, vuosi,
                    1000, 100, 1100);

        END LOOP;

-- Johto- ja hallintokorvauksen muutos
INSERT INTO mhu_muutos (versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja)
VALUES  (1, urakka_id, '2025-06-25', 'johto-ja-hallintokorvaus', null, 'Työmääräarviot ylittyivät',
         (select id from kayttaja where kayttajanimi = 'tero'));
INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja,  lisatieto, koontilaskun_kuukausi)
VALUES  (1230, '2025-10-15', 36, (select id from kayttaja where kayttajanimi = 'tero'),
         'Muutoksesta automaattisesti luotu kulu 1', 'lokakuu/5-hoitovuosi');
INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, luoja, tyyppi, tavoitehintainen)
VALUES  ( 0, (SELECT id FROM kulu WHERE lisatieto = 'Muutoksesta automaattisesti luotu kulu 1'), 1230, _toimenpideinstassi_id_hoidon_johto, _johto_ja_hallintokorvaus_tehtavaryhma_id, 'kokonaishintainen',
          (select id from kayttaja where kayttajanimi = 'tero'), 'jjh-muutos', true);
INSERT INTO mhu_muutos_kulu (versio, muutos, kulu)
VALUES  (1, (SELECT id FROM mhu_muutos WHERE syy = 'Työmääräarviot ylittyivät'),
         (SELECT id FROM kulu WHERE lisatieto = 'Muutoksesta automaattisesti luotu kulu 1'));

    RETURN TRUE;

end
$$ language plpgsql;


SELECT * FROM luo_mhu_muutoksia((SELECT id FROM harja.public.urakka WHERE nimi = 'Iin MHU 2021-2026'),
              '2025-05-07');



-- ==================================================
-- Määrämitattavat tehtävämäärämuutokset - iin mhu 
-- ==================================================
CREATE OR REPLACE FUNCTION luo_maaramitattavat_muutos_kulut(p_summat int[])
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  urakka_id_ii_mhu              INTEGER;
  kayttaja_id                   INTEGER;
  tehtava_soratie               INTEGER;
  tinst_ii_mhu_liikenne         INTEGER;
  tinst_ii_mhu_soratie          INTEGER;
  tinst_ii_mhu_yllapito         INTEGER;
  tehtavaryhma_liikennemerkit   INTEGER;
  tehtavaryhma_puun_poisto      INTEGER;
  tehtavaryhma_soratie          INTEGER;
  tehtavaryhma_rummut           INTEGER;
BEGIN
  kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
  urakka_id_ii_mhu := (select id from urakka where nimi LIKE 'Iin MHU 2021-%');
  tinst_ii_mhu_liikenne := (select id from toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Liikenneympäristön hoito TP');
  tinst_ii_mhu_soratie := (select id from toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Soratien hoito TP');
  tinst_ii_mhu_yllapito := (select id from toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 MHU Ylläpito TP');
  tehtava_soratie := (select id from tehtavaryhma where yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1');
  tehtavaryhma_liikennemerkit := (select id from tehtavaryhma where yksiloiva_tunniste = '87a3bd38-ae0a-4c74-ad0d-38a6d5d512ad');
  tehtavaryhma_puun_poisto := (select id from tehtavaryhma where yksiloiva_tunniste = '405a8a12-70c0-4ef6-91f4-689197493239');
  tehtavaryhma_soratie := (select id from tehtavaryhma where yksiloiva_tunniste = 'f51c3d67-d21f-4286-bbb5-9354dcd073d6');
  tehtavaryhma_rummut := (select id from tehtavaryhma where yksiloiva_tunniste = 'd6d8e712-4b08-4954-bea1-c772a37492da');

  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  -- Liikenneympäristön hoito 
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[1],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[1] AND erapaiva = '2026-06-01'),
  p_summat[1],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_liikennemerkit,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen'));

  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[2],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (1,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[2] AND erapaiva = '2026-06-01'),
  p_summat[2],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_liikennemerkit,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'));

  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[3],'2025-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/4-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[3] AND erapaiva = '2025-06-01'),
  p_summat[3],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_liikennemerkit,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'));

  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[4],'2024-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/3-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[4] AND erapaiva = '2024-06-01'),
  p_summat[4],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_liikennemerkit,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'));


  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  -- V - Vesakonraivaukset ja puun poisto
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[5],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[5] AND erapaiva = '2026-06-01'),
  p_summat[5],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_puun_poisto,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Runkopuiden poisto'));


  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  -- O - Sorapientareet
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[6],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[6] AND erapaiva = '2026-06-01'),
  p_summat[6],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_soratie,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto'));

  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[7],'2025-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/4-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[7] AND erapaiva = '2025-06-01'),
  p_summat[7],
  tinst_ii_mhu_liikenne,
  tehtavaryhma_soratie,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto'));


  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  -- C - Soratie
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[8],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[8] AND erapaiva = '2026-06-01'),
  p_summat[8],
  tinst_ii_mhu_soratie,
  tehtava_soratie,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto'));


  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  -- Rummut
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[9],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (0,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[9] AND erapaiva = '2026-06-01'),
  p_summat[9],
  tinst_ii_mhu_yllapito,
  tehtavaryhma_rummut,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm'));

  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[10],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (1,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[10] AND erapaiva = '2026-06-01'),
  p_summat[10],
  tinst_ii_mhu_yllapito,
  tehtavaryhma_rummut,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm'));
  
  -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
    (p_summat[11],'2026-06-01',urakka_id_ii_mhu,'2025-09-01 14:18:52.450004',kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');

  INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
  (2,
  (SELECT id FROM kulu WHERE kokonaissumma = p_summat[11] AND erapaiva = '2026-06-01'),
  p_summat[11],
  tinst_ii_mhu_yllapito,
  tehtavaryhma_rummut,'kokonaishintainen','2025-09-01 14:18:52.450',kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
  (SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet'));

END $$;


SELECT luo_maaramitattavat_muutos_kulut(ARRAY[255, 147, 389, 532, 1212, 344, 1420, 4200, 1101, 7850, 3600]);
