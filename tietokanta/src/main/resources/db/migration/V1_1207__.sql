-- Lupausten pohjadata hoitokaudelle 2025-2026
INSERT INTO lupausryhma(otsikko, jarjestys, "urakan-alkuvuosi", luotu, "rivin-tunnistin-selite")
VALUES
    ('Kannustavat alihankintasopimukset', 1, 2025, NOW(), 'Yleinen'),
    ('Toiminnan suunnitelmallisuus', 2, 2025, NOW(), 'Yleinen'),
    ('Laadunvarmistus ja laadunosoitus', 3, 2025, NOW(), 'Yleinen'),
    ('Turvallisuus ja ympäristö', 4, 2025, NOW(), 'Yleinen'),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2025, NOW(), 'Yleinen');

-- Lupausryhmien ja urakoiden linkitykset lupausryhma_urakka taululla

--- Linkitetään muut urakat myös
INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
         JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm)
WHERE lupausryhma."urakan-alkuvuosi" = 2025
  AND lupausryhma."rivin-tunnistin-selite" = 'Yleinen';

-- Lupaukset

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", kuvaus, sisalto, "urakan-alkuvuosi") VALUES
-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 26, '{10,6}', 6, 0,
 'Talvihoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään niissä alihankintasopimuksissa, jotka ovat toteuttaneet ko. hoitokauden hoitourakan
 töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. <br><br>' ||
 '<h4>Tilaaja on varannut vuosittain</h4> ' ||
 '<ul> <li>perusurakassa 5 000 € ja me vähintään 15 000 €;</li>' ||
 '<li>vaativassa urakassa 8 000 € ja me vähintään 24 000 €; sekä</li>' ||
 '<li>erittäin vaativassa urakassa 12 000 € ja me vähintään 36 000 €</li> </ul>' ||
 'tämän lupauksen mukaiseen kannustinjärjestelmään. <br><br>' ||
 'Tilaajan ja meidän rahavarauksemme yhdistetään ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.' ,
 2025),

(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 18, '{4,9}', 9, 0,
 'Kesähoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on käytössä vähintään niissä alihankintasopimuksissa, ' ||
 'jotka ovat toteuttaneet ko. hoitokaudella hoitourakan töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa. Lupaus täyttyy myös ' ||
 'kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama järjestelmä on edelleen käytössä.<br><br>

Tilaaja on varannut vuosittain <br><br>
<ul>
<li>perusurakassa 2 000 € ja me vähintään 6 000 €;</li>
<li>vaativassa urakassa 4 000 € ja me vähintään 12 000 €; sekä </li>
<li>erittäin vaativassa urakassa 6 000 € ja me vähintään 18 000 € </li>
</ul> <br>Tämän lupauksen mukaiseen kannustinjärjestelmään. <br><br>

Tilaajan ja meidän rahavarauksemme yhdistetään ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2025),


(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 20, '{9}', 9, 0,
 'Kyselytutkimus alihankkijoille',
 'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen
tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta.

Kyselytutkimusten vastausprosentin keskiarvon ollessa 0 %, saa tästä lupauksesta 0 pistettä. Kyselytutkimuksen vastausprosentin keskiarvon jäädessä välille > 0 % ja ≤ 25 %, saa tästä lupauksesta 2 pistettä riippumatta kyselytutkimuksen tuloksesta.',
 2025),

(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 4, '{10,11,12,1,2,3,4,5,6,7,8,9}', 9, 0,
 'Alihankintasopimusten indeksiehto',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on käytössä vähintään niissä alihankintasopimuksissa, jotka ovat toteuttaneet ko. ' ||
 'hoitokaudella hoitourakan töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa. Lupaus täyttyy myös kannustinjärjestelmän ' ||
 'kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama järjestelmä on edelleen käytössä. <br><br>

Tilaaja on varannut vuosittain
<ul>
<li>perusurakassa 2 000 € ja me vähintään 6 000 €;</li>
<li>vaativassa urakassa 4 000 € ja me vähintään 12 000 €; sekä</li>
<li>erittäin vaativassa urakassa 6 000 € ja me vähintään 18 000 €</li>
</ul>
Tämän lupauksen mukaiseen kannustinjärjestelmään.<br><br>

Tilaajan ja meidän rahavarauksemme yhdistetään ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2025),

-- Lupaus 5
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 18, '{10,11,12,1,2,3,4,5,6,7,8,9}', 9, 0,
 'Maksuehto',
 'Emme rajoita laskutusehtoa työsuorituksia sisältävissä alihankintasopimuksissamme ja maksamme työsuorituksista alihankintasopimuksiin kirjatulla maksuehdolla, joka on:',
 2025),

-- B. Toiminnan suunnitelmallisuus
-- Lupaus 6
(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 14, '{10,11,12,1,2,3,4,5,6,7,8,9}', 9, 0,
 'Hoidon vuosikierron mukainen suunnittelu',
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
seuraavan kuukauden aikana.',
 2025),

-- Lupaus 7
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 8, '{10,1,4,6}', 9, 0,
 'Hoitovuoden lopun tavoitehinnan ja toteutuvien kustannuksien ennustaminen',
 'Ennustamme urakan hoitovuoden lopun tavoitehintaa ja toteutuvia kustannuksia 4 kertaa vuodessa alla mainittuihin määräpäiviin mennessä.<br><br>
<table>
<tr><td colspan=2>31.8.*</td>
<td colspan=2>15.1</td>
<td colspan=2>30.4</td>
<td colspan=2>30.6</td>
</tr>
<tr>
 <td>Ennusteen tarkkuus</td>
<td>Pistettä</td>
<td>Ennusteen tarkkuus</td>
<td>Pistettä</td>
<td>Ennusteen tarkkuus</td>
<td>Pistettä</td>
<td>Ennusteen tarkkuus</td>
<td>Pistettä</td>
</tr>
<tr><td>> 9,0 %</td>
<td>1</td>
<td>> 6,0 %</td>
<td>1</td>
<td>> 3,0 %</td>
<td>1</td>
<td>> 2,0 %</td>
<td>1</td>
</tr>
<tr><td>≤ 9,0 %</td>
<td>4</td>
<td>≤ 6,0 %</td>
<td>4</td>
<td>≤ 3,0 %</td>
<td>4</td>
<td>≤ 2,0 %</td>
<td>4</td>
</tr>
<tr><td>≤ 7,0 %</td>
<td>8</td>
<td>≤4,0 %</td>
<td>8</td>
<td>≤ 2,0 %</td>
<td>8</td>
<td>≤ 1,0 %</td>
<td>8</td>
</tr>
*Tulevan hoitovuoden ennuste. Määräpäivä urakan ensimmäisenä hoitovuotena 15.10.',
 2025),



-- C. Laadunvarmistus ja laadunosoitus
-- Lupaus 8
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja laadunosoitus' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 20, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 9, 0,
 'Luovutuksen menettely',
 'Meillä (pääurakoitsijalla) on käytössä itselleluovutuksen menettely määräaikaan sidotuista töistä / työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta. Alihankkijamme tekevät itselleluovutuksen vastaavista omista töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista. Itselleluovutukset dokumentoidaan tilaajan hankeaineiston hallintajärjestelmään.',
 2025),

-- Lupaus 9
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja laadunosoitus' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 4, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 9, 0,
 'Kohdistetun seurannan parantaminen',
 'Sijoittamme hoidollisesti haastaviin tai muuten tilaajan toiminnan kannalta tarpeellisiin kohteisiin eri puolille urakka-aluetta yhdestä neljään työmaakameraa, tai vastaavaa.<br><br>
Kohteet, joihin kamerat sijoitetaan, sovitaan yhteistyössä tilaajan kanssa ja ne voivat vaihtua urakan ja hoitovuoden aikana.<br><br>
Kameroilla tuotetun materiaalin tulee olla joko videokuvaa tai valokuvia vähintään viiden (5) minuutin välein. Kuvan tulee olla vähintään HD-laatua (1920 × 1080 pikseliä). Videoiden toistonopeutta tulee voida säätää ja valokuvia tulee olla mahdollista katsella nk. timelapse-videona. Tuotettua materiaalia säilytettään yhden (1) kuukauden ajan ja siihen tulee antaa tilaajalle vapaa pääsy. Internet-yhteyden katkeamisen varalle järjestelmässä tulee olla tallennustilaa, jolle katkon aikana syntynyt materiaali tallentuu myöhemmin katsottavaksi. Kamerat tulee pyrkiä sijoittamaan niin, ettei yksittäisen tienkäyttäjän tunnistaminen ole mahdollista. Mikäli tienkäyttäjät ovat tunnistettavissa, on kuva-aineisto anonymisoitava ennen kuin aineisto on tilaajan ja urakoitsijan katseltavissa järjestelmän katselupalvelussa. ',
 2025),



-- D. Turvallisuus ja ympäristö
-- Lupaus 10
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja ympäristö' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 9, 0,
 'Turvallisuuden teemakokoukset',
 'Pidämme alihankkijoiden operatiiviselle henkilöstölle hoitovuosittain työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset. Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa työmaakokouksessa.',
 2025),


-- Lupaus 11
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja ympäristö' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 9, 0,
 'Ajoneuvokohtainen ajotavanseurantajärjestelmä',
 'Alihankkijoiltamme, jotka tekevät hoitokaudella hoitourakan töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa, edellytetään ajoneuvokohtaista ajotavanseurantajärjestelmää ja hyödynnämme järjestelmän antamaa tietoa toiminnan johtamisessa.',
 2025),

-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
-- Lupaus 12
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 18, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 0, 0,
 'Tilanne- ja ennakkotiedotus',
 'Toteutamme tilanne- ja ennakkotiedotusta paikallisten tiedotusvälineiden tai sosiaalisen median alustojen kautta vähintään kerran viikossa*. ' ||
 'Tilanne- ja ennakkotiedotusjulkaisu on kuvallinen ja paikkasidonnainen julkaisu tulevista tai käynnissä olevista urakan töistä. Julkaisuiksi ei lasketa muiden laatimien julkaisujen jakamista.<br><br>

*Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.',
 2025),

-- Lupaus 13
(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen',14, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 9, 0,
 'Viestintä sidosryhmien kanssa',
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt, koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin. Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä vuoropuhelusta ja viestinnästä.',
 2025),

-- Lupaus 14
(14, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', 0, 0,
 'Palautteet ja kehittäminen',
 'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden tietoisuuteen viikoittain*. ' ||
 'Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen, ' ||
 'työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa sekä huomioimme ne viestinnässä.<br><br>

* Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.',
 2025),

-- Lupaus 15
(15, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2025 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, '{ 9}', 9, 0,
 'Tyytyväisyystutkimustulokset',
 'Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
 2025);

-- Lupaus nro. 3  Kyselytutkimus alihankkijoille
DO $$
    DECLARE
        ryhma_otsikko_id_1 INTEGER;
        ryhma_otsikko_id_2 INTEGER;
    BEGIN
        ryhma_otsikko_id_1 = (SELECT id FROM lupaus_vaihtoehto_ryhma where "ryhma-otsikko" = 'Vastausprosentti');
        ryhma_otsikko_id_2 = (SELECT id FROM lupaus_vaihtoehto_ryhma where "ryhma-otsikko" = 'Kyselytutkimuksen tulos');

        -- Yleinen
        -- Askel 1. josta päätyy 2 valinnasta askeleeseen 2 ja 3 valinnasta Askeleeseen 3
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '0%', 0,'Kannustavat alihankintasopimukset','Yleinen', 1, null, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '0 % ja ≤ 25 %', 2,'Kannustavat alihankintasopimukset','Yleinen', 1, null, ryhma_otsikko_id_1);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '> 25 %', 0,'Kannustavat alihankintasopimukset','Yleinen', 1, 2, ryhma_otsikko_id_1);

        -- Askel 2.
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '<= 4,1', 1,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '> 4,1', 4,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '> 4,4', 8,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '> 4,7', 12,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '> 5,0', 16,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
        PERFORM luo_lupauksen_vaihtoehto(3, 2025, '> 5,3', 20,'Kannustavat alihankintasopimukset','Yleinen', 2, null, ryhma_otsikko_id_2);
    END
$$ LANGUAGE plpgsql;

-- Lupaus nro. 5 Maksuehto - Vaihtoehdot- kaikki urakat
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2025, '> 30 pv', 0,'Kannustavat alihankintasopimukset', 'Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2025, '≤ 30 pv', 6,'Kannustavat alihankintasopimukset', 'Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2025, '≤ 21 pv', 12,'Kannustavat alihankintasopimukset', 'Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2025, '≤ 14 pv', 18,'Kannustavat alihankintasopimukset', 'Yleinen', null, null, null);

-- Lupaus nro. 6 Hoidon vuosikierron mukainen suunnittelu
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2025, '< 6 suunnittelukertaa / hoitovuosi', 0,'Toiminnan suunnitelmallisuus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2025, '≥ 6 suunnittelukertaa / hoitovuosi', 2,'Toiminnan suunnitelmallisuus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2025, '≥ 8 suunnittelukertaa / hoitovuosi', 6,'Toiminnan suunnitelmallisuus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2025, '≥ 10 suunnittelukertaa / hoitovuosi', 10,'Toiminnan suunnitelmallisuus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(6, 2025, '≥ 12 suunnittelukertaa / hoitovuosi', 14,'Toiminnan suunnitelmallisuus','Yleinen', null, null, null);

-- Lupaus nro. 8 Luovutuksen menettely
SELECT * FROM luo_lupauksen_vaihtoehto(8, 2025, '<40% itselleluovutettavista töistä / työkokonaisuuksista on itselleluovutettu', 0,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(8, 2025, '≥40% itselleluovutettavista töistä / työkokonaisuuksista on itselleluovutettu', 5,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(8, 2025, '≥60% itselleluovutettavista töistä / työkokonaisuuksista on itselleluovutettu', 10,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(8, 2025, '≥ 80% itselleluovutettavista töistä / työkokonaisuuksista on itselleluovutettu', 15,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(8, 2025, '100% itselleluovutettavista töistä / työkokonaisuuksista on itselleluovutettu', 20,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);

-- Lupaus nro. 9 Kohdistetun seurannan parantaminen
SELECT * FROM luo_lupauksen_vaihtoehto(9, 2025, 'Urakka-alueella on yksi vaatimusten mukainen työmaakamera tai vastaava.', 1,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(9, 2025, 'Urakka-alueella on kaksi vaatimusten mukaista työmaakameraa tai vastaavaa.', 2,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(9, 2025, 'Urakka-alueella on kolme vaatimusten mukaista työmaakameraa tai vastaavaa.', 3,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(9, 2025, 'Urakka-alueella on neljä vaatimusten mukaista työmaakameraa tai vastaavaa', 4,'Laadunvarmistus ja laadunosoitus','Yleinen', null, null, null);

-- Lupaus nro. 10 Turvallisuuden teemakokoukset
SELECT * FROM luo_lupauksen_vaihtoehto(10, 2025, 'Osallistumisprosentti < 50 %', 0, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(10, 2025, 'Osallistumisprosentti ≥ 50 %', 3, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(10, 2025, 'Osallistumisprosentti ≥ 70 %', 6, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(10, 2025, 'Osallistumisprosentti ≥ 90 %', 10, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);

-- Lupaus nro. 11 Ajoneuvokohtainen ajotavanseurantajärjestelmä
SELECT * FROM luo_lupauksen_vaihtoehto(11, 2025, 'Käytössä <15%:ssa ajoneuvoista', 0, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(11, 2025, 'Käytössä ≥15 %:ssa ajoneuvoista', 3, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(11, 2025, 'Käytössä ≥40 %:ssa ajoneuvoista', 6, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(11, 2025, 'Käytössä ≥65 %:ssa ajoneuvoista', 8, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(11, 2025, 'Käytössä ≥90 %:ssa ajoneuvoista', 10, 'Turvallisuus ja ympäristö','Yleinen', null, null, null);

-- Lupaus nro. 12 Tilanne- ja ennakkotiedotus
SELECT * FROM luo_lupauksen_vaihtoehto(12, 2025, 'Lupaus ei toteudu', 0, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(12, 2025, 'Lupaus toteutuu', 10, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(12, 2025, 'Lupaus toteutuu siten, että julkaisut tavoittavat
<ul><li>Perusurakassa vähintään 3 000</li>
<li>Vaativassa urakassa 6 000</li>
<li>Erittäin vaativassa urakassa 10 000 henkeä kuukaudessa.</li></ul>', 18, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);

-- Lupaus nro. 13 Viestintä sidosryhmien kanssa
SELECT * FROM luo_lupauksen_vaihtoehto(13, 2025, '0 vuoropuhelutilausuutta/hoitovuosi', 0, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(13, 2025, '1 vuoropuhelutilausuutta/hoitovuosi', 2, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(13, 2025, '2 vuoropuhelutilausuutta/hoitovuosi', 4, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(13, 2025, '3 vuoropuhelutilausuutta/hoitovuosi', 7, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(13, 2025, '4 vuoropuhelutilausuutta/hoitovuosi', 10, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
SELECT * FROM luo_lupauksen_vaihtoehto(13, 2025, '≥ 5 vuoropuhelutilausuutta/hoitovuosi', 14, 'Viestintä ja tienkäyttäjäasiakkaan palvelu','Yleinen', null, null, null);
