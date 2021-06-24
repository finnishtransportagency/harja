(ns harja.tiedot.urakka.lupaukset
  "Urakan lupausten tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord HaeUrakanLupaustiedot [urakka-id])
(defrecord HaeUrakanLupaustiedotOnnnistui [vastaus])
(defrecord HaeUrakanLupaustiedotEpaonnistui [vastaus])
(defrecord NakymastaPoistuttiin [])

(extend-protocol tuck/Event

  HaeUrakanLupaustiedot
  (process-event [{urakka-id :urakka-id} app]
    (let [parametrit {:urakka-id urakka-id}]
      (-> app
          (tuck-apurit/post! :hae-urakan-lupaustiedot
                             parametrit
                             {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                              :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui})
          (assoc :kiintioiden-haku-kaynnissa? true)
          (dissoc :paallystysilmoitukset))))
  HaeUrakanLupaustiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotOnnnistui " vastaus)
    app)
  HaeUrakanLupaustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotEpaonnistui " vastaus)
    app)

  NakymastaPoistuttiin
  (process-event [_ app]
    (println "NakymastaPoistuttiin ")
    app))