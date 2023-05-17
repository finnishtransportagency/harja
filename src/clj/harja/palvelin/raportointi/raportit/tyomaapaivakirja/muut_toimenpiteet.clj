(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-toimenpiteet
  "Työmaapäiväkirja -näkymän muut toimenpiteet taulukko"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- muut-toimenpiteet-rivi [aikavali toimenpide]
  (rivi
    [:varillinen-teksti {:arvo aikavali}]
    [:varillinen-teksti {:arvo toimenpide}]))

(defn muut-toimenpiteet-taulukko []
  (let [tiedot {:rivin-tiedot (rivi
                                {:otsikko "Aikaväli" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.0225 :tyyppi :varillinen-teksti}
                                {:otsikko "Toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.3 :tyyppi :varillinen-teksti})
                :rivit (into []
                         [(muut-toimenpiteet-rivi "00:00" "-")
                          (muut-toimenpiteet-rivi "01:00" "-")
                          (muut-toimenpiteet-rivi "02:00" "Liikennemerkin vaihto maantiellä 4121 (08:15 - 09:00)")
                          (muut-toimenpiteet-rivi "03:00" "-")
                          (muut-toimenpiteet-rivi "04:00" "-")])}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/osion-otsikko "Muut toimenpiteet")])))
