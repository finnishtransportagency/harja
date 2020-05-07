INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-15', 666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), 'laskutettava', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-15', 6666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), 'laskutettava', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-09-15', 3666.66, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), 'kiinteasti-hinnoiteltu', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),(select id from tehtavaryhma where nimi = 'Vesakonraivaukset ja puun poisto (V)'), null, 'kokonaishintainen'::MAKSUERATYYPPI, 333.33, '2019-11-15', '2019-11-18', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 666.66), 2, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),(select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'), null, 'kokonaishintainen'::MAKSUERATYYPPI, 222.22, '2019-11-22', '2019-11-25', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 666.66), 3, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'),(select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), null, 'kokonaishintainen'::MAKSUERATYYPPI, 111.11, '2019-11-28', '2019-11-30', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 6666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'), (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), (select id from toimenpidekoodi where nimi = 'Rumpujen tarkastus' and tehtavaryhma is not null), 'kokonaishintainen'::MAKSUERATYYPPI, 2222.22, '2019-08-01', '2019-08-31', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 6666.66), 2, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'), (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)'), (select id from toimenpidekoodi where nimi = 'Äkillinen hoitotyö (l.ymp.hoito)' and tehtavaryhma is not null and emo = 612), 'akillinen-hoitotyo'::MAKSUERATYYPPI, 4444.44, '2019-12-01T06:15:00.000000', '2019-12-01T09:45:00.000000', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 3666.66), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'), (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 3666.66, '2020-07-01', '2020-07-31', current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

INSERT INTO liite (nimi, tyyppi, lahde, urakka, luotu, luoja) VALUES ('pensas-2019080019.jpg', 'image/png', 'harja-ui'::lahde, (select id from urakka where nimi = 'Oulun MHU 2019-2024'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));
INSERT INTO lasku_liite (lasku, liite, luotu, luoja) VALUES ((select id from lasku where kokonaissumma = 666.66), (select id from liite where nimi = 'pensas-2019080019.jpg'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'));

-- Käytetään nimetöntä koodi blokkia, jotta voidaan määritellä muuttujia
DO
$$
    DECLARE
        kayttaja_id                       INTEGER;
        urakka_id                 INTEGER;
        toimenpideinstanssi_talvihoito_id INTEGER;
    BEGIN
        kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
        urakka_id := (select id from urakka where nimi = 'Oulun MHU 2019-2024');
-- Laskut MHU raporttia varten -  Maksettu 15.10.2019 - Laskutuskausi alkaa 1.10
-- Talvihoito
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 3000.77, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 300.77, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Soratiet
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 4000.77, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 400.77, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Päällysteet
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 5000.77, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 500.77, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Korvausinvestoinnit
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 6000.77, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 600.77, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Ylläpito
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 7000.77, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES ('2019-10-16', 700.77, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);


-- Kohdistukset - 15.10.2019 - Laskutuskausi alkaa 1.10
-- Talvihoito
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 3000.77 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'),
 (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 3000.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 300.77 AND tyyppi = 'laskutettava' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'),
 (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), NULL, 'lisatyo'::MAKSUERATYYPPI, 300.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
-- Soratiet Oulu MHU Soratien hoito TP
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 4000.77 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Soratien hoito TP'),
 (select id from tehtavaryhma where nimi = 'Sorateiden hoito'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 4000.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 400.77 AND tyyppi = 'laskutettava' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Soratien hoito TP'),
 (select id from tehtavaryhma where nimi = 'Sorateiden hoito'), NULL, 'lisatyo'::MAKSUERATYYPPI, 400.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
-- Päällyste Oulu MHU Soratien hoito TP
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 5000.77 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Päällystepaikkaukset TP'),
 (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 5000.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 500.77 AND tyyppi = 'laskutettava' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Päällystepaikkaukset TP'),
 (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'lisatyo'::MAKSUERATYYPPI, 500.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
-- Korvausinvestoinnit Oulu MHU MHU Korvausinvestointi TP
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 6000.77 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Korvausinvestointi TP'),
 (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 6000.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 600.77 AND tyyppi = 'laskutettava' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Korvausinvestointi TP'),
 (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'lisatyo'::MAKSUERATYYPPI, 600.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
-- Ylläpito -  Oulu MHU MHU Ylläpito TP
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 7000.77 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Ylläpito TP'),
 (select id from tehtavaryhma where nimi = 'MHU Ylläpito'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 7000.77, '2019-10-01', '2019-10-31', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 700.77 AND tyyppi = 'laskutettava' AND erapaiva = '2019-10-16'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Ylläpito TP'),
 (select id from tehtavaryhma where nimi = 'MHU Ylläpito'), NULL, 'lisatyo'::MAKSUERATYYPPI, 700.77, '2019-10-01', '2019-10-31', current_timestamp,kayttaja_id);


-- Laskut 20.03.2020
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 3000.20, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 300.20, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Soratiet
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 4000.20, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 400.20, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Päällystykset
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 5000.20, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 500.20, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Korvausinvestoinnit
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 6000.20, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 600.20, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);
-- Ylläpito
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 7000.20, urakka_id, 'kiinteasti-hinnoiteltu', current_timestamp, kayttaja_id);
INSERT INTO lasku (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja) VALUES ('2020-03-20', 700.20, urakka_id, 'laskutettava', current_timestamp, kayttaja_id);

-- Kohdistukset 1.3.2020 - 31.3.2020
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 3000.20 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'), (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 3000.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 300.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'), (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), NULL, 'lisatyo'::MAKSUERATYYPPI, 300.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
-- Soratiet
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 4000.20 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Soratien hoito TP'), (select id from tehtavaryhma where nimi = 'Sorateiden hoito'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 4000.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 400.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Soratien hoito TP'), (select id from tehtavaryhma where nimi = 'Sorateiden hoito'), NULL, 'lisatyo'::MAKSUERATYYPPI, 400.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
-- Soratiet
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 5000.20 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Päällystepaikkaukset TP'), (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 5000.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 500.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Päällystepaikkaukset TP'), (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'lisatyo'::MAKSUERATYYPPI, 500.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
-- Korvausinvestoinnit
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 6000.20 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Korvausinvestointi TP'), (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 6000.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 600.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Korvausinvestointi TP'), (select id from tehtavaryhma where nimi = 'Päällysteiden paikkaus'), NULL, 'lisatyo'::MAKSUERATYYPPI, 600.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
-- Ylläpito
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 7000.20 AND tyyppi = 'kiinteasti-hinnoiteltu' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Ylläpito TP'), (select id from tehtavaryhma where nimi = 'MHU Ylläpito'), NULL, 'kokonaishintainen'::MAKSUERATYYPPI, 7000.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);
INSERT INTO lasku_kohdistus (lasku, rivi, toimenpideinstanssi, tehtavaryhma, tehtava, maksueratyyppi, summa, suoritus_alku, suoritus_loppu, luotu, luoja) VALUES
((select id from lasku where kokonaissumma = 700.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-03-20'), 1, (select id from toimenpideinstanssi where nimi = 'Oulu MHU MHU Ylläpito TP'), (select id from tehtavaryhma where nimi = 'MHU Ylläpito'), NULL, 'lisatyo'::MAKSUERATYYPPI, 700.20, '2020-03-15', '2020-03-20', current_timestamp, kayttaja_id);

        END
$$ LANGUAGE plpgsql;