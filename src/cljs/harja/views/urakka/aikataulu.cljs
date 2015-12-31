(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.aikataulu :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [cljs.core.async :refer [<!]]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


(defn aikataulu
  []
  (komp/luo
    (komp/lippu tiedot/aikataulu-nakymassa?)
      (fn []
        (let [ur @nav/valittu-urakka
              urakka-id (:id ur)
              sopimus-id @u/valittu-sopimusnumero
              aikataulut @tiedot/aikataulurivit
              paallystysurakoitsijana? #(roolit/rooli-urakassa? roolit/paallystysaikataulun-kirjaus urakka-id)
              tiemerkintaurakoitsijana? #(roolit/rooli-urakassa? roolit/urakan-tiemerkitsija urakka-id)]
          (log "aikataulut: " (pr-str aikataulut))
          [:div.aikataulu
          [grid/grid
           {:otsikko      "Kohteiden aikataulu"
            :voi-poistaa? (constantly false)
            :piilota-toiminnot? true
            :tallenna     (roolit/jos-rooli-urakassa roolit/paallystysaikataulun-kirjaus
                                                     urakka-id
                                                     #(tiedot/tallenna-paallystyskohteiden-aikataulu urakka-id
                                                                                                     sopimus-id
                                                                                                     %)
                                                     :ei-mahdollinen)}

           [{:otsikko "Kohde\u00AD ID" :leveys "5%" :nimi :kohdenumero :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}
            {:otsikko "Kohteen nimi" :leveys "10%" :nimi :nimi :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}

            {:otsikko "TR-osoite" :leveys "10%" :nimi :tr-osoite :tyyppi :string :muokattava? (constantly false)}
            {:otsikko "Pääll. aloitus\u00AD" :leveys "8%" :nimi :aikataulu_paallystys_alku :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt
             :muokattava? paallystysurakoitsijana?}
            {:otsikko "Pääll. valmis" :leveys "8%" :nimi :aikataulu_paallystys_loppu :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt :muokattava? paallystysurakoitsijana?}
            {:otsikko     "Valmis tie\u00ADmerkin\u00ADtään" :leveys "7%" :nimi :valmis_tiemerkintaan :tyyppi :komponentti :muokattava? paallystysurakoitsijana?
             :komponentti (fn [rivi]
                            (if (not (:valmis_tiemerkintaan rivi))
                              [:button.nappi-ensisijainen.nappi-grid
                               {:type     "button"
                                :on-click #(log "Painettu")} "Valmis"]
                              [:span (pvm/pvm-aika-opt (:valmis_tiemerkintaan rivi))]))}
            {:otsikko "TM valmis" :leveys "8%" :nimi :aikataulu_tiemerkinta_loppu :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? tiemerkintaurakoitsijana?}
            {:otsikko "Kohde valmis" :leveys "7%" :nimi :kohde-valmis :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? paallystysurakoitsijana?}
            #_{:otsikko     "Toiminnot" :nimi :toiminnot :tyyppi :komponentti
             :komponentti (fn [rivi]
                            (let [valmis-tiemerkintaan? (= "Kyllä" (:valmis-tiemerkintaan rivi))]
                              [:button.nappi-ensisijainen.nappi-grid
                               {:class    (str "nappi-ensisijainen " (if valmis-tiemerkintaan? "disabled"))
                                :type     "button"
                                :on-click #(log "Painettu")} "Valmis tiemerkintään"]))
             :leveys      "14%"}]
           @tiedot/aikataulurivit
           ;; FIXME Alla hardcoodattu testidata. Poistetaan kun kannasta haku toimii
           #_[{:kohdenumero          1 :nimi "Mt 4 Ylä-Laakkola" :tr-osoite "4/1/100/4/534" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
               :paall-aloitusaika    "10.6.2015" :paall-valmistumisaika "5.7.2015" :tm-alkuaika "6.7.2015" :tm-valmistumisaika "14.7.2015"
               :valmis-tiemerkintaan "Kyllä" :kohde-valmis "Ei"}
              {:kohdenumero          2 :nimi "Mt 4 Ala-Laakkola" :tr-osoite "4/4/534/6/134" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
               :paall-aloitusaika    "11.6.2015" :paall-valmistumisaika "6.7.2015" :tm-alkuaika "7.7.2015" :tm-valmistumisaika "14.7.2015"
               :valmis-tiemerkintaan "Ei" :kohde-valmis "Ei"}
              {:kohdenumero          3 :nimi "Mt 4 Päkylä" :tr-osoite "4/6/134/7/1534" :kvl 123 :toimenpide "MPKJ" :yp-lk "1"
               :paall-aloitusaika    "12.6.2015" :paall-valmistumisaika "7.7.2015" :tm-alkuaika "8.7.2015" :tm-valmistumisaika "14.7.2015"
               :valmis-tiemerkintaan "Ei" :kohde-valmis "Ei"}
              ]]]))))