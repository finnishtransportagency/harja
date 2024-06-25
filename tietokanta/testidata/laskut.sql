INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-15', 666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-10-15', 6666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'lokakuu/1-hoitovuosi');
INSERT INTO kulu (erapaiva, kokonaissumma, urakka, luotu, luoja, koontilaskun_kuukausi)
VALUES ('2019-09-15', 3666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'lokakuu/1-hoitovuosi');

INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where nimi = 'Vesakonraivaukset ja puun poisto (V)'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 333.33, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 666.66), 2, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 222.22, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 666.66), 3, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 111.11, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 6666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 2222.22, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 6666.66), 2, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),
 (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)'), 'akillinen-hoitotyo'::MAKSUERATYYPPI, 'rahavaraus', 4444.44, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa,  luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 3666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'),
 (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 3666.66, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO liite (nimi, tyyppi, lahde, urakka, luotu, luoja) VALUES ('pensas-2019080019.jpg', 'image/png', 'harja-ui'::lahde, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO kulu_liite (kulu, liite, luotu, luoja) VALUES ((select id from kulu where kokonaissumma = 666.66), (select id from liite where nimi = 'pensas-2019080019.jpg'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Käytetään nimetöntä koodi blokkia, jotta voidaan määritellä muuttujia
DO
$$
    DECLARE
        kayttaja_id              INTEGER;
        urakka_id                INTEGER;
        tinst_talvihoito         INTEGER;
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

    BEGIN
        kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
        urakka_id := (select id from urakka where nimi = 'Oulun MHU 2019-2024');
        tinst_talvihoito := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP');
        tinst_soratie := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Soratien hoito TP');
        tinst_paallystys := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Päällystepaikkaukset TP');
        tinst_korvaus := (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Korvausinvestointi TP');
        tinst_yllapito := (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Ylläpito TP');
        tinst_mhu_hoidon_johto := (select id from toimenpideinstanssi where nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP');
        tehtava_talvihoito := (select id from tehtavaryhma where nimi = 'Talvihoito (A)');
        tehtava_soratie := (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)');
        tehtava_paikkaus := (select id from tehtavaryhma where nimi = 'Kuumapäällyste (Y1)');
        tehtava_yllapito := (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)');
        tehtava_palkkio := (select id from tehtavaryhma where nimi = 'Hoidonjohtopalkkio (G)');
        tehtava_korvaus := (select id from tehtavaryhma where nimi = 'RKR-korjaus (Q)');
        tehtava_mhu_hoidon_johto := (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)');
        tehtava_erillishankinnat := (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)');

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


-- Kohdistukset - 15.10.2019 - Laskutuskausi alkaa 1.10
-- Talvihoito
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 3000.77 AND erapaiva = '2019-10-16'), 1, tinst_talvihoito,
 tehtava_talvihoito, 'kokonaishintainen'::MAKSUERATYYPPI, 'hankintakulu', 3000.77, current_timestamp, kayttaja_id);
INSERT INTO kulu_kohdistus (kulu, rivi, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, summa, luotu, luoja) VALUES
((select id from kulu where kokonaissumma = 300.77 AND erapaiva = '2019-10-16'), 1, tinst_talvihoito,
 tehtava_talvihoito, 'lisatyo'::MAKSUERATYYPPI, 'hankintakulu', 300.77, current_timestamp, kayttaja_id);
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

        END
$$ LANGUAGE plpgsql;
