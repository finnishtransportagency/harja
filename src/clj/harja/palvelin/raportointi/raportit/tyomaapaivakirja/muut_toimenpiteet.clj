(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-toimenpiteet
  "Työmaapäiväkirja -näkymän muut toimenpiteet taulukko"
  (:require
    [clojure.string :as str]
    [harja.pvm :as pvm]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- muut-toimenpiteet-rivi [aikavali toimenpide]
  (rivi
    [:varillinen-teksti {:arvo aikavali}]
    [:varillinen-teksti {:arvo toimenpide}]))

(defn muut-toimenpiteet-taulukko [toimenpiteet]
  (let [rivit (mapv
                #(muut-toimenpiteet-rivi (pvm/kellonaikavali (:aloitus %) (:lopetus %)) (str/join (:toimenpiteet %)))
                toimenpiteet)
        tiedot {:rivin-tiedot (rivi
                                {:otsikko "Aikaväli" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.116 :tyyppi :varillinen-teksti}
                                {:otsikko "Toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti})
                :rivit rivit}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/osion-otsikko "Muut toimenpiteet")])))
