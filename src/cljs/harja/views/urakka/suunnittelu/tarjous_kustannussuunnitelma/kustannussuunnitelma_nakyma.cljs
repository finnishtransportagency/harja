(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-nakyma
  "Uusi kustannusten suunnittelu"
  (:require [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]))


(defn kilpailutettavat-hankinnat [e! app]
  [:div.row.kustannussuunnitelma-osio
   [:div.col-xs-12
    [:h2 "Kilpailutettavat hankinnat"]]])

(defn rahavaraukset [e! app]
  [:div.row.kustannussuunnitelma-osio
   [:div.col-xs-12
    [:h2 "Rahavaraukset"]]])

(defn nakyma* [e! {:keys [tallennus-kesken? valittu-hoitokausi] :as app}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        urakan-kesto-vuosina (- urakan-loppuvuosi urakan-alkuvuosi)
        hoitokaudet (into [] (range urakan-alkuvuosi (+ urakan-kesto-vuosina urakan-alkuvuosi)))]
    [:div
     [:div.row
      [:div.col-xs-12.col-md-6
       [:h1 "Hoitovuoden alun tavoitehinta"]
       [:div (-> @tila/yleiset :urakka :nimi)]]
      [:div.col-xs-12.col-md-6
       [yleiset/linkki "Muokkaa tarjouksen tietoja"
        #(siirtymat/siirry-annettuun-valilehteen @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
           {:taso1 :urakat :taso2 :suunnittelu :taso3 :tarjous})
        {:style {:float "right"
                 :line-height "2rem"}}]]]
     [:div.row {:style {:margin-top "1rem"}}
      [:div.col-xs-12.col-md-3
       [:span.caption-small-strong.alasveto-label "Hoitovuosi"]
       [yleiset/livi-pudotusvalikko {:valinta (pvm/vuosi (first valittu-hoitokausi))
                                     :vayla-tyyli? true
                                     :disabled tallennus-kesken?
                                     :data-cy "hoitokausi-valinta"
                                     :valitse-fn #(e! (kust-tiedot/->ValitseHoitokausiKustannussuunnitelmaan %))
                                     :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitokaudet "Hoitovuosi")
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        hoitokaudet]]]
     [kilpailutettavat-hankinnat e! app]
     [rahavaraukset e! app]]))

(defn kustannussuunitelma []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
