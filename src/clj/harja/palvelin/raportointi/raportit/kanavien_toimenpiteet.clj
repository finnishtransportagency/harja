(ns harja.palvelin.raportointi.raportit.kanavien-toimenpiteet
  "Kokonaishintaiset toimenpiteet raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi raportin-otsikko]]
   [harja.domain.kanavat.raportointi :as k-raportointi]
   [harja.kyselyt.urakat :as urakat-q]))


(defn suorita [db _ {:keys [rivit parametrit]}]

  (let [{:keys [aikavali urakka]} parametrit
        otsikko "Kokonaishintaiset toimenpiteet"
        urakka-id (:id urakka)
        alkupvm (first aikavali)
        loppupvm (second aikavali)
        lyhytnimet (urakat-q/hae-urakoiden-nimet db {:urakkatyyppi "vesivayla-kanavien-hoito" :vain-puuttuvat false :urakantila "kaikki"})
        ;; Käyttää lyhytnimeä jos olemassa, jos ei -> urakan koko nimi
        urakan-nimi (k-raportointi/suodata-urakat lyhytnimet  #{urakka-id})
        urakan-nimi (k-raportointi/kokoa-lyhytnimet urakan-nimi)
        raportin-otsikko (raportin-otsikko urakan-nimi otsikko alkupvm loppupvm)
        sarakkeet (rivi
                    {:otsikko "Pvm" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.5 :tyyppi :varillinen-teksti}
                    {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Huoltokohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Tehtävä" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.45 :tyyppi :varillinen-teksti}
                    {:otsikko "Muu toimenpide" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                    {:otsikko "Lisätieto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                    {:otsikko "Suorittaja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.5 :tyyppi :varillinen-teksti}
                    {:otsikko "Kuittaaja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti})]

    [:raportti {:nimi raportin-otsikko
                :piilota-otsikko? true}

     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? rivit) "Ei raportoitavaa.")
                 :sheet-nimi otsikko}
      sarakkeet
      rivit]]))
