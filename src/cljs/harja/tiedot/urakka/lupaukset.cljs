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
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord HaeUrakanLupaustiedot [urakka-id])
(defrecord HaeUrakanLupaustiedotOnnnistui [vastaus])
(defrecord HaeUrakanLupaustiedotEpaonnistui [vastaus])

(defrecord MuokkaaLuvattujaPisteita [])
(defrecord TallennaLuvatutPisteet [pisteet])
(defrecord TallennaLuvatutPisteetOnnnistui [vastaus])
(defrecord TallennaLuvatutPisteetEpaonnistui [vastaus])

(defrecord NakymastaPoistuttiin [])

(extend-protocol tuck/Event

  HaeUrakanLupaustiedot
  (process-event [{urakka-id :urakka-id} app]
    (let [parametrit {:urakka-id urakka-id}]
      (-> app
          (tuck-apurit/post! :hae-urakan-lupaustiedot
                             parametrit
                             {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                              :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui}))))

  HaeUrakanLupaustiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotOnnnistui " vastaus)
    app)

  HaeUrakanLupaustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotEpaonnistui " vastaus)
    app)

  MuokkaaLuvattujaPisteita
  (process-event [{vastaus :vastaus} app]
    (assoc app :muokkaa-luvattuja-pisteita? true))

  TallennaLuvatutPisteet
  (process-event [{pisteet :pisteet} app]
    (let [parametrit {:pisteet pisteet}]
      (-> app
         (tuck-apurit/post! :tallenna-luvatut-pisteet
                            parametrit
                            {:onnistui ->TallennaLuvatutPisteetOnnnistui
                             :epaonnistui ->TallennaLuvatutPisteetEpaonnistui})
         :muokkaa-luvattuja-pisteita? false))
    (assoc app
               :luvatut-pisteet pisteet))

  TallennaLuvatutPisteetOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "TallennaLuvatutPisteetOnnnistui " vastaus)
    app)
  TallennaLuvatutPisteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "Paikkauskohteen tallennus epäonnistui"
      :varoitus
      viesti/viestin-nayttoaika-aareton)
    app)


  NakymastaPoistuttiin
  (process-event [_ app]
    (println "NakymastaPoistuttiin ")
    app))