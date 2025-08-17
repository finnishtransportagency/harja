CREATE OR REPLACE FUNCTION luo_mhu_muutoksia(urakka_id INTEGER, alkaen_pvm DATE)
    RETURNS BOOLEAN AS
$$
declare
    _versio INTEGER := 1;
    _toimenpideinstanssi_id_talvihoito INTEGER := (SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') AND urakka = urakka_id); -- Talvihoito
    _toimenpideinstanssi_id_liikymp_hoito INTEGER := (SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') AND urakka = urakka_id); -- Liikenneympäristön hoito
    _toimenpideinstanssi_id_paall_paikk INTEGER := (SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') AND urakka = urakka_id); -- Päällystepaikkaukset
    _toimenpideinstanssi_id_sorateiden_hoito INTEGER := (SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') AND urakka = urakka_id); -- Sorateiden hoito
    _toimenpideinstanssi_id_mhu_yllapito INTEGER := (SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') AND urakka = urakka_id); -- MHU Ylläpito
    _toimenpideinstassi_id_hoidon_johto INTEGER := (SELECT id FROM toimenpideinstanssi WHERE urakka = urakka_id AND toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151'));
    _johto_ja_hallintokorvaus_tehtavaryhma_id INTEGER := (SELECT id FROM tehtavaryhma WHERE nimi = 'J - Johto- ja hallintokorvaus');
    _tehtava_id_ab_paikkaus INTEGER := (SELECT id FROM tehtava WHERE nimi = 'AB-paikkaus levittäjällä');
    _tehtava_id_soratien_rummut_alle_600mm INTEGER := (SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm');
    _tehtava_id_soratien_rummut_600_1000mm INTEGER := (SELECT id FROM tehtava WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm');
    -- jotta ao. logiikka toimii, alkaen_pvm oltava tammi-syyskuun aikana
    ensimmainen_tayden_hkn_alkuvuosi INTEGER := (SELECT EXTRACT(YEAR FROM alkaen_pvm) :: INTEGER);
    viimeinen_tayden_hkn_alkuvuosi INTEGER := (SELECT EXTRACT(YEAR FROM (SELECT loppupvm FROM urakka WHERE id = urakka_id)) :: INTEGER);
    kayttaja_id_tero INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero');

BEGIN
-- itse muutoksia on vain yksi, ja siitä tallennetaan kaikille tuleville hoitokausille kustannus- ja määrävaikutus
-- muutos 1: päällysteiden paikkausta enemmän
    INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
    VALUES (_versio,urakka_id, alkaen_pvm, 'pysyva', 'Päällysteen paikkausmuutos',
            'Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.', kayttaja_id_tero,
            NOW());

-- muutos 2: erillisrahoitettu sorastus
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
VALUES (_versio,urakka_id, alkaen_pvm, 'erillisrahoitettu', 'Erillisrahoitettu sorastusmuutos',
        'Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.', kayttaja_id_tero,
        NOW());
INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi, summa)
VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Erillisrahoitettu sorastusmuutos'), 'hankintakustannukset',
        _toimenpideinstanssi_id_sorateiden_hoito, ensimmainen_tayden_hkn_alkuvuosi, 3000);

-- muutos 3: poikkeama tehtävä- ja määräluettelon määrästä yksittäisen hoitovuoden osalta, ei tehdäkään sorateiden rumpuja
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
VALUES (_versio,urakka_id, alkaen_pvm, 'maarapoikkeama', 'Tämän hoitovuoden määräpoikkeamamuutos',
        'Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.', kayttaja_id_tero,
        NOW());
INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi, summa)
VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Tämän hoitovuoden määräpoikkeamamuutos'), 'hankintakustannukset',
        _toimenpideinstanssi_id_mhu_yllapito, ensimmainen_tayden_hkn_alkuvuosi, 1000);
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
        urakka_id, NOW(), kayttaja_id_tero);
INSERT INTO mhu_muutos_liite (muutos, liite)
VALUES ((SELECT id FROM mhu_muutos WHERE nimi = 'Tämän hoitovuoden määräpoikkeamamuutos'),
        (SELECT id FROM liite WHERE nimi = 'rumpu.jpg'));

FOR vuosi IN ensimmainen_tayden_hkn_alkuvuosi..viimeinen_tayden_hkn_alkuvuosi LOOP
            -- muutos 1: päällysteiden paikkausta enemmän - kustannusvaikutus
            INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi, summa)
            VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Päällysteen paikkausmuutos'), 'hankintakustannukset',
                    _toimenpideinstanssi_id_paall_paikk, vuosi, 1000);
            -- muutos 1: päällysteiden paikkausta enemmän - tehtävä- ja määräluettelon muutokset
            INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi, edellinen_maara, maaramuutos, uusi_maara)
            VALUES (_versio, (SELECT id FROM mhu_muutos WHERE nimi = 'Päällysteen paikkausmuutos'), _tehtava_id_ab_paikkaus, vuosi,
                    1000, 100, 1100);

        END LOOP;

-- Johto- ja hallintokorvauksen muutos
INSERT INTO mhu_muutos (versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja)
VALUES  (1, urakka_id, '2025-06-25', 'johto-ja-hallintokorvaus', null, 'Työmääräarviot ylittyivät',
         kayttaja_id_tero);
INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja,  lisatieto, koontilaskun_kuukausi)
VALUES  (1230, '2025-10-15', 36, kayttaja_id_tero,
         'Muutoksesta automaattisesti luotu kulu 1', 'lokakuu/5-hoitovuosi');
INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, luoja, tyyppi, tavoitehintainen)
VALUES  ( 0, (SELECT id FROM kulu WHERE lisatieto = 'Muutoksesta automaattisesti luotu kulu 1'), 1230, _toimenpideinstassi_id_hoidon_johto, _johto_ja_hallintokorvaus_tehtavaryhma_id, 'kokonaishintainen',
          kayttaja_id_tero, 'jjh-muutos', true);
INSERT INTO mhu_muutos_kulu (versio, muutos, kulu)
VALUES  (1, (SELECT id FROM mhu_muutos WHERE syy = 'Työmääräarviot ylittyivät'),
         (SELECT id FROM kulu WHERE lisatieto = 'Muutoksesta automaattisesti luotu kulu 1'));

-- Iihin vähän kiinteähintaista työtä, jotta nähdään että nousee oikein pysyävän muutoksen lomakkeelle
insert into public.kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, luotu, luoja, muokattu, muokkaaja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
values  (2025, 10, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.802000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 11, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.880000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 12, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.881000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 1, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.882000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 2, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.883000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 3, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.884000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 4, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.884000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 5, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.885000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 6, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.886000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 7, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.886000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 8, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.887000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 9, 1000, _toimenpideinstanssi_id_talvihoito, null, null, 45, '2025-08-12 15:51:33.888000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 10, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.631000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 11, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.709000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 12, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.710000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 1, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.712000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 2, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 3, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 4, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.714000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 5, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 6, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 7, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 8, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 9, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, 45, '2025-08-12 15:51:43.717000', kayttaja_id_tero, null, null, null, null, null, 0),
        -- vielä vähän päällysteiden paikkauksia... kahdelle hoitovuodelle
        (2025, 10, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.631000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 11, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.709000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 12, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.710000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 1, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.712000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 2, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 3, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 4, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.714000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 5, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 6, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 7, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 8, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, 45, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 9, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.717000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2026, 10, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.631000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2026, 11, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.709000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2026, 12, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.710000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 1, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.712000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 2, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.713000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 3, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.713000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 4, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.714000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 5, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.715000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 6, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.715000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 7, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.716000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 8, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.716000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 9, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, 45, '2025-08-12 15:51:43.717000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0);

    RETURN TRUE;

end
$$ language plpgsql;


SELECT * FROM luo_mhu_muutoksia((SELECT id FROM harja.public.urakka WHERE nimi = 'Iin MHU 2021-2026'),
              '2025-05-07');
