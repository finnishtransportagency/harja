(ns harja.views.hallinta.tyokalut.ajastukset-nakyma
  "Työkalu kaikkien yöllisten ja muiden ajastusten käynnistämiseen käsin."
  (:require [harja.ui.debug :as debug]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.tyokalut.ajastukset-tiedot :as ajastukset-tiedot]))

(defn ajastukset-listattuna [e! app]
  [:div.row
   [:div "Siirrä kaikki kustannusarvioidut kustannukset toteutuneiksi kustannuksiksi:"]
   (if (:kustannusarvioidut-tyot-toteumiksi-kaynnissa app)
     [:div "Ajo käynnissä"]
     [:div [napit/tallenna "Aja!"
            #(e! (ajastukset-tiedot/->AjaKustannusarvioidutTyotToteumiksi))]]) ])

(defn nayta-ajastukset* [e! app]
  (if
    (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
    [:div
     [debug/debug app]
     [:h2 "Aja käsin erilaisia yöllisiä ja muita ajastuksia."]
     [ajastukset-listattuna e! app]]
    [:div "Ei oikeuksia"]))

(defn nayta-ajastukset []
  [tuck ajastukset-tiedot/tila nayta-ajastukset*])
