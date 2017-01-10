COMMENT ON TABLE yllapitokohde IS
E'Ylläpitokohte kuvaa tienosaa, jolle tehdään ylläpitoluonteista työtä (päällystys, paikkaus, tiemerkintä). Taulua käytetään em. tyyppisissä urakoissa kohteiden osoitteiden ja niihin kohdistuvien töiden hallintaan. Sana "ylläpitokohde" on keksitty tietomallia varten, muutoin käytetään yleensä käsitteitä päällystys-, paikkaus- ja tiemerkintäkohde.\n\n

Kohteet ovat joko päällystys- tai paikkaustyyppisiä, joka päätellään yllopitokohdetyotyyppi-sarakkeesta. Kohteen tyyppi kuvaa sitä, millaista työtä kohteella on tarkoitus ensisijaisesti tehdä. Kohteen tyyppiä ei pidä sekoittaa sarakkeeseen yllapitokohdetyyppi, joka kuvaa sitä, millaista tietä ollaan työstämässä (päällystetty tie, soratie, kevytliikenne).\n\n

Ylläpitokohde koostuu yleensä vähintään yhdestä ylläpitokohdeosasta (ks. taulu yllapitokohdeosa).\n\n

Ylläpitokohte on sidottu urakkaan urakka-sarakkeen kautta. Tämä sarake kuvaa kohtee "ensisijaista" urakkkaa. Lisäksi on olemassa sarake suorittava_tiemerkintaurakka, joka kuva kohteen suorittavaa tiemerkintäurakkaa. Tiemerkinnässä kohde siis edelleen kuuluu ensisijaisesti päällystysurakkaan urakka-sarakkeen kautta, mutta linkittyy tiemerkintäurakkaan suorittava_tiemerkintaurakka -sarakkeen kautta.';

COMMENT ON TABLE yllapitokohdeosa IS
E'Ylläpitokohdeosa (käytetään myös nimityksiä alikohde ja tierekisterikohde) kuvaa tienosaa ylläpitokohteen sisällä. Kohdeosien avulla voidaan tarkemmin määrittää, miten ylläpitokohde jakaantuu osiin ja mitä toimenpiteitä eri osilla suoritetaan. Ylläpitokohdeosien tulisi kattaa ylläpitokohteen tieosoite kokonaan alusta loppuun niin, ettei väliin jää "tyhjää aluetta".';

COMMENT ON TABLE tarkastusajo IS
E'Tarkastusajo-tauluun tallentuu perustiedot Harjan laadunseurannan mobiilityökalulla aloitetusta tarkastusajosta, kuten ajon aloitus- ja lopetusaika.';

COMMENT ON TABLE reittimerkinta IS
E'Reittimerkintä-tauluun tallennetaan Harjan laadunseurannan mobiilityökalulla kerättyä dataa. Jokainen rivi taulussa kuvaa yksittäistä joko työkalun itsensä automaattisesti tekemää merkintää tarkastusajon aikana tai käyttäjän tekemää syötettä. Yhteen ajoon saattaa liittyä tuhansia eri merkintöjä. Reittimerkintä on aina uniikki merkinnän id:n ja siihen liittyvän tarkastusajon id:n kanssa.';

COMMENT ON TABLE valitavoite IS
E'Välitavoite kuvaa urakkaan liittyvää tehtävää asiaa, joka pyritään saamaan valmiiksi urakan aikana. Välitavoite sisältää mm. tehtävän asian kuvauksen sekä tiedot välitavoitteen valmistumisesta.\n\n

Välitavoite liitetään urakkaan urakka-sarakkeen kautta. Jos urakka-sarakkeessa on tyhjä arvo, välitavoitetta ei ole sidottu tällöin mihinkään urakkaan, vaan kyseessä on ns. valtakunnallinen välitavoite.\n\n

Valtakunnalliset välitavoitteet ovat välitavoitteita, jotka koskevat kaikkia tietyntyyppisiä urakoita ja niistä kopioidaan oma rivi tavoitetta koskeviin urakoihin. Urakkaan kopioidulla välitavoitteella on tieto siitä, mistä valtakunnallisesta välitavoitteesta kyseinen välitavoite on luotu. Valtakunnallinen välitavoite voi olla joko kertaluontoinen tai vuosittain toistuva, jolloin toisto-sarakkeet kertovat, minkä kuukauden päivänä välitavoite toistuu joka vuosi.\n\n

Tiemerkintäurakoissa valtakunnallisia välitavoitteita kutsutaan termillä "välitavoitepohja", mutta tietomallimielessä kyse on samasta asiasta.';

COMMENT ON TABLE muokkauslukko IS
E'Muokkauslukko-taulua käytetään lukitsemaan jokin muokattava asia (esim. päällystysilmoitus), jotta useampi käyttäjä ei voi muokata samaa asiaa samaan aikaan. Muokkauslukolla on:\n
 - id (voi olla mikä tahansa mielivaltainen teksti, mutta nimessä kannattaisi olla muokattavan asian nimi ja sen yksilöivä id)\n
 - kayttaja (kertoo, kuka asian lukitsi)\n
 - aikaleima (kertoo, milloin lukko on viimeksi virkistetty)';

COMMENT ON TABLE raportti IS
E'Raportti-taulu sisältää raportit, jotka voidaan suorittaa.\n\n

  nimi                      Raportin keyword nimi, esim "laskutusyhteenveto"\n
  kuvaus                    Raportin ihmisen luettava nimi, esim, "Laskutusyhteenveto"\n
  konteksti                 kontekstit, jossa raportin voi suorittaa (ks. raporttiparametri-enum)\n
  parametrit                Parametrit, joilla raportti voidaan suorittaa (ks. raporttiparametri-enum)\n
  koodi                     Viittaus Clojure-koodiin, joka suorittaa raportin\n
  urakkatyyppi              Array urakkatyyppejä, joille raportti voidaan suorittaa';