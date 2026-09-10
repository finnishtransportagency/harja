(ns harja.palvelin.raportointi.raportit.valikatselmus
  "Valikatselmuksen PDF-raportti"
  (:require [harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf :as valikatselmus-pdf]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm kasittelija] :as parametrit}]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitovuosi (pvm/vuosi alkupvm)
        hoitovuoden-tiedot (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle
                             db user {:urakkaid urakka-id :hoitovuosi hoitovuosi})
        data (valikatselmus-pdf/yhteenveto-rivit hoitovuoden-tiedot urakan-tiedot)
        _ (println "data:" (pr-str data))

        raportin-otsikko "Välikatselmus"
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        muodosta-taulukkorivi (fn [[otsikko arvo lihavoitu?]]
                                (when-not (= :vaakaviiva otsikko)
                                  {:lihavoi? lihavoitu?
                                   :rivi [otsikko arvo]}))
        _ (println "(map #(muodosta-taulukkorivi %) (:tavoitehinta data))" (mapv #(muodosta-taulukkorivi %) (:tavoitehinta data)))
        _ (println "(keep #(muodosta-taulukkorivi %) (:kustannukset data))" (keep #(muodosta-taulukkorivi %) (:kustannukset data)))
        ]
    (into [:raportti {:nimi raportin-otsikko
                      :orientaatio :portrait
                      :urakan-nimi (:nimi urakan-tiedot)
                      :aikajakso aikajakso
                      :otsikon-koko :iso
                      :raportin-yleiset-tiedot {:raportin-nimi raportin-otsikko}
                      :alkupvm alkupvm
                      :loppupvm loppupvm}
           [:jakaja nil]]

      (concat
        [[:otsikko-heading "Hoitovuoden lopun tavoite- ja kattohinta"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:tavoitehinta data)))]]

        [[:otsikko-heading "Tavoitehintaan kuuluvat toteutuneet kustannukset"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:kustannukset data)))]]

        ;; Jos tavoitehinta on alitettu, ylitetty jne, niin kootaan ne tähän.
        (when (:tavoitehinnan-ylitys data)
          [[:otsikko-heading "Tavoitehinnan ylitys"]
           [:taulukko {}
            [{:leveys 10 :otsikko ""}
             {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
            (into [] (keep #(muodosta-taulukkorivi %) (:tavoitehinnan-ylitys data)))]])

        (when (:tavoitehinnan-alitus data)
          [[:otsikko-heading "Tavoitehinnan alitus"]
           [:taulukko {}
            [{:leveys 10 :otsikko ""}
             {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
            (into [] (keep #(muodosta-taulukkorivi %) (:tavoitehinnan-alitus data)))]])

        (when (:kattohinnan-ylitys data)
          [[:otsikko-heading "Kattohinnan ylitys"]
           [:taulukko {}
            [{:leveys 10 :otsikko ""}
             {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
            (into [] (keep #(muodosta-taulukkorivi %) (:kattohinnan-ylitys data)))]])

        [[:otsikko-heading "Bonukset"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:bonukset data)))]]

        [[:otsikko-heading "Sanktiot"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:sanktiot data)))]]

        [[:otsikko-heading "Hoidonjohtopalkkion muutos"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:hoidonjohtopalkkio data)))]]))))
