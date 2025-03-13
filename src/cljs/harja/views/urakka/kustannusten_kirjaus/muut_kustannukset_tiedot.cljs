(ns harja.views.urakka.kustannusten-kirjaus.muut-kustannukset-tiedot
  "Tiemerkintöjen muut kustannukset välilehti - tiedot"
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


(defn hae-tietoja
  ""
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-123
    {:tr (:tr valinnat)
     :aikavali (:aikavali valinnat)
     :urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn voi-tallentaa?
  ""
  [{:keys [kustannus] :as valittu-rivi}]
  (let [] false))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    ; (hae-tietoja app)
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
    (assoc app :haku-kaynnissa? false)))
