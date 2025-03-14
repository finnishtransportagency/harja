(ns harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(defonce kustannusten-kirjaus-valilehti-nakyvissa? (atom false))

(defn kustannusten-summa [rivit avain]
  (let [summa (reduce + 0 (map avain rivit))]
    summa))

(defn pk-osuus-totaalista [rivit avain]
  (reduce + (map (fn [rivi]
                   (* (:kustannus rivi) (/ (avain rivi) 100)))
              rivit)))

(defn prosenttiosuus-kustannuksesta
  [kustannus p-osuus]
  (* (/ p-osuus 100) kustannus))

(defrecord HaeKustannukset [urakka])
(defrecord HaeKustannuksetOnnistui [vastaus])
(defrecord HaeKustannuksetEpaonnistui [vastaus])

(defrecord TallennaKustannukset [tiedot urakka])
(defrecord TallennaKustannuksetOnnistui [vastaus app])
(defrecord TallennaKustannuksetEpaonnistui [vastaus])

(extend-protocol tuck/Event
  HaeKustannukset
  (process-event [urakka app]
    (tuck-apurit/post! :hae-tiemerkinta-kustannuskirjaus
      {:urakka (get urakka :urakka)}
      {:onnistui ->HaeKustannuksetOnnistui
       :epaonnistui ->HaeKustannuksetEpaonnistui})
    (assoc app :haku-kaynnissa? true))

  HaeKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :haku-kaynnissa? false
      :kustannukset vastaus))

  HaeKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKustannusEpaonnistui" (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeKustannuksetEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    (assoc app
      :kustannukset []
      :haku-kaynnissa? false))

  TallennaKustannukset
  (process-event [{tiedot :tiedot urakka :urakka} app]
    (let [payload {:urakka urakka :tiedot (into []
                                            (map (fn [m]
                                                   (dissoc m :id))
                                              tiedot))}]
      (tuck-apurit/post! :tallenna-tiemerkinta-kustannuskirjaus
        payload
        {:onnistui ->TallennaKustannuksetOnnistui
         :epaonnistui ->TallennaKustannuksetEpaonnistui})))

  TallennaKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    ((tuck/current-send-function) (->HaeKustannukset (:urakka vastaus)))
    (assoc app :kustannukset (:tiedot vastaus) :tallennus-kaynnissa? false :tallennus-onnistui? true))

  TallennaKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKustannuksetEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kustannuksen tallennuksessa tapahtui virhe" :varoitus)
    app))
