(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.kalusto
  "Työmaapäiväkirja -näkymän kalusto ja tien toimenpiteet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- kalusto-rivi [aikavali peruskalusto lisakalusto toimenpide]
  (rivi
    [:varillinen-teksti {:arvo aikavali}]
    [:varillinen-teksti {:arvo peruskalusto}]
    [:varillinen-teksti {:arvo lisakalusto}]
    [:varillinen-teksti {:arvo toimenpide}]))

(defn kalusto-taulukko []
  (let [tiedot {:oikealle-tasattavat #{1 2}
                :rivin-tiedot (rivi
                                {:otsikko "Aikaväli" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.121 :tyyppi :varillinen-teksti}
                                {:otsikko "Peruskalusto (KA/TR)" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.3 :tyyppi :varillinen-teksti}
                                {:otsikko "Lisäkalusto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.3 :tyyppi :varillinen-teksti}
                                {:otsikko "Toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti})
                :rivit (into []
                         [(kalusto-rivi "00:00" "6" "-" "Pistehiekoitus (01:23 - 02:15), Suolaus (00:22 - 01:12)")
                          (kalusto-rivi "01:00" "6" "-" "Pistehiekoitus (01:23 - 02:15), Suolaus (00:22 - 01:12)")])}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/sektio-otsikko "Kalusto ja tielle tehdyt toimenpiteet")])))
