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
(defrecord AvaaKustannusModal [rivi])
(defrecord HaeTyypit [])
(defrecord HaeTyypitOnnistui [vastaus])
(defrecord HaeTyypitEpaonnistui [vastaus])
(defrecord MuokkaaRivia [rivi])


(defn hae-muut-kustannukset
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-tiemerkinta-muut-kustannukset
    {:aikavali (:aikavali valinnat)
     :urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn- hae-kustannustyypit [app]
  (tuck-apurit/post! app :hae-tiemerkinta-kustannustyypit
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTyypitOnnistui
     :epaonnistui ->HaeTyypitEpaonnistui}))


(defn voi-tallentaa?
  ""
  [{:keys [kustannus] :as valittu-rivi}]
  (let [] false))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-muut-kustannukset app)
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
  
  HaeTyypit
  (process-event [_ app]
    (hae-kustannustyypit app)
    (assoc app :haku-kaynnissa? true))
  
  HaeTyypitOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :tyypit vastaus
      :haku-kaynnissa? false))
  
  HaeTyypitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (println "epa: " vastaus)
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))
  
  MuokkaaRivia
  (process-event [{rivi :rivi} app]
    (update app :valittu-rivi merge rivi))

  AvaaKustannusModal
  (process-event [{rivi :rivi} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi))))
