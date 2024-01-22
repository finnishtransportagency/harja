(ns harja.palvelin.raportointi.raportit.kanavien-hairiotilanteet
  "Häiriötilanne raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi raportin-otsikko]]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [harja.kyselyt.urakat :as urakat-q]
   [taoensso.timbre :as log]))


(defn suorita [db user {:keys [rivit parametrit]}]
  
  (let [{:keys [aikavali urakka]} parametrit
        otsikko "Häiriötilanteet"
        urakka-id (:id urakka)
        alkupvm (first aikavali)
        loppupvm (second aikavali)
        lyhytnimet (urakat-q/hae-urakoiden-nimet db {:urakkatyyppi "vesivayla-kanavien-hoito" :vain-puuttuvat false :urakantila "kaikki"})

        fn-suodata-urakat (fn [data idt]
                            (filter #(idt (:id %)) data))
        
        fn-kokoa-lyhytnimet (fn [data]
                              (->> data
                                (map #(or (:lyhyt_nimi %) (:nimi %)))
                                (str/join ", ")))
        
        ;; Käyttää lyhytnimeä jos olemassa, jos ei -> urakan koko nimi
        urakan-nimi (fn-suodata-urakat lyhytnimet  #{urakka-id})
        urakan-nimi (fn-kokoa-lyhytnimet urakan-nimi)
        raportin-otsikko (raportin-otsikko urakan-nimi otsikko alkupvm loppupvm)
        ;; Sarakkeet normaalisti passataan tähän gridin mukana, mutta koska sarakkeiden otsikon ovat niin pitkiä
        ;; niitä pakko vähän muotoilla, PDF rapsasta tulee muuten ihan mössöä
        sarakkeet (rivi
                    {:otsikko "Havainto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.75 :tyyppi :varillinen-teksti}
                    {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Luokka" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.9 :tyyppi :varillinen-teksti}
                    {:otsikko "Syy" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.9 :tyyppi :varillinen-teksti}
                    {:otsikko "Odotus (h)" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Ammatti lkm" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Huvi lkm" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti}
                    {:otsikko "Toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                    {:otsikko "Korjaus aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Korjaus tila" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Paikallinen" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.7 :tyyppi :varillinen-teksti})]
    
    [:raportti {:nimi raportin-otsikko
                :piilota-otsikko? true}

     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi otsikko}
      sarakkeet
      rivit]]))
