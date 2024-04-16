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


(defonce tila (atom {:rivit nil
                     :lomake-valinnat nil
                     :kustannukset-yhteensa nil 
                     :muokataan false
                     :haku-kaynnissa? false}))

(def nakymassa? (atom false))
(def kustannusten-tyypit #{"Arvonmuutokset" "Indeksi- ja kustannustason muutokset" "Muut kustannukset"}) 


;; Tuck 
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaLomake [])
(defrecord SuljeLomake [])
(defrecord MuokkaaLomaketta [rivi])


(defn- hae-paikkaus-kustannukset [app]
  (let [aikavali (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi)
        alkuaika (when (some? aikavali) (first aikavali))
        loppuaika (when (some? aikavali) (second aikavali))]

    (tuck-apurit/post! app :hae-paikkaus-kustannukset
      {:aikavali aikavali
       :urakka-id @nav/valittu-urakka-id}
      {:onnistui ->HaeTiedotOnnistui
       :epaonnistui ->HaeTiedotEpaonnistui})))


(extend-protocol tuck/Event

  HaeTiedot
  (process-event [_ app]
    (println "HaeTiedot, interval: " (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi))
    (hae-paikkaus-kustannukset app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [;; Laske kaikki kustannukset yhteen
          kustannukset (reduce + (map (fn [rivi] (or (:kokonaiskustannus rivi) 0)) vastaus))
          ]
      (println "\n V: " (vec vastaus))
      (assoc app
        :rivit (vec vastaus)
        :kustannukset-yhteensa kustannukset
        :haku-kaynnissa? false))
)

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
    (assoc app :muokataan false))
  
  MuokkaaLomaketta
  (process-event [{rivi :rivi} app]
    (update app :lomake-valinnat merge rivi)))
