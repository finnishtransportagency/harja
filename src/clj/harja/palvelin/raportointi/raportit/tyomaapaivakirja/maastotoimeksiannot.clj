(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.maastotoimeksiannot
  "Työmaapäiväkirja -näkymän kalusto ja tien toimenpiteet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- maatoimeksianto-rivi [nimi aika]
  (rivi
    [:varillinen-teksti {:arvo nimi}]
    [:varillinen-teksti {:arvo aika}]))

(defn maastotoimeksiannot-taulukko []
  (let [tiedot {:rivin-tiedot (rivi
                                {:otsikko "Nimi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Käytetty aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.1 :tyyppi :varillinen-teksti})
                :rivit (into []
                         [(maatoimeksianto-rivi "3 kpl liittymätarkastelua maantiellä 4121" "5,0 h")
                          (maatoimeksianto-rivi "Kaapelitarkastukset Tiekonsultti Oy:n kanssa teillä 4,8,12" "5,0 h")
                          (maatoimeksianto-rivi "Siirtoajoneuvo siirto paikasta 4/200/300 kunnan xxx varastoon" "5,0 h")])}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/sektio-otsikko "Viranomaispäätöksiin liittyvät maastotoimeksiannot")])))
