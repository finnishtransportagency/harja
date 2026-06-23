INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-15', 666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-15', 6666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-09-15', 3666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'lokakuu/1-hoitovuosi');

INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja, tehtava) VALUES
((select id from kulu where kokonaissumma = 666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where yksiloiva_tunniste = '405a8a12-70c0-4ef6-91f4-689197493239'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 333.33, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), (select id from tehtava where nimi = 'Runkopuiden poisto'));
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 666.66), 2, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where yksiloiva_tunniste = '1855032a-2bb3-46d4-b9b4-c6d4e4c25d05'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 222.22, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 666.66), 3, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where yksiloiva_tunniste = '430b0c7e-64c1-42e3-99d2-35d17f9fceba'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 111.11, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 6666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where yksiloiva_tunniste = '0250dcc5-a13c-4efe-87ee-a7a1b8f65764'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 2222.22, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 6666.66), 2, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where yksiloiva_tunniste = 'c3cb9e68-7f08-4145-ad8f-f2985e8f1658'), 'akillinen-hoitotyo'::MAKSUERATYYPPI, 'rahavaraus', 4444.44, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa,  luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 3666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'),
 (select id from tehtavaryhma where yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 3666.66, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO liite (nimi, tyyppi, lahde, urakka, luotu, luoja) VALUES ('pensas-2019080019.jpg', 'image/png', 'harja-ui'::lahde, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_liite (kulu, liite, luotu, luoja) VALUES ((select id from kulu where kokonaissumma = 666.66), (select id from liite where nimi = 'pensas-2019080019.jpg'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Käytetään nimetöntä koodi blokkia, jotta voidaan määritellä muuttujia
DO
$$
    DECLARE
        kayttaja_id              INTEGER;
        urakka_id                INTEGER;
        tinst_talvihoito         INTEGER;
        tinst_lyh                INTEGER;
        tinst_mhu_hoidon_johto   INTEGER;
        tinst_soratie            INTEGER;
        tinst_paallystys         INTEGER;
        tinst_korvaus            INTEGER;
        tinst_yllapito           INTEGER;
        tehtava_talvihoito       INTEGER;
        tehtava_soratie          INTEGER;
        tehtava_paikkaus         INTEGER;
        tehtava_yllapito         INTEGER;
        tehtava_palkkio          INTEGER;
        tehtava_korvaus          INTEGER;
        tehtava_erillishankinnat INTEGER;
        tehtava_mhu_hoidon_johto INTEGER;
        tehtava_muut             INTEGER;
        rahavaraus_varalasku     INTEGER;

    BEGIN
        kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
        urakka_id := (select id from urakka where nimi = 'Oulun MHU 2019-2024');
        tinst_talvihoito := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP');
        tinst_lyh := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP');
        tinst_soratie := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Soratien hoito TP');
        tinst_paallystys := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Päällystepaikkaukset TP');
        tinst_korvaus := (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Korvausinvestointi TP');
        tinst_yllapito := (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Ylläpito TP');
        tinst_mhu_hoidon_johto := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP');
        tehtava_talvihoito := (select id from tehtavaryhma where yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac');
        tehtava_soratie := (select id from tehtavaryhma where yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1');
        tehtava_paikkaus := (select id from tehtavaryhma where yksiloiva_tunniste = 'b1cca2a5-6445-4f49-878d-a95f144cc190');
        tehtava_yllapito := (select id from tehtavaryhma where yksiloiva_tunniste = '82ecc58a-f96c-46f0-9c70-d29bb6cd4266');
        tehtava_palkkio := (select id from tehtavaryhma where yksiloiva_tunniste = '0ef0b97e-1390-4d6c-bbc4-b30536be8a68');
        tehtava_korvaus := (select id from tehtavaryhma where yksiloiva_tunniste = '9bfa48c6-a225-4d56-9275-8b08cf6302c4');
        tehtava_mhu_hoidon_johto := (select id from tehtavaryhma where yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54');
        tehtava_erillishankinnat := (select id from tehtavaryhma where yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c');
        tehtava_muut := (select id from tehtavaryhma where yksiloiva_tunniste = '4e3cf237-fdf5-4f58-b2ec-319787127b3e');
        rahavaraus_varalasku := (select id from rahavaraus where nimi = 'Varalaskupaikat');

-- Laskut MHU raporttia varten -  Maksettu 15.10.2019 - Laskutuskausi alkaa 1.10
-- Talvihoito
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 3000.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 300.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
-- Soratiet
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 4000.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 400.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
-- Päällysteet
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 5000.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 500.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
-- Korvausinvestoinnit
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 6000.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 600.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
-- Ylläpito
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 7000.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 700.77, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-16', 1000.00, urakka_id, current_timestamp, kayttaja_id, 'lokakuu/1-hoitovuosi');

-- Kohdistukset - 15.10.2019 - Laskutuskausi alkaa 1.10
-- Talvihoito
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 3000.77 AND erapaiva = '2019-10-16'), 1, tinst_talvihoito,
 tehtava_talvihoito, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 3000.77, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 300.77 AND erapaiva = '2019-10-16'), 1, tinst_talvihoito,
 tehtava_talvihoito, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 300.77, current_timestamp, kayttaja_id);
-- Soratiet Oulu MHU Soratien hoito TP
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 4000.77 AND erapaiva = '2019-10-16'), 1, tinst_soratie,
 tehtava_soratie, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 4000.77, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 400.77 AND erapaiva = '2019-10-16'), 1, tinst_soratie,
 tehtava_soratie, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 400.77, current_timestamp, kayttaja_id);
-- Päällyste Oulu MHU Soratien hoito TP
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 5000.77 AND erapaiva = '2019-10-16'), 1, tinst_paallystys,
 tehtava_paikkaus, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 5000.77, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 500.77 AND erapaiva = '2019-10-16'), 1, tinst_paallystys,
 tehtava_paikkaus, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', 500.77, current_timestamp, kayttaja_id);
-- Korvausinvestoinnit Oulu MHU MHU Korvausinvestointi TP
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 6000.77 AND erapaiva = '2019-10-16'), 1, tinst_korvaus,
 tehtava_korvaus, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 6000.77, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 600.77 AND erapaiva = '2019-10-16'), 1, tinst_korvaus,
 tehtava_korvaus, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 600.77, current_timestamp, kayttaja_id);
-- Ylläpito -  Oulu MHU MHU Ylläpito TP
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 7000.77 AND erapaiva = '2019-10-16'), 1, tinst_yllapito,
 tehtava_yllapito, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 7000.77, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 700.77 AND erapaiva = '2019-10-16'), 1, tinst_yllapito,
 tehtava_yllapito, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 700.77, current_timestamp,kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, rahavaraus_id, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 1000.00 AND erapaiva = '2019-10-16'), 1, tinst_yllapito,
tehtava_muut, rahavaraus_varalasku, 'kokonaishintainen'::MAKSUERATYYPPI, 'rahavaraus', 1000.00, current_timestamp, kayttaja_id);


-- Laskut 20.03.2020
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 3000.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 300.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
-- Soratiet
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 4000.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 400.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
-- Päällystykset
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 5000.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 500.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
-- Korvausinvestoinnit
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 6000.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 600.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
-- Ylläpito
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 7000.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-03-20', 700.20, urakka_id, current_timestamp, kayttaja_id, 'maaliskuu/1-hoitovuosi');

-- Kohdistukset 1.3.2020 - 31.3.2020
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 3000.20 AND erapaiva = '2020-03-20'),
 1, tinst_talvihoito, tehtava_talvihoito, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 3000.20,  current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 300.20 AND erapaiva = '2020-03-20'),
 1, tinst_talvihoito, tehtava_talvihoito, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 300.20, current_timestamp, kayttaja_id);
-- Soratiet
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 4000.20 AND erapaiva = '2020-03-20'),
 1, tinst_soratie, tehtava_soratie, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 4000.20, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 400.20 AND erapaiva = '2020-03-20'),
 1, tinst_soratie, tehtava_soratie, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 400.20, current_timestamp, kayttaja_id);
-- Soratiet
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 5000.20 AND erapaiva = '2020-03-20'),
 1, tinst_paallystys, tehtava_paikkaus, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 5000.20, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 500.20 AND erapaiva = '2020-03-20'),
 1, tinst_paallystys, tehtava_paikkaus, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 500.20, current_timestamp, kayttaja_id);
-- Korvausinvestoinnit
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 6000.20 AND erapaiva = '2020-03-20'),
 1, tinst_korvaus, tehtava_korvaus, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 6000.20, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 600.20 AND erapaiva = '2020-03-20'),
 1, tinst_korvaus, tehtava_korvaus, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false, 600.20, current_timestamp, kayttaja_id);
-- Ylläpito
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 7000.20 AND erapaiva = '2020-03-20'),
 1, tinst_yllapito, tehtava_yllapito, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 7000.20, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, tavoitehintainen, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 700.20 AND erapaiva = '2020-03-20'),
 1, tinst_yllapito, tehtava_yllapito, 'lisatyo'::MAKSUERATYYPPI, 'lisatyo', false,700.20, current_timestamp, kayttaja_id);

-- Poikkeuskulut MHU ja Hoidon johdon hallinnolle - 04/2020
-- Normaalisti näitä ei pitäisi lisätä, mutta koska se on käyttöliittymästä mahdollista, niin tehdään testiaineisto
INSERT into kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-04-20', 10.20, urakka_id, current_timestamp, kayttaja_id, 'huhtikuu/1-hoitovuosi');
INSERT into kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-04-21', 10.20, urakka_id, current_timestamp, kayttaja_id, 'huhtikuu/1-hoitovuosi');
INSERT into kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
  VALUES ('2020-04-22', 10.20, urakka_id, current_timestamp, kayttaja_id, 'huhtikuu/1-hoitovuosi');
-- Kohdistukset
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa,  luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 10.20 AND erapaiva = '2020-04-20'),
 1, tinst_mhu_hoidon_johto, tehtava_palkkio, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 10.20,  current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 10.20 AND erapaiva = '2020-04-21'),
 1, tinst_mhu_hoidon_johto, tehtava_mhu_hoidon_johto, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 10.20, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 10.20 AND erapaiva = '2020-04-22'),
 1, tinst_mhu_hoidon_johto, tehtava_erillishankinnat, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 10.20, current_timestamp, kayttaja_id);

-- Lupausten kustannukset

-- Lisää budjetoituja kustannuksia eri toimenpiteille hoitovuosi 4 (2023-2024)
INSERT INTO kustannusarvioitu_tyo (sopimus, toimenpideinstanssi, tehtavaryhma, summa, summa_indeksikorjattu, vuosi, kuukausi, tyyppi, luoja, luotu) 
VALUES 
-- Talvihoitokustannuksia (käytetään A - Talvihoito tehtäväryhmää)
((SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') LIMIT 1), -- Iin urakka
 (SELECT tpi.id FROM toimenpideinstanssi tpi JOIN toimenpide tp ON tpi.toimenpide = tp.id WHERE tpi.urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND tp.koodi = '23104' LIMIT 1),
 (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac'),
 15000, 15450, 2023, 10, 'kokonaishintainen', 1, NOW()),

-- Liikenneympäristön hoitokustannuksia (käytetään B - Liikenneympäristön hoito tehtäväryhmää)
((SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') LIMIT 1),
 (SELECT tpi.id FROM toimenpideinstanssi tpi JOIN toimenpide tp ON tpi.toimenpide = tp.id WHERE tpi.urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND tp.koodi = '23116' LIMIT 1),
 (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '1855032a-2bb3-46d4-b9b4-c6d4e4c25d05'), 
 8000, 8240, 2023, 11, 'kokonaishintainen', 1, NOW()),

-- Sorateiden hoitokustannuksia (käytetään C - Sorateiden hoito tehtäväryhmää)
((SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') LIMIT 1),
 (SELECT tpi.id FROM toimenpideinstanssi tpi JOIN toimenpide tp ON tpi.toimenpide = tp.id WHERE tpi.urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND tp.koodi = '23124' LIMIT 1),
 (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1'),
 5000, 5150, 2023, 12, 'kokonaishintainen', 1, NOW());

-- Lisää toteutuneita kuluja
INSERT INTO kulu (urakka, erapaiva, kokonaissumma, luoja, luotu, koontilaskun_kuukausi)
VALUES 
((SELECT id FROM urakka WHERE sampoid = '1242141-II3'), '2023-10-15', 66000, 1, NOW(), 'lokakuu/4-hoitovuosi'),
((SELECT id FROM urakka WHERE sampoid = '1242141-II3'), '2023-11-20', 35000, 1, NOW(), 'lokakuu/4-hoitovuosi'),
((SELECT id FROM urakka WHERE sampoid = '1242141-II3'), '2023-12-10', 21000, 1, NOW(), 'lokakuu/4-hoitovuosi');

-- Kohdista kulut toimenpiteisiin (käytetään tehtäväryhmiä)
INSERT INTO kulu_kohdistus (kulu, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, luoja, luotu, tavoitehintainen)
VALUES 
-- Talvihoito
((SELECT id FROM kulu WHERE urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND kokonaissumma = 66000 LIMIT 1),
 1, 
 66000,
 (SELECT tpi.id FROM toimenpideinstanssi tpi JOIN toimenpide tp ON tpi.toimenpide = tp.id WHERE tpi.urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND tp.koodi = '23104' LIMIT 1),
 (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac'),
 'kokonaishintainen'::MAKSUERATYYPPI,
 'hankintakulu', 1, NOW(), TRUE),

-- Liikenneympäristön hoito  
((SELECT id FROM kulu WHERE urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND kokonaissumma = 35000 LIMIT 1),
 1,  
 35000,
 (SELECT tpi.id FROM toimenpideinstanssi tpi JOIN toimenpide tp ON tpi.toimenpide = tp.id WHERE tpi.urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND tp.koodi = '23116' LIMIT 1),
 (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '1855032a-2bb3-46d4-b9b4-c6d4e4c25d05'),
 'kokonaishintainen'::MAKSUERATYYPPI,
 'hankintakulu', 1, NOW(), TRUE),

-- Sorateiden hoito
((SELECT id FROM kulu WHERE urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND kokonaissumma = 21000 LIMIT 1),
 1,  
 21000,
 (SELECT tpi.id FROM toimenpideinstanssi tpi JOIN toimenpide tp ON tpi.toimenpide = tp.id WHERE tpi.urakka = (SELECT id FROM urakka WHERE sampoid = '1242141-II3') AND tp.koodi = '23124' LIMIT 1),
 (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1'),
 'kokonaishintainen'::MAKSUERATYYPPI,
 'hankintakulu', 1, NOW(), TRUE);

        END
$$ LANGUAGE plpgsql;
