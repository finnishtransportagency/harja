--- TODO: Tarkista vielä Espoon ja Vantaan nimet kun saapuvat Samposta ja tee tarvittavat korjaukset
-- - Kun linkitetään Espoon ja Vantaan urakoiden lupausryhmät molempiin urakoihin
-- - Kun linkitetään muut urakat myös


-- Lupausten pohjadata hoitokaudelle 2024-2025
INSERT INTO lupausryhma(otsikko, jarjestys, "urakan-alkuvuosi", luotu, "rivin-tunnistin-selite")
VALUES
    ('Kannustavat alihankintasopimukset', 1, 2024, NOW(), 'Yleinen'),
    ('Kannustavat alihankintasopimukset', 1, 2024, NOW(), 'Espoo ja Vantaa'),
    ('Toiminnan suunnitelmallisuus', 2, 2024, NOW(), 'Yleinen'),
    ('Toiminnan suunnitelmallisuus', 2, 2024, NOW(), 'Espoo ja Vantaa'),
    ('Laadunvarmistus ja reagointikyky', 3, 2024, NOW(), 'Yleinen'),
    ('Laadunvarmistus ja reagointikyky', 3, 2024, NOW(), 'Espoo ja Vantaa'),
    ('Turvallisuus ja osaamisen kehittäminen', 4, 2024, NOW(), 'Yleinen'),
    ('Turvallisuus ja osaamisen kehittäminen', 4, 2024, NOW(), 'Espoo ja Vantaa'),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2024, NOW(), 'Yleinen'),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2024, NOW(), 'Espoo ja Vantaa');
    
-- Lupausryhmien ja urakoiden linkitykset lupausryhma_urakka taululla

--- Linkitetään Espoon ja Vantaan urakoiden lupausryhmät molempiin urakoihin
--- TODO: Tarkista vielä Espoon ja Vantaan nimet kun saapuvat Samposta ja tee tarvittavat korjaukset

DO $$
DECLARE
    tarkistus_lapaisty BOOLEAN;
    urakka_id_espoo INTEGER;
    urakka_id_vantaa INTEGER;
BEGIN
    urakka_id_espoo = (SELECT id FROM urakka WHERE nimi ILIKE '%Espoo%' AND  EXTRACT(YEAR FROM urakka.alkupvm) = 2024); 
    urakka_id_vantaa = (SELECT id FROM urakka WHERE nimi ILIKE '%Vantaa%' AND  EXTRACT(YEAR FROM urakka.alkupvm) = 2024);
    -- Tarkista löytyykö ympäristöstä
    IF urakka_id_espoo IS NULL OR urakka_id_vantaa IS NULL THEN
        RAISE NOTICE 'Vantaan ja Espoon urakoita ei löytynyt lupauksia varten. Tämä on ok lokaalisti.';
        tarkistus_lapaisty := FALSE;
    ELSE
        RAISE NOTICE 'Vantaan ja Espoon urakat linkitetty lupauksiin!';
        tarkistus_lapaisty := TRUE;
    END IF;

    IF tarkistus_lapaisty THEN
        INSERT INTO lupausryhma_urakka(lupausryhma_id, urakka_id) VALUES
-- Espoo
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_espoo),
-- Vantaa
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_vantaa),
-- Espoo
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_espoo),
-- Vantaa
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_vantaa),
-- Espoo
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_espoo),
-- Vantaa
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_vantaa),
-- Espoo
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_espoo),
-- Vantaa
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_vantaa),
-- Espoo
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_espoo),
-- Vantaa
    ((SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), 
    urakka_id_vantaa); 
    END IF;
END $$;


--- Linkitetään muut urakat myös
INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
    JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm)
WHERE lupausryhma."urakan-alkuvuosi" = 2024
AND lupausryhma."rivin-tunnistin-selite" = 'Yleinen'
AND urakka.nimi NOT LIKE '%Espoo%' -- TODO: Tarkista osuma oikeaan urakkaan
AND urakka.nimi NOT LIKE '%Vantaa%'; -- TODO: Tarkista osuma oikeaan urakkaan

-- Lupaukset

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", kuvaus, sisalto, "urakan-alkuvuosi") VALUES
-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, '{10}', 6, 0,
 'Talvihoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2024),
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 10, '{10}', 6, 0,
 'Talvihoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 10 000 €  ja me vähintään 20 000 € 
tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2024),

(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, '{10}', 9, 0,
 'Kesähoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2024),
(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 6, '{10}', 9, 0,
 'Kesähoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2024),

(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 15, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Kyselytutkimus alihankkijoille',
 'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta. 
 Kyselytutkimusten vastausprosentin keskiarvon ollessa 0 %, saa tästä lupauksesta 0 pistettä. Jos kyselytutkimuksen vastausprosentin keskiarvo jää välille > 0 % ja ≤ 25 %, saa tästä lupauksesta 2 pistettä riippumatta kyselytutkimuksen tuloksesta.',
 2024),

(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'kysely', 15, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Kyselytutkimus alihankkijoille',
 'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta. 
 Kyselytutkimusten vastausprosentin keskiarvon ollessa 0 %, saa tästä lupauksesta 0 pistettä. Jos kyselytutkimuksen vastausprosentin keskiarvo jää välille > 0 % ja ≤ 25 %, saa tästä lupauksesta 2 pistettä riippumatta kyselytutkimuksen tuloksesta.',
 2024),

-- B. Toiminnan suunnitelmallisuus
(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 10, null, 0, 1,
 'Kuukausittainen töiden suunnittelu',
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
seuraavan kuukauden aikana.',
 2024),
 (4, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 6, null, 0, 1,
 'Kuukausittainen töiden suunnittelu',
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
seuraavan kuukauden aikana.',
 2024),

--- Vain Espoo ja Vantaa

 (5, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'monivalinta', 16, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Budjetissa pysyminen',
 '<h4>Lupaustaso 1</h4>
  <p>Urakoitsija suunnitelmallisella toiminnallaan varmistaa rahavarauksen A budjetissa pysymisen +/-10 %.</p>
  <h4><strong>Lupaustaso 2</strong></h4>
  <p>Urakoitsija suunnitelmallisella toiminnallaan varmistaa rahavarauksen A budjetissa pysymisen +/-5 %.</p>',
 2024),
 (6, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'monivalinta', 12, '{10, 11, 12, 1, 2, 3, 4}', 5, 0,
 'Talvihoidon mitoitus- ja reittisuunnitelmat',
 '<h4>Lupaustaso 1</h4>
<p>Laadimme&nbsp;<strong>talvihoidon mitoitus-, reitti- ja toiminnanohjaussuunnitelmia eri s&auml;&auml;tilanteisiin&nbsp;</strong>voidaksemme toteuttaa ty&ouml;t aina optimaalisella kalustolla. N&auml;in pyrimme tehokkaasti est&auml;m&auml;&auml;n kunnossapidon toimin mahdollisesti laajatkin liikenneh&auml;iri&ouml;t urakka-alueella. Suunnitelmia on sek&auml; liukkaudentorjuntaan ett&auml; lumenpoistoon.</p>
<ul>
<li>Auraus- ja liukkaudentorjuntareittien mitoitusperiaatteet eri talvihoitoluokilla:
<ul>
<li>Ise/Is 35 km</li>
<li>Ib/Ic 60 km</li>
<li>II 70km(tr50km)</li>
<li>III 85km(tr55km)</li>
<li>Ramppipituudet tulee huomioida reittimitoituksessa</li>
</ul>
</li>
</ul>
<ul>
<li>Laadimme my&ouml;s suunnitelman puomiaukkojen, vilkkaiden pys&auml;kkien ja lumitilojen tyhjennysj&auml;rjestyksest&auml; ja aikataulusta.</li>
</ul>
<p>Toimitamme&nbsp;<strong>viikkoa ennen sopimuskatselmusta&nbsp;</strong>talvihoidon reitti- ja mitoitussuunnitelman (LO 1/2017 perustuva), josta selvi&auml;&auml; suunnitellut reitit, reittien pituudet, suunniteltu kalusto varusteineen sek&auml; mitoitusnopeus talvihoitoluokittain.</p>
<p><strong>P&auml;ivit&auml;mme ja yll&auml;pid&auml;mme&nbsp;</strong>talvihoidon suunnitelmaa vuosittain ja aina tarvittaessa, noudatamme mitoitusperiaatteita kaikkina vuosina.</p>
<h4>Lupaustaso 2</h4>
<p>Seuraamme talvihoidon&nbsp;<strong>toteutuneita toimenpideaikoja&nbsp;</strong>tilaajan kanssa ennalta sovituista reiteistä (2-5 kpl) tai talvihoitotoimenpiteistä ja raportoimme tilaajalle toimenpideaikojen toteutumisen&nbsp;<strong>kuukausittain</strong>. Käymme raportin yhdessä tilaajan kanssa läpi kuukausittain. Laatupuutteita havaitessa käynnistämme viipymättä&nbsp;<strong>korjaavat toimenpiteet</strong>.</p>',
 2024),


-- C. Laadunvarmistus ja reagointikyky
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'monivalinta', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Kunnossapitoilmoitukset',
 'Toimenpiteitä aiheuttaneiden ilmoitusten (urakoitsijaviestien) %-osuus talvihoitoon ja sorateiden
kunnossapitoon liittyvistä ilmoituksista. (6 sisäistä pistevaihtoehtoa).',
 2024),

(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Luovutuksen menettely',
 'Meillä (pääurakoitsijalla) on käytössä itselle luovutuksen menettely määräaikaan sidotuista töistä
/ työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta, sorateiden ja siltojen
hoidosta sekä ojituksesta. Alihankkijamme tekevät itselle luovutuksen vastaavista omista
töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista.',
 2024),

 
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Luovutuksen menettely',
 'Meillä (pääurakoitsijalla) on käytössä itselle luovutuksen menettely määräaikaan sidotuista töistä
/ työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta, sorateiden ja siltojen
hoidosta sekä ojituksesta. Alihankkijamme tekevät itselle luovutuksen vastaavista omista
töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista.',
 2024),


(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5}', 6, 0,
 'Talvihoidon pistokokeet',
 'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina > 6 kertaa talvessa tilaajan kanssa ennalta sovittuna ajankohtana (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai viikonloppuisin. 
 Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle viimeistään seuraavassa työmaakokouksessa.',
 2024),
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5}', 6, 0,
 'Talvihoidon pistokokeet',
 'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina > 6 kertaa talvessa tilaajan kanssa ennalta sovittuna ajankohtana (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai viikonloppuisin. 
 Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle viimeistään seuraavassa työmaakokouksessa.',
 2024),

-- D. Turvallisuus ja osaamisen kehittäminen
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 6,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Turvallisuuden teemakokoukset',
 'Pidämme vähintään 80 %:lle alihankkijoiden operatiivisesta henkilöstöstä vuosittain
työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.
Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa
työmaakokouksessa',
 2024),
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Turvallisuuden teemakokoukset',
 'Pidämme vähintään 80 %:lle alihankkijoiden operatiivisesta henkilöstöstä vuosittain
työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.
Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa
työmaakokouksessa',
 2024),

(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Koulutukset',
 'Järjestämme urakassa koulutuksia, joiden aiheita voivat olla esim. menetelmätieto,
laatutietoisuus, raportointi, seurantalaitteiden käyttö ja työturvallisuus. Järjestämäämme
koulutukseen (1 htp / hoitovuosi) osallistuu vähintään 1 alihankkijan henkilö kultakin
sopimussuhteessa olevalta alihankkijalta. Osallistumisvelvollisuus on kirjattu
alihankintasopimuksiimme.',
 2024),
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Koulutukset',
 'Järjestämme urakassa koulutuksia, joiden aiheita voivat olla esim. menetelmätieto,
laatutietoisuus, raportointi, seurantalaitteiden käyttö ja työturvallisuus. Järjestämäämme
koulutukseen (1 htp / hoitovuosi) osallistuu vähintään 1 alihankkijan henkilö kultakin
sopimussuhteessa olevalta alihankkijalta. Osallistumisvelvollisuus on kirjattu
alihankintasopimuksiimme.',
 2024),

-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen',5, null, 0, 0,
 'Tilanne- ja ennakkotiedotus',
 'Toteutamme tilanne- ja ennakkotiedotusta tiedotusvälineiden tai sosiaalisen median alustojen kautta vähintään kerran viikossa*. Tilanne- ja ennakkotiedotusjulkaisu on kuvallinen ja paikkasidonnainen julkaisu tulevista tai käynnissä olevista urakan töistä. Julkaisuiksi ei lasketa muiden laatimien julkaisujen jakamista.
*Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.',
 2024),
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen',5, null, 0, 0,
 'Tilanne- ja ennakkotiedotus',
 'Toteutamme tilanne- ja ennakkotiedotusta tiedotusvälineiden tai sosiaalisen median alustojen kautta vähintään kerran viikossa*. Tilanne- ja ennakkotiedotusjulkaisu on kuvallinen ja paikkasidonnainen julkaisu tulevista tai käynnissä olevista urakan töistä. Julkaisuiksi ei lasketa muiden laatimien julkaisujen jakamista.
*Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.',
 2024),

(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 12, null, 9, 0,
 'Viestintä sidosryhmien kanssa',
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt,
koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että
sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin.
Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä.',
 2024),
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 6, null, 9, 0,
 'Viestintä sidosryhmien kanssa',
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt,
koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että
sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin.
Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä.',
 2024),

--- Vain Espoo ja Vantaa

(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Espoo ja Vantaa'), null, 'yksittainen', 4, null, 0, 0,
 'Palautteet ja tienkäyttäjätyytyväisyys',
 'Teemme analyyseja Harja-ilmoituksista kuukausittain. Saatamme analyysien tulokset henkilöstön, alihankkijoiden ja tilaajan tietoisuuteen.

Toimitamme alihankkijoille heidän työtään koskevat tienkäyttäjäpalautteet ja urakoitsijaviestit. Analyysien, palautteiden ja omien sekä alihankkijoidemme havaintojen perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen, työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa sekä huomioimme ne viestinnässä.

Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
 2024),

---

(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, null, 0, 0,
 'Palautteet ja kehittäminen',
 'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden
tietoisuuteen viikoittain. Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen
perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen,
työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa
sekä huomioimme ne viestinnässä.

* Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.',
 2024),

(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2024 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 3, null, 9, 0,
 'Tyytyväisyystutkimustulokset',
 'Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin
kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden
tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme
analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
 2024);

-- Lupaus nro. 3 - Vaihtoehdot - kaikki urakat

INSERT INTO lupaus_vaihtoehto_ryhma("ryhma-otsikko")
VALUES
    ('Vastausprosentti'),
    ('Kyselytutkimuksen tulos');

DO $$
    DECLARE
        ryhma_otsikko_id_1 INTEGER;
        ryhma_otsikko_id_2 INTEGER;
    BEGIN
        ryhma_otsikko_id_1 = (SELECT id FROM lupaus_vaihtoehto_ryhma where "ryhma-otsikko" = 'Vastausprosentti'); 
        ryhma_otsikko_id_2 = (SELECT id FROM lupaus_vaihtoehto_ryhma where "ryhma-otsikko" = 'Kyselytutkimuksen tulos');
       
        -- Yleinen
        -- Askel 1. josta päätyy 2 valinnasta askeleeseen 2 ja 3 valinnasta Askeleeseen 3
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '0%', 0,'Kannustavat alihankintasopimukset','Yleinen', 1, null, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '0 % ja ≤ 25 %', 0,'Kannustavat alihankintasopimukset','Yleinen', 1, 2, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 25 %', 0,'Kannustavat alihankintasopimukset','Yleinen', 1, 3, ryhma_otsikko_id_1);

        -- Askel 2.
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '<= 4,1', 0,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,1', 2,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,4', 2,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,7', 2,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,0', 2,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,3', 2,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);

        -- Askel 3.
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '<= 4,1', 0,'Kannustavat alihankintasopimukset','Yleinen', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,1', 3,'Kannustavat alihankintasopimukset','Yleinen', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,4', 5,'Kannustavat alihankintasopimukset','Yleinen', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,7', 7,'Kannustavat alihankintasopimukset','Yleinen', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,0', 11,'Kannustavat alihankintasopimukset','Yleinen', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,3', 15,'Kannustavat alihankintasopimukset','Yleinen', 3, null, ryhma_otsikko_id_2);

         -- Espoo ja Vantaa
        -- Askel 1. josta päätyy 2 valinnasta askeleeseen 2 ja 3 valinnasta Askeleeseen 3
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '0%', 0,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 1, null, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '0 % ja ≤ 25 %', 0,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 1, 2, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 25 %', 0,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 1, 3, ryhma_otsikko_id_1);

        -- Askel 2.
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '<= 4,1', 0,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,1', 2,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,4', 2,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,7', 2,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,0', 2,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,3', 2,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 2, null, ryhma_otsikko_id_2);

        -- Askel 3.
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '<= 4,1', 0,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,1', 3,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,4', 5,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 4,7', 7,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,0', 11,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 3, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2024, '> 5,3', 15,'Kannustavat alihankintasopimukset','Espoo ja Vantaa', 3, null, ryhma_otsikko_id_2);


    END
$$ LANGUAGE plpgsql;

-- Lupaus nro. 5 Kunnossapitoilmoitukset - Vaihtoehdot- kaikki urakat

SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, '> 25 % / hoitovuosi', 0,'Laadunvarmistus ja reagointikyky','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, '10-25 % / hoitovuosi', 2,'Laadunvarmistus ja reagointikyky','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, '15-20 % / hoitovuosi', 4,'Laadunvarmistus ja reagointikyky','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, '10-15 % / hoitovuosi', 6,'Laadunvarmistus ja reagointikyky','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, '5-10 % / hoitovuosi', 8,'Laadunvarmistus ja reagointikyky','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, '0-5 % / hoitovuosi', 10,'Laadunvarmistus ja reagointikyky','Yleinen', null, null, null);

-- Lupaus nro. 5 Budjetissa pysyminen - Vaihtoehdot - Espoon ja Vantaa
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, 'Lupaus ei toteutunut, > +/- 10%', 0,'Toiminnan suunnitelmallisuus','Espoo ja Vantaa', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, 'Lupaustaso 1, < +/- 10%', 12,'Toiminnan suunnitelmallisuus','Espoo ja Vantaa', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2024, 'Lupaustasot 2 < +/- 5 %', 16,'Toiminnan suunnitelmallisuus','Espoo ja Vantaa', null, null, null);

-- Lupaus nro. 6 Talvihoidon mitoitus- ja reittisuunnitelmat - Vaihtoehdot - Espoon ja Vantaa
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2024, 'Lupaus ei toteutunut', 0,'Toiminnan suunnitelmallisuus','Espoo ja Vantaa', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2024, 'Lupaustaso 1', 8,'Toiminnan suunnitelmallisuus','Espoo ja Vantaa', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2024, 'Lupaustasot 1 ja 2', 12,'Toiminnan suunnitelmallisuus','Espoo ja Vantaa', null, null, null);