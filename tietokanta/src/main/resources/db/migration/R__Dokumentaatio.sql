-- Ylläpito

COMMENT ON TABLE yllapitokohde IS
E'Ylläpitokohte kuvaa tienosaa, jolle tehdään ylläpitoluonteista työtä (päällystys, paikkaus, tiemerkintä). Taulua käytetään em. tyyppisissä urakoissa kohteiden osoitteiden ja niihin kohdistuvien töiden hallintaan. Sana "ylläpitokohde" on keksitty tietomallia varten, muutoin käytetään yleensä käsitteitä päällystys-, paikkaus- ja tiemerkintäkohde.\n\n

Kohteet ovat joko päällystys- tai paikkaustyyppisiä, joka päätellään yllopitokohdetyotyyppi-sarakkeesta. Kohteen tyyppi kuvaa sitä, millaista työtä kohteella on tarkoitus ensisijaisesti tehdä. Kohteen tyyppiä ei pidä sekoittaa sarakkeeseen yllapitokohdetyyppi, joka kuvaa sitä, millaista tietä ollaan työstämässä (päällystetty tie, soratie, kevytliikenne).\n\n

Ylläpitokohde koostuu yleensä vähintään yhdestä ylläpitokohdeosasta (ks. taulu yllapitokohdeosa).\n\n
Ylläpitokohteella voi olla ylläpitoluokka, jonka arvot ovat kokonaislukuja 1-10, koodit menevät YHA:an.\n
Ylläpitoluokista tarkempaa domain-tietoa löytyy harja.domain.yllapitokohteet.\n\n
Ylläpitokohte on sidottu urakkaan urakka-sarakkeen kautta. Tämä sarake kuvaa kohtee "ensisijaista" urakkkaa. Lisäksi on olemassa sarake suorittava_tiemerkintaurakka, joka kuva kohteen suorittavaa tiemerkintäurakkaa. Tiemerkinnässä kohde siis edelleen kuuluu ensisijaisesti päällystysurakkaan urakka-sarakkeen kautta, mutta linkittyy tiemerkintäurakkaan suorittava_tiemerkintaurakka -sarakkeen kautta.';

COMMENT ON TABLE yllapitokohdeosa IS
E'Ylläpitokohdeosa (käytetään myös nimityksiä alikohde ja tierekisterikohde) kuvaa tienosaa ylläpitokohteen sisällä. Kohdeosien avulla voidaan tarkemmin määrittää, miten ylläpitokohde jakaantuu osiin ja mitä toimenpiteitä eri osilla suoritetaan. Ylläpitokohdeosien tulisi kattaa ylläpitokohteen tieosoite kokonaan alusta loppuun niin, ettei väliin jää "tyhjää aluetta".';

-- Ylläpito (päällystys)

COMMENT ON TABLE paallystysilmoitus IS
E'Päällystysilmoitus on ylläpitokohteeseen (paallystyskohde-sarake) liittyvä ilmoitus tehdystä työstä.\n\n

  Päällystysilmoituksen varsinaiset tiedot tallentuvat ilmoitustiedot-sarakkeeseen JSONB-muodossa. Tässä JSONissa on listattu jokaiselle osoitteelle erikseen tieto siitä, mitä työtä kyseisessä osoitteessa on tehty. Osoite viittaa ylläpitokohteen kohdeosaan kohdeosa-id:llä.\n\n

  Päällystysilmoituksen muut tiedot tallentuvat normaalisti taulun eri sarakkeisiin. Päällystysilmoituksen päätös-sarakkeet kertovat tilaajan ilmoitukselle tekemän hyväksyntäprosessin tiedot. Asiakatarkastus-sarakkeet kertovat konsultin ilmoitukselle tekemästä asiatarkastuksesta.\n\n

  Päällystysilmoituksella on seuraavat tilat:\n
  - (ei tilaa), päällystysilmoitusta ei ole aloitettu
  - aloitettu, päällystysilmoitusta on alettu täyttää
  - valmis, päällystysilmoituksen tiedot on täytetty, mutta sitä ei ole vielä hyväksytty
  - lukittu, tilaaja on hyväksynyt tehdyn päällystysilmoituksen. Lukittua ilmoitusta ei tulisi enää muokata.';

-- Mobiili laadunseuranta

COMMENT ON TABLE tarkastusajo IS
E'Tarkastusajo-tauluun tallentuu perustiedot Harjan laadunseurannan mobiilityökalulla aloitetusta tarkastusajosta, kuten ajon aloitus- ja lopetusaika. Käynnissä olevasta ajosta kerätään raakadataa tarkastusreitti-tauluun\n\n

tyyppi-sarake kertoo ajon tyypin. Nykyään tämä sarake ei ole enää käytössä.';

COMMENT ON TABLE tarkastusreitti IS
E'reittimerkinta-tauluun tallennetaan Harjan laadunseurannan mobiilityökalulla kerättyä raakaa dataa. Jokainen rivi taulussa kuvaa yksittäistä joko työkalun itsensä automaattisesti tekemää merkintää tarkastusajon aikana tai käyttäjän tekemää syötettä. Yhteen ajoon saattaa liittyä tuhansia eri merkintöjä. Reittimerkintä on aina uniikki merkinnän id:n ja siihen liittyvän tarkastusajon id:n kanssa.\n\n

Tauluun tallennettua tietoa käytetään luomaan yhteenveto tehdystä ajosta eli datasta muodostetaan tarkastus tarkastus-tauluun.\n\n

Sarakkeiden selitykset:\n
- sijainti_tarkkuus       HTML5-API:n palauttama GPS-pisteen tarkkuus (säde, metreinä)';

COMMENT ON TABLE vakiohavainto IS
E'Vakiohavaintotaulussa esitellään erilaisia usein tehtäviä havaintoja, joita voidaan tehdä Harjan laadunseurannan mobiilityökalulla. Tärkeät sarakkeet:\n
- nimi: Käyttäjälle näkyvä vakiohavainnon nimi\n
- jatkuva: Onko tämä välikohtainen havainto (true jos on, false jos pistemäinen)\n
- avain: Vakiohavainnon tunniste muotoiltuna Clojure-avaiksi ilman kaksoispistettä (sama joka on mobiilityökalun UI:ssa)';

-- Raportointi

COMMENT ON TABLE raportti IS
E'Raportti-taulu sisältää raportit, jotka voidaan suorittaa.\n\n

  nimi                      Raportin keyword nimi, esim "laskutusyhteenveto"\n
  kuvaus                    Raportin ihmisen luettava nimi, esim, "Laskutusyhteenveto"\n
  konteksti                 kontekstit, jossa raportin voi suorittaa (ks. raporttiparametri-enum)\n
  parametrit                Parametrit, joilla raportti voidaan suorittaa (ks. raporttiparametri-enum)\n
  koodi                     Viittaus Clojure-koodiin, joka suorittaa raportin\n
  urakkatyyppi              Array urakkatyyppejä, joille raportti voidaan suorittaa';

-- Välitavoitteet

COMMENT ON TABLE valitavoite IS
E'Välitavoite kuvaa urakkaan liittyvää tehtävää asiaa, joka pyritään saamaan valmiiksi urakan aikana. Välitavoite sisältää mm. tehtävän asian kuvauksen sekä tiedot välitavoitteen valmistumisesta.\n\n

Välitavoite liitetään urakkaan urakka-sarakkeen kautta. Jos urakka-sarakkeessa on tyhjä arvo, välitavoitetta ei ole sidottu tällöin mihinkään urakkaan, vaan kyseessä on ns. valtakunnallinen välitavoite.\n\n

Valtakunnalliset välitavoitteet ovat välitavoitteita, jotka koskevat kaikkia tietyntyyppisiä urakoita ja niistä kopioidaan oma rivi tavoitetta koskeviin urakoihin. Urakkaan kopioidulla välitavoitteella on tieto siitä, mistä valtakunnallisesta välitavoitteesta kyseinen välitavoite on luotu. Valtakunnallinen välitavoite voi olla joko kertaluontoinen tai vuosittain toistuva, jolloin toisto-sarakkeet kertovat, minkä kuukauden päivänä välitavoite toistuu joka vuosi.\n\n

Tiemerkintäurakoissa valtakunnallisia välitavoitteita kutsutaan termillä "välitavoitepohja", mutta tietomallimielessä kyse on samasta asiasta.';

-- Lukot

COMMENT ON TABLE muokkauslukko IS
E'Muokkauslukko-taulua käytetään lukitsemaan jokin muokattava asia (esim. päällystysilmoitus), jotta useampi käyttäjä ei voi muokata samaa asiaa samaan aikaan. Muokkauslukolla on:\n
 - id (voi olla mikä tahansa mielivaltainen teksti, mutta nimessä kannattaisi olla muokattavan asian nimi ja sen yksilöivä id)\n
 - kayttaja (kertoo, kuka asian lukitsi)\n
 - aikaleima (kertoo, milloin lukko on viimeksi virkistetty)';

COMMENT ON TABLE sanktio IS
E'Sanktio-tauluun kirjataan urakassa sanktio tai bonus.\n
 - Sanktion tyyppi määräytyy tarkemmin taulun sanktiotyyppi ja enumit sanktiolaji kautta\n
 - Sanktio tyypillisesti määrätään laadun alituksesta tai toistuvasta huolimattomuudesta\n
 - Bonus tyypillisesti myönnetään odotukset ylittävästä toiminnallisesta laadusta\n
 - Tietomallissa Sanktioon liittyy aina laatupoikkeama, vaikka sanktio olisikin ns. suorasanktio\n
 -- Suorasanktiot ovat sanktioita, jotka on luotu laatupoikkeamat/sanktiot näkymässä\n
 - Ylläpidon urakoissa sanktioihin voi liittyä vakiofraasi ja ylläpitokohde (laatupoikkeaman kautta linkitetty)';


COMMENT ON TABLE sanktiotyyppi IS
E'Sanktiotyyppi-taulussa kerrotaan eri urakkatyyppien kannalta olennaiset sanktiotyypit.\n
 - Sanktiotyyppi-rivi kertoo tyypin nimen, mahdollisesti siihen liittyvän toimenpidekoodin, urakkatyypin ja sanktiolajin.';

COMMENT ON TABLE urakkatyypin_indeksi IS
E'Sisältää tiedon mitä indeksejä erityyppisissä urakoissa on käytössä.';

COMMENT ON TABLE paallystysurakan_indeksit IS
E'Päällystysurakoissa pitää sitoa kustannuksia mm. raskaan ja kevyen polttoöljyn tai \n
 nestekaasun hintaindekseihin. Tässä taulussa on tieto, mitä sidontoja päällystysurakoissa on tehty.';