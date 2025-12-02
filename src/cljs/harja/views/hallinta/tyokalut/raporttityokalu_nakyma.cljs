(ns harja.views.hallinta.tyokalut.raporttityokalu-nakyma
  "Työkaluja kaikkien raporttien korjaamiseen."
  (:require [harja.ui.debug :as debug]
            [reagent.core :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.hallinta.tyokalut.raporttityokalu-tiedot :as raporttityokalu-tiedot]))

(defn raporttityokalut-listattuna [e! app]
  [:div
   [:div.row
    [:div.col-xs-12
     [:h3 "Päivitä materiaalicachet urakalle"]]]
   [:div.row
    [:div.col-xs-12
     [:p "Suolatoteumia raportoitaessa API:n kautta on mahdollista, että Harjan raakadata ja raporttien näyttämät tiedot eroavat toisistaan.
     Tämä johtuu siitä, että Harjan raportit käyttävät materiaalicachea nopeuttamaan raporttien latautumista. Voit korjata materiaalicachet päivittämällä ne urakalle alla olevalla työkalulla."]
     [:p "Päivitä sopimuksen materiaalicachet valitulle urakalle. Valitse ajanjaksoksi tuotannossa maksimissaan
   kuukausi kerrallaan, jotta päivitys ei kuormita järjestelmää liikaa. Lokaalisti ei ole olemassa rajoituksia."]]]
   [:div.row
   (if (:paivita-materiaalicache-kaynnissa app)
     [:div.col-xs-12 "Ajo käynnissä"]
     [:div [:div.col-xs-12.col-md-1
            [kentat/tee-kentta
             {:vayla-tyyli? true
              :elementin-id (gensym)
              :placeholder "Urakkaid"
              :tyyppi :positiivinen-numero}
             (r/wrap (:tie app)
               #(e! (raporttityokalu-tiedot/->AsetaUrakkaId %)))]]
      ;; Lisää kentät alkupvm ja loppupvm
      [:div.col-xs-12.col-md-1
       [kentat/tee-kentta
        {:vayla-tyyli? true
         :elementin-id (gensym)
         :placeholder "Alkupäivä"
         :tyyppi :pvm}
        (r/wrap (:alkupvm app)
          #(e! (raporttityokalu-tiedot/->AsetaAlkupvm %)))]]
      [:div.col-xs-12.col-md-1
       [kentat/tee-kentta
        {:vayla-tyyli? true
         :elementin-id (gensym)
         :placeholder "Loppupäivä"
         :tyyppi :pvm}
        (r/wrap (:loppupvm app)
          #(e! (raporttityokalu-tiedot/->AsetaLoppupvm %)))]]

      [napit/tallenna "Päivitä"
       #(e! (raporttityokalu-tiedot/->PaivitaMateriaalicachetUrakalle))]])]])

(defn nayta-raporttityokalut* [e! app]
  (if
    (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
    [:div
     [:h2 "Erilaisia työkaluja raporttien korjaamiseen"]
     [raporttityokalut-listattuna e! app]
     [debug/debug app]]
    [:div "Ei oikeuksia"]))

(defn nayta-raporttityokalut []
  [tuck raporttityokalu-tiedot/tila nayta-raporttityokalut*])
