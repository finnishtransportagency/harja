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
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn aikataulu
  []
  (komp/luo
    (komp/lippu tiedot/aikataulu-nakymassa?)
      (fn []
        (let [ur @nav/valittu-urakka
              urakka-id (:id ur)
              sopimus-id (first @u/valittu-sopimusnumero)
              aikataulut @tiedot/aikataulurivit
              paallystysurakoitsijana? #(oikeudet/voi-kirjoittaa? oikeudet/urakat-aikataulu
                                                                  urakka-id)
              tiemerkintaurakoitsijana? #(oikeudet/urakat-aikataulu urakka-id "TM-valmis")
              voi-tallentaa? (or paallystysurakoitsijana? tiemerkintaurakoitsijana?)]
          (log "aikataulut: " (pr-str aikataulut))
          [:div.aikataulu
          [grid/grid
           {:otsikko "Kohteiden aikataulu"
            :voi-poistaa? (constantly false)
            :piilota-toiminnot? true
            :tallenna (if voi-tallentaa?
                        #(tiedot/tallenna-yllapitokohteiden-aikataulu urakka-id
                                                                      sopimus-id
                                                                      %)
                        :ei-mahdollinen)}
           [{:otsikko "Kohde\u00AD ID" :leveys "5%" :nimi :kohdenumero :tyyppi :string
             :pituus-max 128 :muokattava? (constantly false)}
            {:otsikko "Kohteen nimi" :leveys "10%" :nimi :nimi :tyyppi :string :pituus-max 128
             :muokattava? (constantly false)}

            {:otsikko "TR-osoite" :leveys "10%" :nimi :tr-osoite :tyyppi :string
             :muokattava? (constantly false)}
            {:otsikko "Pääll. aloitus\u00AD" :leveys "8%" :nimi :aikataulu-paallystys-alku
             :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt
             :muokattava? paallystysurakoitsijana?}
            {:otsikko "Pääll. valmis" :leveys "8%" :nimi :aikataulu-paallystys-loppu
             :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt :muokattava? paallystysurakoitsijana?}
            {:otsikko     "Valmis tie\u00ADmerkin\u00ADtään" :leveys "7%"
             :nimi :valmis-tiemerkintaan :tyyppi :komponentti :muokattava? paallystysurakoitsijana?
             :komponentti (fn [rivi]
                            (if (not (:valmis-tiemerkintaan rivi))
                              [:button.nappi-ensisijainen.nappi-grid
                               {:type     "button"
                                :on-click #(log "Painettu")} "Valmis"]
                              [:span (pvm/pvm-aika-opt (:valmis-tiemerkintaan rivi))]))}
            {:otsikko "TM valmis" :leveys "8%" :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
             :fmt pvm/pvm-opt :muokattava? tiemerkintaurakoitsijana?}
            {:otsikko "Kohde valmis" :leveys "7%" :nimi :aikataulu-kohde-valmis :tyyppi :pvm
             :fmt pvm/pvm-opt :muokattava? paallystysurakoitsijana?}]
           @tiedot/aikataulurivit]]))))
