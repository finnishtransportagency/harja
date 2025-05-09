CREATE OR REPLACE FUNCTION luo_mhu_muutoksia(urakka_id INTEGER, alkaen_pvm DATE)
    RETURNS BOOLEAN AS
$$
declare
    _versio INTEGER := 1;
    _toimenpide_id_paall_paikk INTEGER := (SELECT id FROM toimenpide WHERE koodi = '20107'); -- Päällystepaikkaukset
    _toimenpide_id_sorateiden_hoito INTEGER := (SELECT id FROM toimenpide WHERE koodi = '23124'); -- Sorateiden hoito
    _toimenpide_id_mhu_yllapito INTEGER := (SELECT id FROM toimenpide WHERE koodi = '20191'); -- MHU Ylläpito
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
    RETURN TRUE;

end
$$ language plpgsql;


SELECT * FROM luo_mhu_muutoksia((SELECT id FROM harja.public.urakka WHERE nimi = 'Iin MHU 2021-2026'),
              '2025-05-07');
