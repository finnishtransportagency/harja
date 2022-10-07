-- Lupausten pohjadata hoitokaudelle 2022-2023
INSERT INTO lupausryhma(otsikko, jarjestys, "urakan-alkuvuosi", luotu)
VALUES
    ('Kannustavat alihankintasopimukset', 1, 2022, NOW()),
    ('Toiminnan suunnitelmallisuus', 2, 2022, NOW()),
    ('Laadunvarmistus ja reagointikyky', 3, 2022, NOW()),
    ('Turvallisuus ja osaamisen kehittäminen', 4, 2022, NOW()),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2022, NOW());

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", kuvaus, sisalto, "urakan-alkuvuosi") VALUES
-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 8, '{10}', 6, 0,
    'Talvihoidon kannustinjärjestelmä',
    'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
   käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
   kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
   järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
   € tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
   ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
    2022),
(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 8, '{10}', 9, 0,
    'Kesähoidon kannustinjärjestelmä',
    'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on
   käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
   kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
   järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
   € tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
   ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
    2022),
(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2022), null, 'kysely', 14, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
    'Kyselytutkimus alihankkijoille',
    'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen
   tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta.',
    2022),

-- B. Toiminnan suunnitelmallisuus
(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 10, null, 0, 1,
    'Kuukausittainen töiden suunnittelu',
    'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
   kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
   Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
   läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
   seuraavan kuukauden aikana.',
    2022),
-- C. Laadunvarmistus ja reagointikyky
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2022), null, 'monivalinta', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
    'Kunnossapitoilmoitukset',
    'Toimenpiteitä aiheuttaneiden ilmoitusten (urakoitsijaviestien) %-osuus talvihoitoon ja sorateiden
   kunnossapitoon liittyvistä ilmoituksista. (6 sisäistä pistevaihtoehtoa).',
    2022),
(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
    'Luovutuksen menettely',
    'Meillä (pääurakoitsijalla) on käytössä itselle luovutuksen menettely määräaikaan sidotuista töistä
   / työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta, sorateiden ja siltojen
   hoidosta sekä ojituksesta. Alihankkijamme tekevät itselle luovutuksen vastaavista omista
   töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista.',
    2022),
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5}', 6, 0,
    'Talvihoidon pistokokeet',
    'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina > 6 kertaa
    talvessa (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja
    liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai
    viikonloppuisin. Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle
    viimeistään seuraavassa työmaakokouksessa.',
    2022),
-- D. Turvallisuus ja osaamisen kehittäminen
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 5, null, 0, 0,
    'Työturvallisuuden raportointi',
    'Seuraamme urakassa systemaattisesti työturvallisuutta vaarantavia läheltä piti -tilanteita ja
   teemme korjaavia toimenpiteitä ko. tilanteiden vähentämiseksi. Raportoimme em. tilanteet sekä
   niihin liittyvät suunnitellut ja/tai tehdyt toimenpiteet tilaajalle työmaakokouksien yhteydessä.',
    2022),
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 5,
    '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
    'Turvallisuuden teemakokoukset',
    'Pidämme vähintään 80 %:lle alihankkijoiden operatiivisesta henkilöstöstä vuosittain
   työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.
   Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa
   työmaakokouksessa',
    2022),
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 5,
    '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
    'Koulutukset',
    'Järjestämme urakassa koulutuksia, joiden aiheita voivat olla esim. menetelmätieto,
   laatutietoisuus, raportointi, seurantalaitteiden käyttö ja työturvallisuus. Järjestämäämme
   koulutukseen (1 htp / hoitovuosi) osallistuu vähintään 1 alihankkijan henkilö kultakin
   sopimussuhteessa olevalta alihankkijalta. Osallistumisvelvollisuus on kirjattu
   alihankintasopimuksiimme.',
    2022),
-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 2, null, 0, 0,
    'Tilanne- ja ennakkotiedotus',
    'Toteutamme tilanne- ja ennakkotiedotusta vähintään 4 kertaa kuukaudessa.',
    2022),
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 12, null, 9, 0,
    'Viestintä sidosryhmien kanssa',
    'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt,
   koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä
   vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että
   sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin.
   Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä
   vuoropuhelusta ja viestinnästä.',
    2022),
(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 8, null, 0, 0,
    'Palautteet ja kehittäminen',
    'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden
   tietoisuuteen viikoittain. Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen
   perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen,
   työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa
   sekä huomioimme ne viestinnässä.',
    2022),
(14, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2022), null, 'yksittainen', 3, null, 9, 0,
    'Tyytyväisyystutkimustulokset',
    'Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin
   kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden
   tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme
   analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
    2022);

-- kyselytutkimus alihankkijoille lupaukset 3 osalta toimitetaan luultavasti muualla kuin Harjassa.
-- Harjassa valitaan vain monivalintana saatu tulos
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2022, '<= 4,1 ', 0);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2022, '> 4,1', 2);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2022, '> 4,4', 4);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2022, '> 4,7', 6);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2022, '> 5,0', 10);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2022, '> 5.3', 14);

SELECT * FROM luo_lupauksen_vaihtoehto(5, 2022, '> 25 % / hoitovuosi', 0);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2022, '10-25 % / hoitovuosi', 2);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2022, '15-20 % / hoitovuosi', 4);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2022, '10-15 % / hoitovuosi', 6);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2022, '5-10 % / hoitovuosi', 8);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2022, '0-5 % / hoitovuosi', 10);

