DO $$
    DECLARE
        urakkaid INTEGER;
        kayttajaid INTEGER;
        alkuvuosi INTEGER := 2021;

    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'Iin MHU 2021-2026');
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'yit_uuvh');

        INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
        VALUES (urakkaid, 76, kayttajaid);

        INSERT INTO lupaus_vastaus ("lupaus-id", "urakka-id", kuukausi, vuosi, vastaus, "lupaus-vaihtoehto-id", luoja)
        VALUES ((SELECT id FROM lupaus WHERE jarjestys = 1 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, null, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 2 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, null, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 3 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, 4, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 3 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 11, alkuvuosi, TRUE, 6, kayttajaid);

        -- Urakan tavoitehinta
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tarjous_tavoitehinta, tavoitehinta, kattohinta, tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu, luotu)
        VALUES (urakkaid, 1, 90000, 100000, 110000, testidata_indeksikorjaa(100000, alkuvuosi, 10, urakkaid), testidata_indeksikorjaa(110000, alkuvuosi, 10, urakkaid), NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tarjous_tavoitehinta, tavoitehinta, kattohinta, tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu, luotu)
        VALUES (urakkaid, 2, 90000, 100000, 110000, testidata_indeksikorjaa(100000, (1 + alkuvuosi), 10, urakkaid), testidata_indeksikorjaa(110000, (1 + alkuvuosi), 10, urakkaid), NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tarjous_tavoitehinta, tavoitehinta, kattohinta, tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu, luotu)
        VALUES (urakkaid, 3, 90000, 100000, 110000, testidata_indeksikorjaa(100000, (2 + alkuvuosi), 10, urakkaid), testidata_indeksikorjaa(110000, (2 + alkuvuosi), 10, urakkaid), NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tarjous_tavoitehinta, tavoitehinta, kattohinta,tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu, luotu)
        VALUES (urakkaid, 4, 90000, 120000, 132000, testidata_indeksikorjaa(120000, (3 + alkuvuosi), 10, urakkaid), testidata_indeksikorjaa(110000, (3 + alkuvuosi), 10, urakkaid), NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tarjous_tavoitehinta, tavoitehinta, kattohinta, tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu, luotu)
        VALUES (urakkaid, 5, 90000, 264000, 290400, testidata_indeksikorjaa(240000, (4 + alkuvuosi), 10, urakkaid), testidata_indeksikorjaa(110000, (4 + alkuvuosi), 10, urakkaid), NOW());
    END
$$ LANGUAGE plpgsql;

-- Lisätään testeihin 2019 alkaville urakoille lupaukset
INSERT INTO lupausryhma(otsikko, jarjestys, "urakan-alkuvuosi", luotu)
VALUES
    ('Kannustavat alihankintasopimukset', 1, 2019, NOW()),
    ('Toiminnan suunnitelmallisuus', 2, 2019, NOW()),
    ('Laadunvarmistus ja reagointikyky', 3, 2019, NOW()),
    ('Turvallisuus ja osaamisen kehittäminen', 4, 2019, NOW()),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2019, NOW());

-- Lupausryhmien linkitys urakkaan 2024 alkaville urakoille linkkitaulun kautta:
-- MHU Suomussalmi, Ivalon MHU testiurakka (uusi), Rovaniemen MHU testiurakka (1. hoitovuosi)
-- Tehään Lupauksien kannalta Ivalon urakka Espoon ja Vantaan kaltaiseksi vaativaksi urakaksi.

-- Linkitetään Ivalo
DO $$
    DECLARE
        tarkistus_lapaisty BOOLEAN;
        urakka_id_ivalo INTEGER;

    BEGIN
        urakka_id_ivalo = (SELECT id FROM urakka WHERE nimi ILIKE '%Ivalon MHU testiurakka%' AND  EXTRACT(YEAR FROM urakka.alkupvm) = 2024);

        -- Tarkista löytyykö ympäristöstä
        IF urakka_id_ivalo IS NULL THEN
            RAISE NOTICE 'Ivalon urakkaa ei löytynyt lupauksia varten. Tämä on ei ole OK!!.';
            tarkistus_lapaisty := FALSE;
        ELSE
            RAISE NOTICE 'Ivalon urakka linkitetty lupauksiin!';
            tarkistus_lapaisty := TRUE;
        END IF;

        IF tarkistus_lapaisty THEN
            INSERT INTO lupausryhma_urakka(lupausryhma_id, urakka_id) VALUES
-- Ivalo
((SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'),
 urakka_id_ivalo),
-- Ivalo
((SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'),
 urakka_id_ivalo),
-- Ivalo
((SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'),
 urakka_id_ivalo),
-- Ivalo
((SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'),
 urakka_id_ivalo),
-- Ivalo
((SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'),
 urakka_id_ivalo);
        END IF;
    END $$;

-- Linkitetään kaikki muut paitsi ivalo
INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
         JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm)
WHERE lupausryhma."urakan-alkuvuosi" = 2024
  AND lupausryhma."rivin-tunnistin-selite" = 'Yleinen'
  AND urakka.nimi NOT LIKE '%Ivalon MHU testiurakka%';

INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
         JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm)
WHERE lupausryhma."urakan-alkuvuosi" BETWEEN 2020 AND 2023;

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", kuvaus, sisalto, "urakan-alkuvuosi") VALUES

-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 8, '{10}', '{6}', 0,
 'Talvihoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2019),
(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 8, '{10}', '{9}', 0,
 'Kesähoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2019),
(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset'  AND "urakan-alkuvuosi" = 2019), null, 'kysely', 14, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', '{9}', 0,
 'Kyselytutkimus alihankkijoille',
 'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen
tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta.',
 2019),

-- B. Toiminnan suunnitelmallisuus
(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 10, null, '{0}', 1,
 'Kuukausittainen töiden suunnittelu',
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
seuraavan kuukauden aikana.',
 2019),
-- C. Laadunvarmistus ja reagointikyky
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky'  AND "urakan-alkuvuosi" = 2019), null, 'monivalinta', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', '{9}', 0,
 'Kunnossapitoilmoitukset',
 'Toimenpiteitä aiheuttaneiden ilmoitusten (urakoitsijaviestien) %-osuus talvihoitoon ja sorateiden
kunnossapitoon liittyvistä ilmoituksista. (6 sisäistä pistevaihtoehtoa).',
 2019),
(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', '{9}', 0,
 'Luovutuksen menettely',
 'Meillä (pääurakoitsijalla) on käytössä itselle luovutuksen menettely määräaikaan sidotuista töistä
/ työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta, sorateiden ja siltojen
hoidosta sekä ojituksesta. Alihankkijamme tekevät itselle luovutuksen vastaavista omista
töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista.',
 2019),
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5}', '{6}', 0,
 'Talvihoidon pistokokeet',
 'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina ≥ 6 kertaa
 talvessa (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja
 liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai
 viikonloppuisin. Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle
 viimeistään seuraavassa työmaakokouksessa.',
 2019),

-- D. Turvallisuus ja osaamisen kehittäminen
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5, null, '{0}', 0,
 'Työturvallisuuden raportointi',
 'Seuraamme urakassa systemaattisesti työturvallisuutta vaarantavia läheltä piti -tilanteita ja
teemme korjaavia toimenpiteitä ko. tilanteiden vähentämiseksi. Raportoimme em. tilanteet sekä
niihin liittyvät suunnitellut ja/tai tehdyt toimenpiteet tilaajalle työmaakokouksien yhteydessä.',
 2019),
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', '{9}', 0,
 'Turvallisuuden teemakokoukset',
 'Pidämme vähintään 80 %:lle alihankkijoiden operatiivisesta henkilöstöstä vuosittain
työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.
Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa
työmaakokouksessa',
 2019),
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', '{9}', 0,
 'Koulutukset',
 'Järjestämme urakassa koulutuksia, joiden aiheita voivat olla esim. menetelmätieto,
laatutietoisuus, raportointi, seurantalaitteiden käyttö ja työturvallisuus. Järjestämäämme
koulutukseen (1 htp / hoitovuosi) osallistuu vähintään 1 alihankkijan henkilö kultakin
sopimussuhteessa olevalta alihankkijalta. Osallistumisvelvollisuus on kirjattu
alihankintasopimuksiimme.',
 2019),
-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 2, null, '{0}', 0,
 'Tilanne- ja ennakkotiedotus',
 'Toteutamme tilanne- ja ennakkotiedotusta vähintään 4 kertaa kuukaudessa.',
 2019),
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 12, null, '{9}', 0,
 'Viestintä sidosryhmien kanssa',
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt,
koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että
sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin.
Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä.',
 2019),
(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 8, null, '{0}', 0,
 'Palautteet ja kehittäminen',
 'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden
tietoisuuteen viikoittain. Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen
perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen,
työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa
sekä huomioimme ne viestinnässä.',
 2019),
(14, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'  AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 3, null, '{9}', 0,
 'Tyytyväisyystutkimustulokset',
 'Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin
kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden
tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme
analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
 2019);

SELECT * FROM luo_lupauksen_vaihtoehto(3, 2019, '<= 4,1 ', 0);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2019, '> 4,1', 2);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2019, '> 4,4', 4);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2019, '> 4,7', 6);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2019, '> 5,0', 10);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2019, '> 5.3', 14);

SELECT * FROM luo_lupauksen_vaihtoehto(5, 2019, '> 25 % / hoitovuosi', 0);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2019, '10-25 % / hoitovuosi', 2);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2019, '15-20 % / hoitovuosi', 4);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2019, '10-15 % / hoitovuosi', 6);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2019, '5-10 % / hoitovuosi', 8);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2019, '0-5 % / hoitovuosi', 10);


-- Esimerkki dataa kyselyistä joissa askeleita on useita
-- Insertoidaana ensin askeleiden otsikot
INSERT INTO lupaus_vaihtoehto_ryhma("ryhma-otsikko")
VALUES
    ('Testiotsikko 1'),
    ('Testiotsikko 2');

DO $$
    DECLARE
        ryhma_otsikko_id_1 INTEGER;
        ryhma_otsikko_id_2 INTEGER;
    BEGIN
        ryhma_otsikko_id_1 = (SELECT id FROM lupaus_vaihtoehto_ryhma where "ryhma-otsikko" = 'Testiotsikko 1'); 
        ryhma_otsikko_id_2 = (SELECT id FROM lupaus_vaihtoehto_ryhma where "ryhma-otsikko" = 'Testiotsikko 2');
       
        -- Askel 1. josta päätyy 2 valinnasta askeleeseen 2 ja 3 valinnasta Askeleeseen 3
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '0%', 0, null,null, 1, null, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '0 % ja ≤ 25 %', 0, null,null, 1, 2, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 25 %', 0, null,null, 1, 3, ryhma_otsikko_id_1);

        -- Askel 2.
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '<= 4,1', 0, null,null, 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 4,1', 2, null,null, 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 4,4', 2, null,null, 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 4,7', 2, null,null, 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 5,0', 2, null,null, 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 5,3', 2, null,null, 2, null, ryhma_otsikko_id_2);

        -- Askel 3.
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '<= 4,1', 0, null,null, 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 4,1', 3, null,null, 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 4,4', 5, null,null, 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 4,7', 7, null,null, 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 5,0', 11, null,null, 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2021, '> 5,3', 15, null,null, 3, null, ryhma_otsikko_id_2);
    END
$$ LANGUAGE plpgsql;

--- Linkitetään 2025 urakat lupausryhmiin
INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
         JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm)
WHERE lupausryhma."urakan-alkuvuosi" = 2025
  AND lupausryhma."rivin-tunnistin-selite" = 'Yleinen';

-- Kustannusennusten testausta varten
 INSERT INTO lupaus (
  jarjestys,
  "lupausryhma-id",
  "urakka-id",
  lupaustyyppi,
  pisteet,
  "kirjaus-kkt",
  "paatos-kk",
  "joustovara-kkta",
  kuvaus,
  sisalto,
  "urakan-alkuvuosi"
) VALUES (
  99, -- testijärjestys, muuta tarvittaessa
  (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' AND "urakan-alkuvuosi" = 2021),
  null,
  'kustannusennuste',
  8,
  '{1,4,6,8}',
  '{6}',
  0,
  'Hoitovuoden lopun tavoitehinnan ja toteutuvien kustannuksien ennustaminen',
  'Ennustamme urakan hoitovuoden lopun tavoitehintaa ja toteutuvia kustannuksia 4 kertaa vuodessa alla mainittuihin määräpäiviin mennessä.',
  2019
);

-- Ensimmäinen hoitovuosi: erikoiskuukaudet
INSERT INTO lupaus_hoitovuoden_kirjauskuukaudet ("lupaus-id", "hoitovuosi-nro", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", luoja)
VALUES
  ((SELECT id FROM lupaus
   WHERE jarjestys = 99
  AND "urakan-alkuvuosi" = 2019
  AND kuvaus = 'Hoitovuoden lopun tavoitehinnan ja toteutuvien kustannuksien ennustaminen'),
  1,
  '{10,1,4,6}',
   6,
  0,
  1);


INSERT INTO lupaus_kustannusennuste_kuukausi_pisteet ("urakan-alkuvuosi", kuukausi, paiva, kuvaus, pisterajat) VALUES
-- 2021 urakat
(2021, 10, 15, 'Lokakuu 15. päivä (2021 urakat)', '[
    {"operaattori": "≤", "raja": 7.0, "pisteet": 8, "kuvaus": "≤ 7,0%"},
    {"operaattori": "≤", "raja": 9.0, "pisteet": 4, "kuvaus": "≤ 9,0%"},
    {"operaattori": ">", "raja": 9.0, "pisteet": 1, "kuvaus": "> 9,0%"}
]'),

(2021, 1, 15, 'Tammikuu 15. päivä (2021 urakat)', '[
    {"operaattori": "≤", "raja": 4.0, "pisteet": 8, "kuvaus": "≤ 4,0%"},
    {"operaattori": "≤", "raja": 6.0, "pisteet": 4, "kuvaus": "≤ 6,0%"},
    {"operaattori": ">", "raja": 6.0, "pisteet": 1, "kuvaus": "> 6,0%"}
]'),

(2021, 4, 30, 'Huhtikuu 30. päivä (2021 urakat)', '[
    {"operaattori": "≤", "raja": 2.0, "pisteet": 8, "kuvaus": "≤ 2,0%"},
    {"operaattori": "≤", "raja": 3.0, "pisteet": 4, "kuvaus": "≤ 3,0%"},
    {"operaattori": ">", "raja": 3.0, "pisteet": 1, "kuvaus": "> 3,0%"}
]'),

(2021, 6, 30, 'Kesäkuu 30. päivä (2021 urakat)', '[
    {"operaattori": "≤", "raja": 1.0, "pisteet": 8, "kuvaus": "≤ 1,0%"},
    {"operaattori": "≤", "raja": 2.0, "pisteet": 4, "kuvaus": "≤ 2,0%"},
    {"operaattori": ">", "raja": 2.0, "pisteet": 1, "kuvaus": "> 2,0%"}
]'),

(2021, 8, 15, 'Elokuu 15. päivä (2021 urakat)', '[
    {"operaattori": "≤", "raja": 7.0, "pisteet": 8, "kuvaus": "≤ 7,0%"},
    {"operaattori": "≤", "raja": 9.0, "pisteet": 4, "kuvaus": "≤ 9,0%"},
    {"operaattori": ">", "raja": 9.0, "pisteet": 1, "kuvaus": "> 9,0%"}
]');
