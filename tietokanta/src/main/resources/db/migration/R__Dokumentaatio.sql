COMMENT ON TABLE yllapitokohde IS
E'Ylläpitokohte kuvaa tienosaa, jolle tehdään ylläpitoluonteista työtä (päällystys, paikkaus, tiemerkintä). Taulua käytetään em. tyyppisissä urakoissa kohteiden osoitteiden ja niihin kohdistuvien töiden hallintaan. Sana "ylläpitokohde" on keksitty tietomallia varten, muutoin käytetään yleensä käsitteitä päällystys-, paikkaus- ja tiemerkintäkohde.\n\n

Kohteet ovat joko päällystys- tai paikkaustyyppisiä (kannassa yllopitokohdetyotyyppi). Kohteen tyyppi kuvaa sitä, millaista työtä kohteella on tarkoitus ensisijaisesti tehdä. Kohteen tyyppiä ei pidä sekoittaa sarakkeeseen yllapitokohdetyyppi, joka kuvaa sitä, millaista tietä ollaan työstämässä (päällystetty tie, soratie, kevytliikenne).\n\n

Ylläpitokohde koostuu yleensä vähintään yhdestä ylläpitokohdeosasta (ks. taulu yllapitokohdeosa).\n\n

Ylläpitokohte on sidottu urakkaan urakka-sarakkeen kautta. Tämä sarake kuvaa kohtee "ensisijaista" urakkkaa. Lisäksi on olemassa sarake suorittava_tiemerkintaurakka, joka kuva kohteen suorittavaa tiemerkintäurakkaa. Tiemerkinnässä kohde siis edelleen kuuluu ensisijaisesti päällystysurakkaan urakka-sarakkeen kautta, mutta linkittyy tiemerkintäurakkaan suorittava_tiemerkintaurakka -sarakkeen kautta.';

COMMENT ON TABLE yllapitokohdeosa IS
E'Ylläpitokohdeosa kuvaa tienosaa ylläpitokohteen sisällä. Kohdeosien avulla voidaan tarkemmin määrittää, miten ylläpitokohde jakaantuu osiin ja mitä toimenpiteitä eri osilla suoritetaan. Ylläpitokohdeosien tulisi kattaa ylläpitokohteen tieosoite kokonaan alusta loppuun niin, ettei väliin jää "tyhjää aluetta".';