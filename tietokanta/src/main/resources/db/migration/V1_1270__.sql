
-- Lupausten pohjadata hoitokaudelle 2026-2027
INSERT INTO lupausryhma(otsikko, jarjestys, "urakan-alkuvuosi", luotu, "rivin-tunnistin-selite")
VALUES
    ('Kannustavat alihankintasopimukset', 1, 2026, NOW(), 'Yleinen'),
    ('Toiminnan suunnitelmallisuus', 2, 2026, NOW(), 'Yleinen'),
    ('Laadunvarmistus ja laadunosoitus', 3, 2026, NOW(), 'Yleinen'),
    ('Turvallisuus ja ympäristö', 4, 2026, NOW(), 'Yleinen'),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2026, NOW(), 'Yleinen');

-- Linkitetään 2026 urakat lupausryhmiin
INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
         JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm)
WHERE lupausryhma."urakan-alkuvuosi" = 2026
  AND lupausryhma."rivin-tunnistin-selite" = 'Yleinen';

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", kuvaus, sisalto, "urakan-alkuvuosi") VALUES
-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 26, '{10,6}', '{6}', 0,
 'Talvihoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään niissä alihankintasopimuksissa, jotka ovat toteuttaneet ko. hoitokauden hoitourakan
 töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. <br><br>' ||
 '<b>Tilaaja on varannut vuosittain</b><br> ' ||
 '<ul> <li>perusurakassa 5 000 € ja me vähintään 15 000 €;</li>' ||
 '<li>vaativassa urakassa 8 000 € ja me vähintään 24 000 €; sekä</li>' ||
 '<li>erittäin vaativassa urakassa 12 000 € ja me vähintään 36 000 €</li> </ul>' ||
 'tämän lupauksen mukaiseen kannustinjärjestelmään. <br><br>' ||
 'Tilaajan ja meidän rahavarauksemme yhdistetään ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.' ,
 2026),

(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 18, '{4,9}', '{9}', 0,
 'Kesähoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on käytössä vähintään niissä alihankintasopimuksissa, ' ||
 'jotka ovat toteuttaneet ko. hoitokaudella hoitourakan töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa. Lupaus täyttyy myös ' ||
 'kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama järjestelmä on edelleen käytössä.<br><br>

<b>Tilaaja on varannut vuosittain</b> <br>
<ul>
<li>perusurakassa 2 000 € ja me vähintään 6 000 €;</li>
<li>vaativassa urakassa 4 000 € ja me vähintään 12 000 €; sekä </li>
<li>erittäin vaativassa urakassa 6 000 € ja me vähintään 18 000 € </li>
</ul>tämän lupauksen mukaiseen kannustinjärjestelmään. <br><br>

Tilaajan ja meidän rahavarauksemme yhdistetään ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2026),

(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 4, '{10,11,12,1,2,3,4,5,6,7,8,9}', '{9}', 0,
 'Alihankintasopimusten indeksiehto',
 'Sidomme kaikki yli vuoden pituiset alihankintasopimuksemme kyseiseen työsuoritukseen soveltuvaan indeksiin. (Esim. MAKU tienpidon erillisindeksi tai polttaineen hintaindeksi.)',
 2026),

-- Lupaus 4
(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 18, '{10,11,12,1,2,3,4,5,6,7,8,9}', '{9}', 0,
 'Maksuehto',
 'Emme rajoita laskutusehtoa työsuorituksia sisältävissä alihankintasopimuksissamme ja maksamme työsuorituksista alihankintasopimuksiin kirjatulla maksuehdolla. <br><br>' ||
 'Lupauksen toteuma arvioidaan työsuorituksia sisältävien alihankintasopimusten maksuehdon perusteella.',
 2026),

-- B. Toiminnan suunnitelmallisuus
-- Lupaus 5
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 14, '{10,11,12,1,2,3,4,5,6,7,8,9}', '{9}', 0,
 'Hoidon vuosikierron mukainen suunnittelu',
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä hoidon vuosikierron mukaisesti. Töitä voidaan ' ||
 'suunnitella esimerkiksi palaverein tai sähköisin menettelyin. Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.) ' ||
 'läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa suunniteltavalla aikajaksolla.',
 2026),

-- Lupaus 6
(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kustannusennuste', 8, '{1,4,6,8}', '{1,4,6,8}', 0,
 'Hoitovuoden lopun tavoitehinnan ja toteutuvien kustannuksien ennustaminen',
 'Ennustamme urakan hoitovuoden lopun tavoitehintaa ja toteutuvia kustannuksia 4 kertaa vuodessa alla mainittuihin määräpäiviin mennessä.<br><br>
<table class="lupaus-kuvaus-taulukko">
<thead>
 <tr>
 <td colspan=2>*31.8.</td>
<td colspan=2>15.1.</td>
<td colspan=2>30.4.</td>
<td colspan=2>30.6.</td>
</tr>
<tr>
 <td>Tarkkuus</td>
<td>Pistettä</td>
<td>Tarkkuus</td>
<td>Pistettä</td>
<td>Tarkkuus</td>
<td>Pistettä</td>
<td>Tarkkuus</td>
<td>Pistettä</td>
</tr>
 </thead>
 <tbody>
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
</tr> </tbody></table> <br><br>
*Tulevan hoitovuoden ennuste. <b>Määräpäivä ensimmäisenä hoitovuotena on 15.10.</b> <br><br>'  ||
 'Hoitovuoden toteutuneet lupauspisteet todetaan laskemalla lupaustaulukon mukaan saatujen pisteiden keskiarvo. <b> Mikäli' ||
 ' jotain ennustetta ei tehdä määräaikaan mennessä, ennusteesta ei saa yhtään pistettä.</b>',
 2026),




-- C. Laadunvarmistus ja laadunosoitus
-- Lupaus 7
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja laadunosoitus' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 20, '{9}', '{9}', 0,
 'Luovutuksen menettely',
 'Meillä (pääurakoitsijalla) on käytössä itselleluovutuksen menettely määräaikaan sidotuista töistä / työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta. Alihankkijamme tekevät itselleluovutuksen vastaavista omista töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista. Itselleluovutukset dokumentoidaan tilaajan hankeaineiston hallintajärjestelmään.',
 2026),

-- Lupaus 8
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja laadunosoitus' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 4, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', '{0}', 0,
 'Kohdistetun seurannan parantaminen',
 'Sijoittamme hoidollisesti haastaviin tai muuten tilaajan toiminnan kannalta tarpeellisiin kohteisiin eri puolille urakka-aluetta yhdestä neljään työmaakameraa, tai vastaavaa.
<b>Kohteet, joihin kamerat sijoitetaan, sovitaan yhteistyössä tilaajan kanssa ja ne voivat vaihtua urakan ja hoitovuoden aikana.</b><br><br>

Kameroilla tuotetun materiaalin tulee olla joko videokuvaa tai valokuvia vähintään viiden (5) minuutin välein. Kuvan tulee olla vähintään HD-laatua (1920 × 1080 pikseliä). Videoiden toistonopeutta tulee voida säätää ja valokuvia tulee olla mahdollista katsella nk. timelapse-videona. Tuotettua materiaalia säilytettään yhden (1) kuukauden ajan ja siihen tulee antaa tilaajalle vapaa pääsy. Internet-yhteyden katkeamisen varalle järjestelmässä tulee olla tallennustilaa, jolle katkon aikana syntynyt materiaali tallentuu myöhemmin katsottavaksi. <br><br>

Kamerat tulee pyrkiä sijoittamaan niin, ettei yksittäisen tienkäyttäjän tunnistaminen ole mahdollista. Mikäli tienkäyttäjät ovat tunnistettavissa, on kuva-aineisto anonymisoitava ennen kuin aineisto on tilaajan ja urakoitsijan katseltavissa järjestelmän',
 2026),



-- D. Turvallisuus ja ympäristö
-- Lupaus 9
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja ympäristö' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', '{9}', 0,
 'Turvallisuuden teemakokoukset',
 'Pidämme alihankkijoiden operatiiviselle henkilöstölle hoitovuosittain työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.' ||
 ' Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa työmaakokouksessa.',
 2026),


-- Lupaus 10
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja ympäristö' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', '{0}', 0,
 'Ajoneuvokohtainen ajotavanseurantajärjestelmä',
 'Alihankkijoiltamme, jotka tekevät hoitokaudella hoitourakan töitä vähintään 20 henkilötyöpäivää tai laskuttavat vähintään 10 000 euroa, edellytetään <b>ajoneuvokohtaista ajotavanseurantajärjestelmää</b> ja hyödynnämme järjestelmän antamaa tietoa toiminnan johtamisessa.',
 2026),

-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
-- Lupaus 11
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely', 18, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', '{0}', 0,
 'Tilanne- ja ennakkotiedotus',
 'Toteutamme tilanne- ja ennakkotiedotusta paikallisten tiedotusvälineiden tai sosiaalisen median alustojen kautta vähintään kerran viikossa*. ' ||
 'Tilanne- ja ennakkotiedotusjulkaisu on kuvallinen ja paikkasidonnainen julkaisu tulevista tai käynnissä olevista urakan töistä. Julkaisuiksi ei lasketa muiden laatimien julkaisujen jakamista.<br><br>

<i>*Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.</i>',
 2026),

-- Lupaus 12
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'kysely',14, '{9}', '{9}', 0,
 'Viestintä sidosryhmien kanssa',
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt, koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin. Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä vuoropuhelusta ja viestinnästä.',
 2026),

-- Lupaus 13
(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9}', '{0}', 0,
 'Palautteet ja kehittäminen',
 'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden tietoisuuteen viikoittain*. ' ||
 'Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen, ' ||
 'työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa sekä huomioimme ne viestinnässä.<br><br>

<i>* Viestintä tulee hoitaa ajallaan vähintään 96 %:sti, jotta lupaus katsotaan toteutuneeksi.</i>',
 2026),

-- Lupaus 14
(14, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu' and "urakan-alkuvuosi" = 2026 and "rivin-tunnistin-selite" = 'Yleinen'), null, 'yksittainen', 8, '{9}', '{9}', 0,
 'Tyytyväisyystutkimustulokset',
 'Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
 2026);
