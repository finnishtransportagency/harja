(ns harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot
  "Tiemerkintöjen sakot ja bonukset - tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :muokataan false
                     :valittu-rivi nil
                     :haku-kaynnissa? false
                     :valinnat {:aikavali (pvm/kuukauden-aikavali (pvm/nyt))}}))

(def nakymassa? (atom false))


;; Tuck 
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaModal [rivi])
(defrecord MuokkaaRivia [rivi])


(defn hae-tiedot
    ;; TODO 
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-tiemerkinta-muut-kustannukset
    {:aikavali (:aikavali valinnat)
     :urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :rivit vastaus
      :haku-kaynnissa? false))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  MuokkaaRivia
  (process-event [{rivi :rivi} app]
    (update app :valittu-rivi merge rivi))

  AvaaModal
  (process-event [{rivi :rivi} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi))))
