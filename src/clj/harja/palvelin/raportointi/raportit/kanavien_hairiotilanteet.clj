(ns harja.palvelin.raportointi.raportit.kanavien-hairiotilanteet
  "Häiriötilanne raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi raportin-otsikko]]
   [harja.domain.kanavat.raportointi :as k-raportointi]
   [harja.kyselyt.urakat :as urakat-q]))


(defn suorita [db _ {:keys [rivit parametrit]}]
  
  (let [{:keys [aikavali urakka]} parametrit
        otsikko "Häiriötilanteet"
        urakka-id (:id urakka)
        alkupvm (first aikavali)
        loppupvm (second aikavali)
        lyhytnimet (urakat-q/hae-urakoiden-nimet db {:urakkatyyppi "vesivayla-kanavien-hoito" :vain-puuttuvat false :urakantila "kaikki"})
        ;; Käyttää lyhytnimeä jos olemassa, jos ei -> urakan koko nimi
        urakan-nimi (k-raportointi/suodata-urakat lyhytnimet  #{urakka-id})
        urakan-nimi (k-raportointi/kokoa-lyhytnimet urakan-nimi)
        raportin-otsikko (raportin-otsikko urakan-nimi otsikko alkupvm loppupvm)
        ;; Sarakkeet normaalisti passataan tähän gridin mukana, mutta koska sarakkeiden otsikon ovat niin pitkiä
        ;; niitä pakko vähän muotoilla, PDF rapsasta tulee muuten ihan mössöä
        sarakkeet (rivi
                    {:otsikko "Havainto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.75 :tyyppi :varillinen-teksti}
                    {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Luokka" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.9 :tyyppi :varillinen-teksti}
                    {:otsikko "Syy" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.9 :tyyppi :varillinen-teksti}
                    {:otsikko "Vesi odotus (h)" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.5 :tyyppi :varillinen-teksti}
                    {:otsikko "Ammatti" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Huvi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti}
                    {:otsikko "Tie odotus (h)" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.5 :tyyppi :varillinen-teksti}
                    {:otsikko "Ajoneuvot" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.7 :tyyppi :varillinen-teksti}
                    {:otsikko "Toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                    {:otsikko "Korjaus aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Tila" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Paikallinen" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti})]
    
    [:raportti {:nimi raportin-otsikko
                :piilota-otsikko? true}

     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi otsikko}
      sarakkeet
      rivit]]))
