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
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.modal :as modal]
            [harja.ui.lomake :as lomake]
            [cljs-time.core :as t]
            [harja.ui.napit :as napit])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))



(defn valmis-tiemerkintaan [kohde-id urakka-id]
  (let [valmis-tiemerkintaan (atom nil)]
    (fn [kohde-id urakka-id]
      [:button.nappi-ensisijainen.nappi-grid
       {:type "button"
        :on-click
        (fn []
          (modal/nayta!
            {:otsikko "Merkintäänkö kohde valmiiksi tiemerkintään?"
             :luokka "merkitse-valmiiksi-tiemerkintaan"
             :footer [:span
                      [:button.nappi-toissijainen
                       {:type "button"
                        :on-click #(do (.preventDefault %)
                                       (modal/piilota!))}
                       "Peruuta"]
                      [napit/palvelinkutsu-nappi
                       "Merkitse"
                       #(do (log "[AIKATAULU] Merkitään kohde valmiiksi tiemerkintää")
                            (tiedot/merkitse-kohde-valmiiksi-tiemerkintaan
                              kohde-id
                              (:valmis-tiemerkintaan @valmis-tiemerkintaan)
                              urakka-id
                              (first @u/valittu-sopimusnumero)))
                       {;:disabled (not (some? @valmis-tiemerkintaan)) ; FIXME Ei päivity jos arvo muuttuu
                        :luokka "nappi-myonteinen"
                        :kun-onnistuu (fn [vastaus]
                                        (modal/piilota!)
                                        (log "[AIKATAULU] Kohde merkitty valmiiksi tiemerkintää")
                                        (reset! tiedot/aikataulurivit vastaus))}]]}
            [:div
             [:p "Haluatko varmasti merkitä kohteen valmiiksi tiemerkintään? Toimintoa ei voi perua."]
             [lomake/lomake {:otsikko ""
                             :muokkaa! (fn [uusi-data]
                                         (reset! valmis-tiemerkintaan uusi-data))}
              [{:otsikko "Tiemerkinnän saa aloittaa"
                :nimi :valmis-tiemerkintaan
                :pakollinen? true
                :tyyppi :pvm}]
              @valmis-tiemerkintaan]]))}
       "Aseta päivämäärä"])))

(defn aikataulu
  [urakka optiot]
  (komp/luo
    (komp/lippu tiedot/aikataulu-nakymassa?)
    (fn [urakka optiot]
      (let [ur @nav/valittu-urakka
            urakka-id (:id ur)
            sopimus-id (first @u/valittu-sopimusnumero)
            paallystysurakoitsijana? #(oikeudet/voi-kirjoittaa? oikeudet/urakat-aikataulu
                                                                urakka-id)
            tiemerkintaurakoitsijana? #(oikeudet/urakat-aikataulu urakka-id "TM-valmis")
            voi-tallentaa? (or paallystysurakoitsijana? tiemerkintaurakoitsijana?)]
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
          [{:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :string
            :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "Koh\u00ADteen nimi" :leveys 7 :nimi :nimi :tyyppi :string :pituus-max 128
            :muokattava? (constantly false)}
           {:otsikko "TR-osoite" :leveys 10 :nimi :tr-osoite :tyyppi :string
            :muokattava? (constantly false) :hae tr-domain/tierekisteriosoite-tekstina}
           ; FIXME Tallennus epäonnistuu jos kellonaikaa ei anna
           {:otsikko "Pääl\u00ADlys\u00ADtys a\u00ADloi\u00ADtet\u00ADtu" :leveys 10 :nimi :aikataulu-paallystys-alku
            :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt
            :muokattava? #(and (= (:nakyma optiot) :paallystys) paallystysurakoitsijana?)}
           {:otsikko "Pääl\u00ADlys\u00ADtys val\u00ADmis" :leveys 10 :nimi :aikataulu-paallystys-loppu
            :tyyppi :pvm-aika :fmt pvm/pvm-aika-opt
            :muokattava? #(and (= (:nakyma optiot) :paallystys) paallystysurakoitsijana?)}
           (when (= (:nakyma optiot) :paallystys)
             {:otsikko "Tie\u00ADmer\u00ADkin\u00ADnän suo\u00ADrit\u00ADta\u00ADva u\u00ADrak\u00ADka"
              :leveys 13 :nimi :suorittava-tiemerkintaurakka
              :tyyppi :valinta
              :fmt (fn [arvo]
                     (:nimi (some
                              #(when (= (:id %) arvo) %)
                              @tiedot/tiemerkinnan-suorittavat-urakat)))
              :valinta-arvo :id
              :valinta-nayta #(if % (:nimi %) "- Valitse urakka -")
              :valinnat @tiedot/tiemerkinnan-suorittavat-urakat
              :nayta-ryhmat [:sama-hallintayksikko :eri-hallintayksikko]
              :ryhmittely #(if (= (:hallintayksikko %) (:id (:hallintayksikko urakka)))
                            :sama-hallintayksikko
                            :eri-hallintayksikko)
              :ryhman-otsikko #(case %
                                :sama-hallintayksikko "Hallintayksikön tiemerkintäurakat"
                                :eri-hallintayksikko "Muut tiemerkintäurakat")
              :muokattava? paallystysurakoitsijana?})
           {:otsikko "Val\u00ADmis tie\u00ADmerkin\u00ADtään" :leveys 10
            :nimi :valmis-tiemerkintaan :tyyppi :komponentti :muokattava? paallystysurakoitsijana?
            :komponentti (fn [rivi]
                           (if (:valmis-tiemerkintaan rivi)
                             [:span (pvm/pvm-aika-opt (:valmis-tiemerkintaan rivi))]
                             (if (= (:nakyma optiot) :paallystys)
                               [valmis-tiemerkintaan (:id rivi) urakka-id]
                               [:span "Ei"])))}
           {:otsikko "Tie\u00ADmer\u00ADkin\u00ADtä a\u00ADloi\u00ADtet\u00ADtu"
            :leveys 6 :nimi :aikataulu-tiemerkinta-alku :tyyppi :pvm
            :fmt pvm/pvm-opt :muokattava? (fn [rivi]
                                            (and (= (:nakyma optiot) :tiemerkinta)
                                                 tiemerkintaurakoitsijana?
                                                 (:valmis-tiemerkintaan rivi)))}
           {:otsikko "Tie\u00ADmer\u00ADkin\u00ADtä val\u00ADmis"
            :leveys 6 :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
            :fmt pvm/pvm-opt :muokattava? (fn [rivi]
                                            (and (= (:nakyma optiot) :tiemerkinta)
                                                 tiemerkintaurakoitsijana?
                                                 (:valmis-tiemerkintaan rivi)))}
           {:otsikko "Koh\u00ADde val\u00ADmis" :leveys 6 :nimi :aikataulu-kohde-valmis :tyyppi :pvm
            :fmt pvm/pvm-opt
            :muokattava? #(and (= (:nakyma optiot) :paallystys) paallystysurakoitsijana?)}]
          (sort-by tr-domain/tiekohteiden-jarjestys @tiedot/aikataulurivit)]]))))
