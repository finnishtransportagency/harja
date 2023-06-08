(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.maastotoimeksiannot
  "Työmaapäiväkirja -näkymän kalusto ja tien toimenpiteet"
  (:require
    [clojure.string :as str]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- maatoimeksianto-rivi [nimi aika]
  (rivi
    [:varillinen-teksti {:arvo nimi}]
    [:varillinen-teksti {:arvo aika}]))

(defn maastotoimeksiannot-taulukko [toimeksiannot]
  (let [toimeksiantorivit (mapv
                            #(maatoimeksianto-rivi
                               (:kuvaus %)
                               ;; Muutetaan piste pilkuksi
                               (str/replace (str (:aika %) " h") "." ","))
                            toimeksiannot)
        tiedot {:rivin-tiedot (rivi
                                {:otsikko "Nimi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Käytetty aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.1 :tyyppi :varillinen-teksti})
                :rivit toimeksiantorivit}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/osion-otsikko "Viranomaispäätöksiin liittyvät maastotoimeksiannot")])))
