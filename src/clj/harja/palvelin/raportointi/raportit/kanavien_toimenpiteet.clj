(ns harja.palvelin.raportointi.raportit.kanavien-toimenpiteet
  "Kokonaishintaiset toimenpiteet raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi raportin-otsikko]]))


(defn suorita [db _ {:keys [rivit parametrit]}]

  (let [{:keys [aikavali urakka]} parametrit
        [alkupvm loppupvm] aikavali
        otsikko "Kokonaishintaiset toimenpiteet"
        urakka-id (:id urakka)
        lyhytnimet (yleinen/hae-urakan-lyhytnimet db :vesivayla-kanavien-hoito urakka-id)
        raportin-otsikko (raportin-otsikko lyhytnimet otsikko alkupvm loppupvm)
        sarakkeet (rivi
                    {:otsikko "Pvm" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.5 :tyyppi :varillinen-teksti}
                    {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Huoltokohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Tehtävä" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.45 :tyyppi :varillinen-teksti}
                    {:otsikko "Muu toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Lisätieto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Suorittaja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.5 :tyyppi :varillinen-teksti}
                    {:otsikko "Kuittaaja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti})]

    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko
                :piilota-otsikko? true}

     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi otsikko}
      sarakkeet
      rivit]]))
