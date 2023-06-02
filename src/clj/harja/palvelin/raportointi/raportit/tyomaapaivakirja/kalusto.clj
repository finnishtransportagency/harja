(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.kalusto
  "Työmaapäiväkirja -näkymän kalusto ja tien toimenpiteet"
  (:require
    [clojure.string :as str]
    [harja.pvm :as pvm]
    [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- kalusto-rivi [aikavali peruskalusto lisakalusto toimenpide]
  (rivi
    [:varillinen-teksti {:arvo aikavali}]
    [:varillinen-teksti {:arvo peruskalusto}]
    [:varillinen-teksti {:arvo lisakalusto}]
    [:varillinen-teksti {:arvo toimenpide}]))

(defn kalusto-taulukko [kalustot]
  (let [tiedot {:oikealle-tasattavat #{1 2}
                :rivin-tiedot (rivi
                                {:otsikko "Aikaväli" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.14 :tyyppi :varillinen-teksti}
                                {:otsikko "Peruskalusto (KA/TR)" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.25 :tyyppi :varillinen-teksti}
                                {:otsikko "Lisäkalusto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.25 :tyyppi :varillinen-teksti}
                                {:otsikko "Toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.7 :tyyppi :varillinen-teksti})
                :rivit (mapv
                         #(kalusto-rivi (pvm/kellonaikavali (:aloitus %) (:lopetus %))
                            (:tyokoneiden_lkm %) (or (:lisakaluston_lkm %) "-") (str/join ", " (:tehtavat %))
                            #_"Miten saadaan tällainen mäppäys: Pistehiekoitus (01:23 - 02:15), Suolaus (00:22 - 01:12)")
                         kalustot)}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/osion-otsikko "Kalusto ja tielle tehdyt toimenpiteet")])))
