-- Ylläpito

COMMENT ON TABLE yllapitokohde IS
E'Ylläpitokohte kuvaa tienosaa, jolle tehdään ylläpitoluonteista työtä (päällystys, paikkaus, tiemerkintä). Taulua käytetään em. tyyppisissä urakoissa kohteiden osoitteiden ja niihin kohdistuvien töiden hallintaan. Sana "ylläpitokohde" on keksitty tietomallia varten, muutoin käytetään yleensä käsitteitä päällystys-, paikkaus- ja tiemerkintäkohde.\n

Kohteet ovat joko päällystys- tai paikkaustyyppisiä, joka päätellään yllopitokohdetyotyyppi-sarakkeesta. Kohteen tyyppi kuvaa sitä, millaista työtä kohteella on tarkoitus ensisijaisesti tehdä. Kohteen tyyppiä ei pidä sekoittaa sarakkeeseen yllapitokohdetyyppi, joka kuvaa sitä, millaista tietä ollaan työstämässä (päällystetty tie, soratie, kevytliikenne).\n

Ylläpitokohde koostuu yleensä vähintään yhdestä ylläpitokohdeosasta (ks. taulu yllapitokohdeosa).\n

Ylläpitokohteella voi olla ylläpitoluokka, jonka arvot ovat kokonaislukuja 1-10, koodit menevät YHA:an.\n
Ylläpitoluokista tarkempaa domain-tietoa löytyy harja.domain.yllapitokohde.\n
Ylläpitokohte on sidottu urakkaan urakka-sarakkeen kautta. Tämä sarake kuvaa kohtee "ensisijaista" urakkkaa. Lisäksi on olemassa sarake suorittava_tiemerkintaurakka, joka kuva kohteen suorittavaa tiemerkintäurakkaa. Tiemerkinnässä kohde siis edelleen kuuluu ensisijaisesti päällystysurakkaan urakka-sarakkeen kautta, mutta linkittyy tiemerkintäurakkaan suorittava_tiemerkintaurakka -sarakkeen kautta.';

COMMENT ON COLUMN yllapitokohde.kohdenumero IS 'Kohdenumero on käyttäjän itse syöttämä (yleensä YHA-kohdenumerosta muokattu, esim. laitettu kirjain perään).';
COMMENT ON COLUMN yllapitokohde.vuodet IS 'Kuvaa, minä vuonna kohdetta pääasiallisesti työstetään. Yleensä kohde tehdään yhden vuoden aikana, mutta kaiken varalta kyseessä on taulukko, jolloin usean vuoden kohteet ovat tietomallissa tuettuja.';

COMMENT ON TABLE yllapitokohdeosa IS
E'Ylläpitokohdeosa (käytetään myös nimityksiä alikohde ja tierekisterikohde) kuvaa tienosaa ylläpitokohteen sisällä. Kohdeosien avulla voidaan tarkemmin määrittää, miten ylläpitokohde jakaantuu osiin ja mitä toimenpiteitä eri osilla suoritetaan.\n

Suoritettava toimenpide kirjataan seuraaviin kenttiin:\n
- paallystetyyppi\n
- raekoko\n
- tyomenetelma\n
- massamaara\n
- toimenpide (sanallinen selitys).';

COMMENT ON TABLE yllapitokohteen_aikataulu IS
E'Sisältää ylläpitokohteen aikataulutiedot kohteen aloituksesta ja lopetuksesta ensisijaisen urakan näkökulmasta. Lisäksi päällystykselle ja tiemerkinnälle on omat aloitus- ja lopetusaikataulut.

 Jokaisella ylläpitokohteella tulisi olla vastinpari tässä taulussa (edes tyhjä rivi ilman mitään aikataulutietoja), mikäli sen aikataulutietoja aiotaan käsitellä';

COMMENT ON TABLE yllapitokohteen_maksuera IS
E'Sisältää ylläpitokohteeseen kuuluvien maksuerien tiedot (yksi rivi = yksi maksuerä). Maksuerä kuvastaa urakoitsijan tekemää ehdotusta ylläpitokohteen maksueristä. Tilaaja täyttää maksuerätunnuksen.';

-- Ylläpito (päällystys)

COMMENT ON TABLE paallystysilmoitus IS
E'Päällystysilmoitus on ylläpitokohteeseen (paallystyskohde-sarake) liittyvä ilmoitus tehdystä työstä.\n

  Päällystysilmoituksen varsinaiset tiedot tallentuvat ilmoitustiedot-sarakkeeseen JSONB-muodossa. Tässä JSONissa on listattu jokaiselle osoitteelle erikseen tieto siitä, mitä työtä kyseisessä osoitteessa on tehty. Osoite viittaa ylläpitokohteen kohdeosaan kohdeosa-id:llä.\n

  Päällystysilmoituksen muut tiedot tallentuvat normaalisti taulun eri sarakkeisiin. Päällystysilmoituksen päätös-sarakkeet kertovat tilaajan ilmoitukselle tekemän hyväksyntäprosessin tiedot. Asiakatarkastus-sarakkeet kertovat konsultin ilmoitukselle tekemästä asiatarkastuksesta.\n

  Päällystysilmoituksella on seuraavat tilat:\n
  - (ei tilaa), päällystysilmoitusta ei ole aloitettu
  - aloitettu, päällystysilmoitusta on alettu täyttää
  - valmis, päällystysilmoituksen tiedot on täytetty, mutta sitä ei ole vielä hyväksytty
  - lukittu, tilaaja on hyväksynyt tehdyn päällystysilmoituksen. Lukittua ilmoitusta ei tulisi enää muokata.';

-- Ylläpito (tiemerkintä)

COMMENT ON TABLE tiemerkinnan_yksikkohintainen_toteuma IS
E'Tauluun tallentuu tiemerkintäurakassa tehdyt toteumat, jotka voivat liittyä ylläpitokohteeseen. Mikäli toteuma ei liity ylläpitokohteeseen, sille kirjataan oma tr-osoite ja pituus. Jos linkittyy poistettuun ylläpitokohteeseen, tulkitaan myös itse toteuman olevan poistettu, vaikkei olisikaan eksplisiittisesti merkitty poistetuksi.\n

hinta_kohteelle, string, jonka sisältönä on kohteen osoite sillä hetkellä kun hinta annettiin. Käytetään tunnistamaan tilanne, jossa hinta on annettu kohteen vanhalle osoitteelle';

COMMENT ON TABLE yllapito_muu_toteuma IS
E'Tätä taulua käytetään tallentamaan ylläpidon urakoiden muita toteumia (tiemerkintäurakan muut toteumat ja päällystysurakan muut kustannukset)';

-- Mobiili laadunseuranta

COMMENT ON TABLE tarkastusajo IS
E'Tarkastusajo-tauluun tallentuu perustiedot Harjan laadunseurannan mobiilityökalulla aloitetusta tarkastusajosta, kuten ajon aloitus- ja lopetusaika. Käynnissä olevasta ajosta kerätään raakadataa tarkastusreitti-tauluun\n

tyyppi-sarake kertoo ajon tyypin. Nykyään tämä sarake ei ole enää käytössä.';

COMMENT ON TABLE tarkastusreitti IS
E'reittimerkinta-tauluun tallennetaan Harjan laadunseurannan mobiilityökalulla kerättyä raakaa dataa. Jokainen rivi taulussa kuvaa yksittäistä joko työkalun itsensä automaattisesti tekemää merkintää tarkastusajon aikana tai käyttäjän tekemää syötettä. Yhteen ajoon saattaa liittyä tuhansia eri merkintöjä. Reittimerkintä on aina uniikki merkinnän id:n ja siihen liittyvän tarkastusajon id:n kanssa.\n

Tauluun tallennettua tietoa käytetään luomaan yhteenveto tehdystä ajosta eli datasta muodostetaan tarkastus tarkastus-tauluun.\n

Sarakkeiden selitykset:\n
- sijainti_tarkkuus       HTML5-API:n palauttama GPS-pisteen tarkkuus (säde, metreinä)
- tr_osoite               Käyttäjän kirjaama TR-osoite ko. pisteelle (oletettavaa on, että suurimmalla osalla pisteitä ei ole tr-osoitetta merkitty, vaan se selvitetään vasta myöhemmin kun tarkastusreitit muunnetaan tarkastuksiksi';

COMMENT ON TABLE vakiohavainto IS
E'Vakiohavaintotaulussa esitellään erilaisia usein tehtäviä havaintoja, joita voidaan tehdä Harjan laadunseurannan mobiilityökalulla. Tärkeät sarakkeet:\n
- nimi: Käyttäjälle näkyvä vakiohavainnon nimi\n
- jatkuva: Onko tämä välikohtainen havainto (true jos on, false jos pistemäinen)\n
- avain: Vakiohavainnon tunniste muotoiltuna Clojure-avaiksi ilman kaksoispistettä (sama joka on mobiilityökalun UI:ssa)';


COMMENT ON TABLE valitavoite IS
E'Välitavoite kuvaa urakkaan liittyvää tehtävää asiaa, joka pyritään saamaan valmiiksi urakan aikana. Välitavoite sisältää mm. tehtävän asian kuvauksen sekä tiedot välitavoitteen valmistumisesta.\n

Välitavoite liitetään urakkaan urakka-sarakkeen kautta. Jos urakka-sarakkeessa on tyhjä arvo, välitavoitetta ei ole sidottu tällöin mihinkään urakkaan, vaan kyseessä on ns. valtakunnallinen välitavoite.\n

Valtakunnalliset välitavoitteet ovat välitavoitteita, jotka koskevat kaikkia tietyntyyppisiä urakoita ja niistä kopioidaan oma rivi tavoitetta koskeviin urakoihin. Urakkaan kopioidulla välitavoitteella on tieto siitä, mistä valtakunnallisesta välitavoitteesta kyseinen välitavoite on luotu. Valtakunnallinen välitavoite voi olla joko kertaluontoinen tai vuosittain toistuva, jolloin toisto-sarakkeet kertovat, minkä kuukauden päivänä välitavoite toistuu joka vuosi.\n

Tiemerkintäurakoissa valtakunnallisia välitavoitteita kutsutaan termillä "välitavoitepohja", mutta tietomallimielessä kyse on samasta asiasta.';

-- Lukot

COMMENT ON TABLE muokkauslukko IS
E'Muokkauslukko-taulua käytetään lukitsemaan jokin muokattava näkymä/asia (esim. päällystysilmoitus), jotta useampi käyttäjä ei voi muokata samaa asiaa samaan aikaan. Muokkauslukolla on:\n
 - id (voi olla mikä tahansa mielivaltainen teksti, mutta nimessä kannattaisi olla muokattavan asian nimi ja sen yksilöivä id)\n
 - kayttaja (kertoo, kuka asian lukitsi)\n
 - aikaleima (kertoo, milloin lukko on viimeksi virkistetty)';

-- Toteumat

COMMENT ON TABLE toteuma_tehtava IS
E'- Toteuman tehtävä linkittyy aina toteumaan. Jos toteuma on poistettu, tulkitaan myös tehtävän olevan poistettu, vaikkei itse tehtävää olisikaan eksplisiittisesti merkitty poistetuksi.';

-- Laadunseuranta

COMMENT ON TABLE tarkastus IS
E'- Tarkastus voi linkittyä ylläpitokohteeseen. Jos ylläpitokohde on poistettu, tulkitaan myös tarkastuksen olevan poistettu, vaikkei itse tarkastusta olisikaan eksplisiittisesti merkitty poistetuksi.';

COMMENT ON TABLE laatupoikkeama IS
E'- Laatupoikkeama voi linkittyä ylläpitokohteeseen. Jos ylläpitokohde on poistettu, tulkitaan myös laatupoikkeaman olevan poistettu, vaikkei itse laatupoikkeamaa olisikaan eksplisiittisesti merkitty poistetuksi.';

COMMENT ON TABLE sanktio IS
E'Sanktio-tauluun kirjataan urakassa sanktio tai bonus.\n
 - Sanktion tyyppi määräytyy tarkemmin taulun sanktiotyyppi ja enumit sanktiolaji kautta\n
 - Sanktio tyypillisesti määrätään laadun alituksesta tai toistuvasta huolimattomuudesta\n
 - Bonus tyypillisesti myönnetään odotukset ylittävästä toiminnallisesta laadusta\n
 - Tietomallissa Sanktioon liittyy aina laatupoikkeama, vaikka sanktio olisikin ns. suorasanktio\n
 - Suorasanktiot ovat sanktioita, jotka on luotu laatupoikkeamat/sanktiot näkymässä\n
 - Ylläpidon urakoissa sanktioihin voi liittyä vakiofraasi ja ylläpitokohde (laatupoikkeaman kautta linkitetty). Jos sanktio liittyy poistettuun laatupoikkeamaan tai ylläpitokohteeseen, sen katsotaan olevan poistettu, vaikkei itse sanktiota olisikaan eksplisiittisesti merkitty poistetuksi.';

COMMENT ON TABLE sanktiotyyppi IS
E'Sanktiotyyppi-taulussa kerrotaan eri urakkatyyppien kannalta olennaiset sanktiotyypit.\n
 - Sanktiotyyppi-rivi kertoo tyypin nimen, mahdollisesti siihen liittyvän toimenpidekoodin, urakkatyypin ja sanktiolajin.';

COMMENT ON TABLE urakkatyypin_indeksi IS
E'Sisältää tiedon mitä indeksejä erityyppisissä urakoissa on käytössä.';

COMMENT ON TABLE paallystysurakan_indeksi IS
E'Päällystysurakoissa pitää sitoa kustannuksia mm. bitumin ja kevyen polttoöljyn tai \n
 nestekaasun hintaindekseihin. Tässä taulussa on tieto, mitä sidontoja päällystysurakoissa on tehty.';

-- Vesiväylät

COMMENT ON TABLE vv_vayla IS
E'Väylä on vedessä oleva reitti, jonka varressa on turvalaitteita. Väylillä on nimi, tyyppi, ja sijainti. Tyyppi voi olla kauppamerenkulku tai muu. Koska turvalaitteiden pitää aina liittyä väylään, tyyppeihin voidaan joutua lisäämään myös "Virtuaaliväylä", joka ei ole oikeasti väylä. Tällaiset turvalaitteet ovat avomerellä.';

COMMENT ON TABLE vatu_turvalaite IS
E'Turvalaitteet ovat vedessä olevia poijuja, viittoja, ja muita asioita. Kaikki vesiväylien toimenpiteet liittyvät aina turvalaitteisiin. Turvalaitteissa voi olla komponentteja, kuten aurinkopaneeleita, akkuja, jne. Turvlaaitteet liittyvät aina väylään.';

COMMENT ON TABLE vv_vikailmoitus IS
E'Vikailmoitukset ovat turvalaitteista löydettyjä vikoja. Viat täytyy korjata tietyn ajan sisällä. Vikakorjaus tulee Harjaan toimenpiteenä.';

COMMENT ON TABLE reimari_toimenpide IS
E'Sisältää Reimarista tuodut toimenpiteiden tiedot. Data on melko raakaa, siksi monet kentät on toteutettu TYPE:llä, eikä esim. linkkeinä muihin tauluihin. reimari-etuliitteelliset sarakkeet sisältävät Reimarista tuotua tietoa, muut kentät on Harjassa luotuja.';

COMMENT ON TABLE reimari_turvalaiteryhma IS
E'Kuvaa turvalaiteryhmää eli ryhmää turvalaitteita, jotka muodostavat vesiväyläurakka-alueen . Kaikki tiedot tulevat tähän tauluun Reimarista.';
COMMENT ON COLUMN reimari_turvalaiteryhma."turvalaitteet" IS 'Turvalaiteryhmään kuuluvien turvalaitteiden numerot. Samaa numeroa käytetään turvalaitteen tunnistamiseen myös muualla, esim. vv_turvalaite taulussa.';


COMMENT ON TABLE reimari_turvalaitekomponentti IS
E'Kuvaa turvalaitteeseen liittyvää komponenttia. Kaikki tiedot tulevat tähän tauluun Reimarista.';

COMMENT ON TABLE reimari_komponenttityyppi IS
E'Kuvaa turvalaitteen komponentin tyyppiä. Kaikki tiedot tulevat tähän tauluun Reimarista.';

COMMENT ON TABLE vv_hinnoittelu_toimenpide IS
E'Linkkitaulu, jolla toimenpiteet ja hinnoittelut liitetään toisiinsa.';

COMMENT ON TABLE vv_hinnoittelu IS
E'Reimari-toimenpiteet kuuluvat hinnoitteluihin vv_hinnoittelu_toimenpide taulun kautta. Kaikki hinnoittelut koskevat vain yksikköhintaisia Reimarin toimenpiteitä.\n

  Hinnoittelu voi olla hintaryhmä, jolla on useita hintoja ja johon voi kuulua useita toimenpiteitä. Tällaisella hinnoittelulla on hintaryhmä-sarakkeessa arvo true.\n

  Hinnoittelu voi kuvata myös toimenpiteen omaa hintaa. Eli jos halutaan määritellä yhdelle toimenpiteelle hinta, niin sille luodaan hinnoittelu, joka ei ole hintaryhmä, sekä hinnoitteluun kuuluvat hinnat.';

COMMENT ON TABLE vv_hinta IS
E'Hinta liittyy aina hinnoitteluun. Hinta annetaan joko summana TAI syötetään määrä, yksikkö ja yksikköhinta';
COMMENT ON COLUMN vv_hinta.ryhma IS 'Tämä on tarkoitettu pääasiassa frontille, jotta hinnat voidaan näyttää oikeiden otsikoiden alla.';
COMMENT ON COLUMN vv_hinta."komponentti-tilamuutos" IS 'Viittaa reimari_toimenpide taulussa olevaan array-tyyppiseen sarakkeeseen reimari-komponentit ja siinä olevaan tilakoodiin.';

COMMENT ON TABLE vv_tyo IS
E'Taulua käytetään ilmaisemaan vesiväylien toimenpiteen hinnoittelussa tehdyn työn toimenpidekoodi ja määrä.';

COMMENT ON TABLE vv_kiintio IS
E'Vesiväylien sopimuksissa mainitaan usein kiintiöitä, jotka voivat olla hyvin vapaamuotoisia. Esimerkiksi sopimukseen voisi kuulua 15 kappaletta talvella vaurioituneen viitan korjausta ja 5 kappaletta vanhentuneen aurinkopaneelin päivitystä. Kokonaishintaiset toimenpiteet täyttävät näitä kiintiöitä, ja kun kiintiö täyttyy, aletaan toimenpiteistä maksamaan yksikköhintoja. Tässä vaiheessa tilaaja/urakoitsija yhdessä määrittelevät tarkalleen mitkä toimenpiteet kuuluvat kiintiöön, ja mitkä eivät, koska esimerkiksi kauas tehdystä toimenpiteestä joudutaan maksamaan suurempi yksikköhinta matkoista. Urakoitsija merkitsee kokonaishintaisia toimenpiteitä kiintiöihin kuuluvaksi, ja tilaaja seuraa kiintiöiden täyttymistä.';

COMMENT ON TABLE vv_materiaali IS
E'Vesiväylien tilaajan materiaalin hallinta kerää tilaajan urakoitsijalle myötämien materiaalien kappalemäärien alkutilanteen sekä muutokset. Esimerkiksi tilaaja luovuttaa urakoitsijalle 10kpl poijuja urakan alussa ja urakoitsija käyttää niitä urakan aikana. Urakan aikana käytetyt ja hankitut materiaalit tulevat riveiksi tähän tauluun.';

COMMENT ON TABLE toimenpideinstanssi_vesivaylat IS
E'Taulun tarkoitus on liittää toimenpideinstanssiin vesiväylä -spesifistä tietoa.';

-- Kanavat

COMMENT ON TABLE kan_kohdekokonaisuus IS
E'Kohdekokonaisuus on ihmstä varten oleva kategoria. Kohteet kuuluvat kohdekokonaisuuksiin, esim "Iisalmen reitti"';

COMMENT ON TABLE kan_kohde IS
E'Kohteet ovat kanavalla sijaitsevia alueita, joihin urakat kohdistuvat. \n
  Kohde sisältää kohteenosia, eli siltoja ja sulkuja. Saimaan kanavan kohteilla on järjestys liikennetapahtumien ketjutusta varten.';

COMMENT ON TABLE kan_kohteenosa IS
E'Kohteenosat ovat siltoja ja sulkuja, jotka sisältyvät kohteeseen. Toimenpiteet kohdistetaan yleensä kohteenosaan.';

COMMENT ON COLUMN kan_kohteenosa.oletuspalvelumuoto IS 'Palvelumuoto, joka annetaan kohdeosalle oletuksena, kun kirjataan liikennetapahtuma';

COMMENT ON TABLE kan_liikennetapahtuma IS
E'Kun alus kulkee kohteen läpi, siitä kirjataan liikennetapahtumalle. Aluksista, ja kohteenosilla käytettävistä palvelumuodoista pidetään kirjaa.';

COMMENT ON TABLE kan_liikennetapahtuma_alus IS
E'Liikennetapahtumaan kirjattava alus.';

COMMENT ON TABLE kan_liikennetapahtuma_toiminto IS
E'Liikennetapahtumat kohdistetaan koko kohteelle, mutta kohteenosat voivat käyttää eri palvelumuotoja, ja eri toimenpiteitä.';

COMMENT ON TABLE kan_huoltokohde IS
E'Huoltokohteet ovat asioita, joita kanavasta korjataan. Huoltokohteita ei pidä sekoittaa kohteeseen! Huoltokohde voi olla esim "Hydrauliikka", kun taas kohde on fyysinen sijainti kanavan varrella, esim. silta.';

COMMENT ON TABLE kan_hairio IS
E'Kuvaa häiriötilannetta. Urakoitsija vastaa hoitourakoissa tapahtuvien häiriötilanteiden tietojen kirjaamisesta.';
COMMENT ON COLUMN kan_hairio.odotusaika_h IS 'Aika, jonka liikennöivät alukset joutuvat odottamaan häiriön takia';
COMMENT ON COLUMN kan_hairio.ammattiliikenne_lkm IS 'Ammattiliikennealusten määrä, jotka joutuvat odottamaan häiriön takia';
COMMENT ON COLUMN kan_hairio.huviliikenne_lkm IS 'Huviliikennealusten määrä, jotka joutuvat odottamaan häiriön takia';
COMMENT ON COLUMN kan_hairio.korjaustoimenpide IS 'Vapaamuotoinen toimenpiteen kuvaus, esim. "Kamera resetoitu"';
COMMENT ON COLUMN kan_hairio.paikallinen_kaytto IS 'Valitaan, onko siirrytty paikallliskäyttöön';

COMMENT ON TABLE kan_toimenpide IS
E'Kuvaa konkreettiset toimenpiteet joita on tehty tietyn kanavan kohteelle, eli sillalle tai sululle, jotka kohdistuvat taas tietttyyn huoltokohteeseen, kuten esim. hydrauliikkaan. Voivat olla hinnoittelultaan esim. kokonais- tai yksikköhintaisia.';

-- Muut

COMMENT ON TABLE kayttaja_anti_csrf_token IS E'Sisältää käyttäjän anti-CSRF tokenin tiedot. Jokaisella tokenilla on voimassaoloaika, ja niitä voi olla useita per käyttäjä (yksi per selainikkuna/-välilehti). Tarkempi tekninen toteutus dokumentoitu koodiin.';

COMMENT ON TABLE yllapitokohteen_sahkopostitiedot IS
E'Sisältää ylläpitokohteen sähköpostilähetystiedot.';
COMMENT ON COLUMN yllapitokohteen_sahkopostitiedot.kopio_lahettajalle IS 'Mailin aikaansaaneen käyttäjän s-posti, johon lähetetään kopio viestistä (tai NULL)';
COMMENT ON COLUMN yllapitokohteen_sahkopostitiedot.vastaanottajat IS 'Vastaanottajat, joille viesti tulisi ainakin lähettää. Viesti saattaa tyypistä riippuen sisältää myös muita vastaanottajia, joille viesti laitetaan aina (esim. tiemerkinnän valmistuminen ilmoitetaan tietyille FIM-käyttäjille)';
