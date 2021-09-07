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
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tavoitehinta, kattohinta, luotu)
        VALUES (urakkaid, 1, 100000, 110000, NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tavoitehinta, kattohinta, luotu)
        VALUES (urakkaid, 2, 100000, 110000, NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tavoitehinta, kattohinta, luotu)
        VALUES (urakkaid, 3, 100000, 110000, NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tavoitehinta, kattohinta, luotu)
        VALUES (urakkaid, 4, 100000, 110000, NOW());
        INSERT INTO urakka_tavoite(urakka, hoitokausi, tavoitehinta, kattohinta, luotu)
        VALUES (urakkaid, 5, 100000, 110000, NOW());
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

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", sisalto, "urakan-alkuvuosi") VALUES

-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 8, '{10}', 6, 0,
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2019),
(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 8, '{10}', 9, 0,
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2019),
(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' AND "urakan-alkuvuosi" = 2019), null, 'kysely', 14, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen
tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta.',
 2019),

-- B. Toiminnan suunnitelmallisuus
(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 10, null, 0, 1,
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
seuraavan kuukauden aikana.',
 2019),
-- C. Laadunvarmistus ja reagointikyky
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' AND "urakan-alkuvuosi" = 2019), null, 'monivalinta', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Toimenpiteitä aiheuttaneiden ilmoitusten (urakoitsijaviestien) %-osuus talvihoitoon ja sorateiden
kunnossapitoon liittyvistä ilmoituksista. (6 sisäistä pistevaihtoehtoa).',
 2019),
(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Meillä (pääurakoitsijalla) on käytössä itselle luovutuksen menettely määräaikaan sidotuista töistä
/ työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta, sorateiden ja siltojen
hoidosta sekä ojituksesta. Alihankkijamme tekevät itselle luovutuksen vastaavista omista
töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista.',
 2019),
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5}', 6, 0,
 'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina > 6 kertaa
 talvessa (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja
 liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai
 viikonloppuisin. Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle
 viimeistään seuraavassa työmaakokouksessa.',
 2019),

-- D. Turvallisuus ja osaamisen kehittäminen
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5, null, 0, 0,
 'Seuraamme urakassa systemaattisesti työturvallisuutta vaarantavia läheltä piti -tilanteita ja
teemme korjaavia toimenpiteitä ko. tilanteiden vähentämiseksi. Raportoimme em. tilanteet sekä
niihin liittyvät suunnitellut ja/tai tehdyt toimenpiteet tilaajalle työmaakokouksien yhteydessä.',
 2019),
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Pidämme vähintään 80 %:lle alihankkijoiden operatiivisesta henkilöstöstä vuosittain
työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.
Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa
työmaakokouksessa',
 2019),
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Järjestämme urakassa koulutuksia, joiden aiheita voivat olla esim. menetelmätieto,
laatutietoisuus, raportointi, seurantalaitteiden käyttö ja työturvallisuus. Järjestämäämme
koulutukseen (1 htp / hoitovuosi) osallistuu vähintään 1 alihankkijan henkilö kultakin
sopimussuhteessa olevalta alihankkijalta. Osallistumisvelvollisuus on kirjattu
alihankintasopimuksiimme.',
 2019),
-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 2, null, 0, 0,
 'Toteutamme tilanne- ja ennakkotiedotusta vähintään 4 kertaa kuukaudessa.',
 2019),
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 12, null, 9, 0,
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt,
koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että
sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin.
Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä.',
 2019),
(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 8, null, 0, 0,
 'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden
tietoisuuteen viikoittain. Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen
perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen,
työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa
sekä huomioimme ne viestinnässä.',
 2019),
(14, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' AND "urakan-alkuvuosi" = 2019), null, 'yksittainen', 3, null, 9, 0,
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