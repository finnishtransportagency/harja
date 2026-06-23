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
    _toimenpideinstassi_id_hoidon_johto INTEGER := (SELECT id FROM toimenpideinstanssi WHERE urakka = urakka_id AND toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151' limit 1));
    _johto_ja_hallintokorvaus_tehtavaryhma_id INTEGER := (SELECT id FROM tehtavaryhma WHERE nimi = 'J - Johto- ja hallintokorvaus');
    _tehtava_id_ab_paikkaus INTEGER := (SELECT id FROM tehtava WHERE nimi = 'AB-paikkaus levittäjällä' limit 1);
    _tehtava_id_soratien_rummut_alle_600mm INTEGER := (SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' limit 1);
    _tehtava_id_soratien_rummut_600_1000mm INTEGER := (SELECT id FROM tehtava WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' limit 1);
    -- jotta ao. logiikka toimii, alkaen_pvm oltava tammi-syyskuun aikana
    ensimmainen_tayden_hkn_alkuvuosi INTEGER := (SELECT EXTRACT(YEAR FROM alkaen_pvm) :: INTEGER);
    viimeinen_tayden_hkn_alkuvuosi INTEGER := (SELECT EXTRACT(YEAR FROM (SELECT loppupvm FROM urakka WHERE id = urakka_id)) :: INTEGER - 1);
    kayttaja_id_tero INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero');
    urakka_sopimus_id INTEGER := (SELECT id FROM sopimus WHERE urakka = urakka_id);

    -- Muutosten id:t talteen, jotta voidaan viitata suoraan niihin
    muutos_id_1 INTEGER := NULL;
    muutos_id_2 INTEGER := NULL;

    muutos_id_3 INTEGER := NULL;
    muutos_id_3_liite_1 INTEGER := NULL;

    muutos_id_4 INTEGER := NULL;
    muutos_id_4_kulu_1 INTEGER := NULL;

    muutos_id_5 INTEGER := NULL;

BEGIN
-- itse muutoksia on vain yksi, ja siitä tallennetaan kaikille tuleville hoitokausille kustannus- ja määrävaikutus

-- Muutos 1: [Pysyvä muutos] Päällysteiden paikkausta enemmän
   INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
   VALUES (_versio, urakka_id, alkaen_pvm, 'pysyva', 'Päällysteen paikkausmuutos',
           'Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.', kayttaja_id_tero,
           NOW())
RETURNING id INTO muutos_id_1;

-- Muutos 2: [Muutostyö: Erillisrahoitus] Erillisrahoitettu sorastus
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, alityyppi, nimi, syy, luoja, luotu)
VALUES (_versio, urakka_id, alkaen_pvm, 'muutostyo', 'erillisrahoitus', 'Erillisrahoitettu sorastusmuutos',
        'Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.', kayttaja_id_tero,
        NOW())
RETURNING id INTO muutos_id_2;

INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi, summa)
VALUES (_versio, muutos_id_2, 'erillishankinnat', NULL, ensimmainen_tayden_hkn_alkuvuosi, 3000);

-- Muutos 3: [Muutostyö: Poikkeama] Poikkeama tehtävä- ja määräluettelon määrästä yksittäisen hoitovuoden osalta, ei tehdäkään sorateiden rumpuja
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, alityyppi, nimi, syy, luoja, luotu)
VALUES (_versio,urakka_id, alkaen_pvm, 'muutostyo', 'poikkeama','Tämän hoitovuoden määräpoikkeamamuutos',
        'Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.', kayttaja_id_tero,
        NOW())
RETURNING id INTO muutos_id_3;

INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi, hoitokauden_alkuvuosi, summa)
VALUES (_versio, muutos_id_3, 'hankintakustannukset',
        _toimenpideinstanssi_id_mhu_yllapito, ensimmainen_tayden_hkn_alkuvuosi, 1000);
INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos)
VALUES (_versio, muutos_id_3, _tehtava_id_soratien_rummut_alle_600mm,
        ensimmainen_tayden_hkn_alkuvuosi, -30);
INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos)
VALUES (_versio, muutos_id_3, _tehtava_id_soratien_rummut_600_1000mm,
        ensimmainen_tayden_hkn_alkuvuosi, -40);
INSERT INTO liite (nimi, tyyppi, lahde, urakka, luotu, luoja)
VALUES ('rumpu.jpg', 'image/png', 'harja-ui'::lahde,
        urakka_id, NOW(), kayttaja_id_tero)
RETURNING id INTO muutos_id_3_liite_1;

INSERT INTO mhu_muutos_liite (muutos, liite)
VALUES (muutos_id_3,muutos_id_3_liite_1);


-- Muutos 4: Johto- ja hallintokorvauksen muutos
   INSERT INTO mhu_muutos (versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja)
   VALUES  (1, urakka_id, '2025-10-20', 'johto-ja-hallintokorvaus', null, 'Työmääräarviot ylittyivät',
            kayttaja_id_tero)
RETURNING id INTO muutos_id_4;

   INSERT INTO kulu (kokonaissumma, erapaiva, urakka, luoja,  lisatieto, koontilaskun_kuukausi)
   VALUES  (1230, '2025-10-15', urakka_id, kayttaja_id_tero,
            'Muutoksesta automaattisesti luotu kulu 1', 'lokakuu/5-hoitovuosi')
RETURNING id INTO muutos_id_4_kulu_1;

INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, luoja, tyyppi, tavoitehintainen)
VALUES  ( 0, muutos_id_4_kulu_1, 1230, _toimenpideinstassi_id_hoidon_johto, _johto_ja_hallintokorvaus_tehtavaryhma_id, 'kokonaishintainen',
          kayttaja_id_tero, 'jjh-muutos', true);
INSERT INTO mhu_muutos_kulu (versio, muutos, kulu)
VALUES  (1, muutos_id_4,muutos_id_4_kulu_1);


-- Muutos 5: [Pysyvä muutos] Edellisen hoitokauden pysyvä muutos
--           Tämän pitäisi tulla näkyviin "Aiemmilta hoitovuosilta jatkuvat pysyvät muutokset"-taulukkoon Muutos-näkymässä
INSERT INTO mhu_muutos(versio, urakka, voimassa_alkaen, tyyppi, nimi, syy, luoja, luotu)
   VALUES (_versio, urakka_id, (SELECT alkaen_pvm - INTERVAL '1 year'), 'pysyva', 'Lisää paikkausta',
           'Jonkin verran pitäisi paikkailla lisää tänä vuonna', kayttaja_id_tero,
           NOW())
RETURNING id INTO muutos_id_5;


-- Usean hoitokauden muutoksia varten loopataan hoitokaudet läpi ja lisätään data tässä
FOR vuosi IN ensimmainen_tayden_hkn_alkuvuosi..viimeinen_tayden_hkn_alkuvuosi
    LOOP
        -- Muutos 1: Päällysteiden paikkausta enemmän - kustannusvaikutus
        INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi,
                                                 hoitokauden_alkuvuosi, summa)
        VALUES (_versio, muutos_id_1, 'hankintakustannukset',
                _toimenpideinstanssi_id_paall_paikk, vuosi, 1000);
        -- Muutos 1: Päällysteiden paikkausta enemmän - tehtävä- ja määräluettelon muutokset
        INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi,
                                                        maaramuutos)
        VALUES (_versio, muutos_id_1, _tehtava_id_ab_paikkaus, vuosi, 100);

        -- Muutos 5: Lisää paikkausta (aiemman hoitovuoden pysyvä muutos) - kustannusvaikutus
        --           HOX: Muutos on voimassa alkaen edellisen hoitokauden alusta, mutta kustannusvaikutusta lisätään
        --                seuraaville kokonaisille hoitovuosille
        INSERT INTO mhu_muutos_kustannusvaikutus(versio, muutos, kustannuslaji, toimenpideinstanssi,
                                                 hoitokauden_alkuvuosi, summa)
        VALUES (_versio, muutos_id_5, 'hankintakustannukset',
                _toimenpideinstanssi_id_paall_paikk, vuosi, 1000);
        -- Muutos 5: Lisää paikkausta (aiemman hoitovuoden pysyvä muutos) - tehtävä- ja määräluettelon muutokset
        --           HOX: Muutos on voimassa alkaen edellisen hoitokauden alusta, mutta määrämuutoksia lisätään
        --           seuraaville kokonaisille hoitovuosille
        INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo(versio, muutos, tehtava, hoitokauden_alkuvuosi,
                                                        maaramuutos)
        VALUES (_versio, muutos_id_5, _tehtava_id_ab_paikkaus, vuosi, 100);

    END LOOP;

--

-- Iihin vähän kiinteähintaista työtä, jotta nähdään että nousee oikein pysyävän muutoksen lomakkeelle
-- TODO: Tämä ei välttämättä ole tarpeeksi geneerinen eri urakoihin, mutta toimii ainakin Iissä
insert into public.kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, luotu, luoja, muokattu, muokkaaja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
values  (2025, 10, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.802000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 11, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.880000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 12, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.881000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 1, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.882000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 2, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.883000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 3, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.884000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 4, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.884000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 5, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.885000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 6, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.886000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 7, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.886000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 8, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.887000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 9, 1000, _toimenpideinstanssi_id_talvihoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:33.888000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 10, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.631000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 11, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.709000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 12, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.710000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 1, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.712000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 2, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 3, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 4, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.714000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 5, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 6, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 7, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 8, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 9, 800, _toimenpideinstanssi_id_liikymp_hoito, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.717000', kayttaja_id_tero, null, null, null, null, null, 0),
        -- vielä vähän päällysteiden paikkauksia... kahdelle hoitovuodelle
        (2025, 10, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.631000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 11, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.709000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2025, 12, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.710000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 1, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.712000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 2, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 3, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.713000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 4, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.714000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 5, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 6, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.715000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 7, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 8, 10000, _toimenpideinstanssi_id_paall_paikk, null, null, urakka_sopimus_id, '2025-08-12 15:51:43.716000', kayttaja_id_tero, null, null, null, null, null, 0),
        (2026, 9, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.717000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2026, 10, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.631000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2026, 11, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.709000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2026, 12, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.710000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 1, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.712000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 2, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.713000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 3, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.713000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 4, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.714000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 5, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.715000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 6, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.715000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 7, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.716000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 8, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.716000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0),
        (2027, 9, 10000, _toimenpideinstanssi_id_paall_paikk, NULL, NULL, urakka_sopimus_id, '2025-08-12 15:51:43.717000', kayttaja_id_tero, NULL, NULL, NULL, NULL, NULL, 0);

    RETURN TRUE;

end
$$ language plpgsql;


SELECT * FROM luo_mhu_muutoksia((SELECT id FROM harja.public.urakka WHERE nimi = 'Iin MHU 2021-2026'),
              '2025-10-01');

SELECT * FROM luo_mhu_muutoksia((SELECT id FROM harja.public.urakka WHERE nimi = 'POP MHU Suomussalmi 2024-2029'),
                                '2025-10-01');



-- ==================================================
-- Määrämitattavat tehtävämäärämuutokset - kulut
-- ==================================================
CREATE OR REPLACE FUNCTION luo_maaramitattavat_muutos_kulut(p_summat int[], p_urakka_nimi text, p_vuosi int)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    urakka_id_mhu                 INTEGER;
    kayttaja_id                   INTEGER;
    tehtava_soratie               INTEGER;
    tinst_mhu_liikenne            INTEGER;
    tinst_mhu_soratie             INTEGER;
    tinst_mhu_yllapito            INTEGER;
    tehtavaryhma_liikennemerkit   INTEGER;
    tehtavaryhma_puun_poisto      INTEGER;
    tehtavaryhma_soratie          INTEGER;
    tehtavaryhma_rummut           INTEGER;
BEGIN
    kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
    urakka_id_mhu := (select id from urakka where nimi = p_urakka_nimi);
    tinst_mhu_liikenne := (select id from toimenpideinstanssi where nimi = p_urakka_nimi || ' Liikenneympäristön hoito TP');
    tinst_mhu_soratie := (select id from toimenpideinstanssi where nimi = p_urakka_nimi || ' Soratien hoito TP');
    tinst_mhu_yllapito := (select id from toimenpideinstanssi where nimi = p_urakka_nimi || ' MHU Ylläpito TP');
    tehtava_soratie := (select id from tehtavaryhma where yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1');
    tehtavaryhma_liikennemerkit := (select id from tehtavaryhma where yksiloiva_tunniste = '87a3bd38-ae0a-4c74-ad0d-38a6d5d512ad');
    tehtavaryhma_puun_poisto := (select id from tehtavaryhma where yksiloiva_tunniste = '405a8a12-70c0-4ef6-91f4-689197493239');
    tehtavaryhma_soratie := (select id from tehtavaryhma where yksiloiva_tunniste = 'f51c3d67-d21f-4286-bbb5-9354dcd073d6');
    tehtavaryhma_rummut := (select id from tehtavaryhma where yksiloiva_tunniste = 'd6d8e712-4b08-4954-bea1-c772a37492da');

    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    -- Liikenneympäristön hoito 
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[1],
         to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,
         NULL,NULL,false,NULL,
         '[Muutokset] Määrämitattava ',
         'kesakuu/5-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[1] AND erapaiva = to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[1],
         tinst_mhu_liikenne,
         tehtavaryhma_liikennemerkit,
         'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,
         NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen' limit 1));

    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[2],
         to_date(p_vuosi::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/4-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[2] AND erapaiva = to_date(p_vuosi::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[2],
         tinst_mhu_liikenne,
         tehtavaryhma_liikennemerkit,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' limit 1));
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[3],
         to_date((p_vuosi - 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/3-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[3] AND erapaiva = to_date((p_vuosi - 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[3],
         tinst_mhu_liikenne,
         tehtavaryhma_liikennemerkit,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' limit 1));
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[4],
         to_date((p_vuosi - 2)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/2-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[4] AND erapaiva = to_date((p_vuosi - 2)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[4],
         tinst_mhu_liikenne,
         tehtavaryhma_liikennemerkit,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' limit 1));


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    -- V - Vesakonraivaukset ja puun poisto
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[5],
         to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[5] AND erapaiva = to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[5],
         tinst_mhu_liikenne,
         tehtavaryhma_puun_poisto,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Runkopuiden poisto' limit 1));
    
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    -- O - Sorapientareet
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[6],
         to_date(p_vuosi::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/4-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[6] AND erapaiva = to_date(p_vuosi::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[6],
         tinst_mhu_liikenne,
         tehtavaryhma_soratie,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto' limit 1));
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[7],
         to_date(p_vuosi::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/4-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[7] AND erapaiva = to_date(p_vuosi::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[7],
         tinst_mhu_liikenne,
         tehtavaryhma_soratie,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto' limit 1));
    
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    -- C - Soratie
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[8],
         to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[8] AND erapaiva = to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[8],
         tinst_mhu_soratie,
         tehtava_soratie,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto' limit 1));


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    -- Rummut
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[9],
         to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (0,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[9] AND erapaiva = to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[9],
         tinst_mhu_yllapito,
         tehtavaryhma_rummut,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' limit 1));
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[10],
         to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (1,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[10] AND erapaiva = to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[10],
         tinst_mhu_yllapito,
         tehtavaryhma_rummut,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' limit 1));
    
    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO kulu (kokonaissumma,erapaiva,urakka,luotu,luoja,muokattu,muokkaaja,poistettu,laskun_numero,lisatieto,koontilaskun_kuukausi) VALUES
        (p_summat[11],
         to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD'),
         urakka_id_mhu,
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450004', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,'[Muutokset] Määrämitattava ','kesakuu/5-hoitovuosi');
    
    INSERT INTO kulu_kohdistus (rivi,kulu,summa,toimenpideinstanssi,tehtavaryhma,"maksueratyyppi",luotu,luoja,muokattu,muokkaaja,poistettu,lisatyon_lisatieto,rahavaraus_id,tyyppi,tavoitehintainen,tehtava) VALUES
        (2,
         (SELECT id FROM kulu WHERE kokonaissumma = p_summat[11] AND erapaiva = to_date((p_vuosi + 1)::text || '-06-01', 'YYYY-MM-DD')),
         p_summat[11],
         tinst_mhu_yllapito,
         tehtavaryhma_rummut,'kokonaishintainen',
         to_timestamp(p_vuosi::text || '-09-01 14:18:52.450', 'YYYY-MM-DD HH24:MI:SS.US'),
         kayttaja_id,NULL,NULL,false,NULL,NULL,'hankintakulu',true,
         (SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' limit 1));

END $$;


-- Euromäärät, urakka, alkuvuosi
SELECT luo_maaramitattavat_muutos_kulut(ARRAY[255, 147, 389, 532, 1212, 344, 1420, 4200, 1101, 7850, 3600], 'Iin MHU 2021-2026', 2025);
SELECT luo_maaramitattavat_muutos_kulut(ARRAY[355, 247, 489, 632, 2212, 444, 2420, 5200, 2101, 8850, 4600], 'POP MHU Kajaani 2025-2030', 2028);


-- ==================================================
-- Määrämitattavat tehtävämäärämuutokset - toteumat
-- ==================================================
CREATE OR REPLACE FUNCTION maaramitattava_toteuma_testidata_mhu(
    p_urakka text,         -- esim 'Iin MHU 2021-%'
    p_vuosi int            -- esim 2025 
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    v_alku int;
    v_loppu int;
    v_urakka_id int;
    v_sopimus_id int;
    v_kayttaja_id int;
BEGIN
    SELECT id INTO v_urakka_id FROM urakka WHERE nimi LIKE p_urakka LIMIT 1;
    SELECT id INTO v_sopimus_id FROM sopimus WHERE urakka = v_urakka_id;
    SELECT id INTO v_kayttaja_id FROM kayttaja WHERE kayttajanimi = 'jvh' LIMIT 1;
    SELECT EXTRACT(YEAR FROM alkupvm), EXTRACT(YEAR FROM loppupvm) INTO v_alku, v_loppu FROM urakka WHERE id = v_urakka_id;

    IF p_vuosi NOT BETWEEN v_alku AND v_loppu THEN
        RAISE EXCEPTION '%: vuosi % ei ole urakan voimassaoloaikana (%-%)', p_urakka, p_vuosi, v_alku, v_loppu;
    END IF;
    
    IF (p_vuosi - 2) NOT BETWEEN v_alku AND v_loppu THEN
        RAISE EXCEPTION '%: anna -2 vuoden buffer - vuosi % ei ole urakan voimassaoloaikana (%-%)', p_urakka, (p_vuosi - 2), v_alku, v_loppu;
    END IF;

    IF v_urakka_id IS NULL THEN  
        RAISE EXCEPTION 'Urakkaa ei löydy: %', p_urakka;
    END IF;

    IF v_kayttaja_id IS NULL THEN
        RAISE EXCEPTION 'Käyttäjää ei löydy.';
    END IF;

    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 1 ' || p_urakka);

    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 1 ' || p_urakka),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen'),
         10, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 1', p_vuosi);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp((p_vuosi - 1)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp((p_vuosi - 1)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp((p_vuosi - 1)::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 2 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 2 ' || p_urakka),
         to_timestamp((p_vuosi - 1)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'),
         4, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 2', p_vuosi - 1);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp((p_vuosi - 2)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp((p_vuosi - 2)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp((p_vuosi - 2)::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 3 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 3 ' || p_urakka),
         to_timestamp((p_vuosi - 2)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'),
         4, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 3 ' || p_urakka, p_vuosi - 2);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp((p_vuosi - 1)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp((p_vuosi - 1)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp((p_vuosi - 1)::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 4 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 4 ' || p_urakka),
         to_timestamp((p_vuosi - 1)::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto'),
         5, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 4', p_vuosi - 1);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 5 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 5 ' || p_urakka),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Maakivien (>1m3) poisto'),
         43, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 5', p_vuosi);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 6 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 6 ' || p_urakka),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' limit 1),
         38, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 6', p_vuosi);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 7 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 7 ' || p_urakka),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' limit 1),
         15, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 7', p_vuosi);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 8 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 8 ' || p_urakka),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/päällystetyt tiet' limit 1),
         2450, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 8', p_vuosi);


    -- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    INSERT INTO toteuma (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto)
    VALUES
        (v_kayttaja_id, 'harja-ui'::lahde, v_urakka_id, v_sopimus_id,
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         to_timestamp(p_vuosi::text || '-11-30 18:05:00', 'YYYY-MM-DD HH24:MI:SS'),
         NULL, NULL, 'kokonaishintainen',
         '[Muutokset] Määrämitattava toteuma 9 ' || p_urakka);
    
    INSERT INTO toteuma_tehtava (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
    VALUES
        (v_kayttaja_id,
         (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 9 ' || p_urakka),
         to_timestamp(p_vuosi::text || '-11-30 17:00:00', 'YYYY-MM-DD HH24:MI:SS'),
         (SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' limit 1),
         3854, v_urakka_id,
         '[Muutokset] Määrämitattava toteuma 9', p_vuosi);


END $$;

-- Vuosi pitää osua urakan voimassaoloon
-- Ei voi olla 2 ensimmäistä hoitokautta
SELECT maaramitattava_toteuma_testidata_mhu('Iin MHU 2021-%', 2025);
SELECT maaramitattava_toteuma_testidata_mhu('POP MHU Kajaani 2025-%', 2028);
