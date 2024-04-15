(ns harja.tiedot.urakka.mpu-kustannukset
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defonce tila (atom {}))
(def nakymassa? (atom false))


;; Tuck 
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaLomake [])
(defrecord SuljeLomake [])


(extend-protocol tuck/Event

  HaeTiedot
  (process-event [_ app]
    (println "HaeTiedot")
    (tuck-apurit/post! app :hae-reikapaikkaus-kustannukset
      {:vuosi @urakka/valittu-urakan-vuosi
       :urakka-id @nav/valittu-urakka-id}
      {:onnistui ->HaeTiedotOnnistui
       :epaonnistui ->HaeTiedotEpaonnistui})
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [kustannukset (reduce + (map :kustannus vastaus))]
      (assoc app
        :rivit vastaus
        :kustannukset kustannukset
        :haku-kaynnissa? false)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  AvaaLomake
  (process-event [_ app]
    (assoc app :muokataan true))

  SuljeLomake
  (process-event [_ app]
    (assoc app :muokataan false)))
