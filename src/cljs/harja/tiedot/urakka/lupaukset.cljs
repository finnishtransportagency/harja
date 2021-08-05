(ns harja.tiedot.urakka.lupaukset
  "Urakan lupausten tiedot."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! >! chan]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord HaeUrakanLupaustiedot [urakka])
(defrecord HaeUrakanLupaustiedotOnnnistui [vastaus])
(defrecord HaeUrakanLupaustiedotEpaonnistui [vastaus])

(defrecord VaihdaLuvattujenPisteidenMuokkausTila [])
(defrecord LuvattujaPisteitaMuokattu [pisteet])
(defrecord TallennaLupausSitoutuminen [])
(defrecord TallennaLupausSitoutuminenOnnnistui [vastaus])
(defrecord TallennaLupausSitoutuminenEpaonnistui [vastaus])

(defrecord NakymastaPoistuttiin [])

(defn- sitoutumistiedot [lupausrivit]
  {:pisteet (:sitoutuminen-pisteet (first lupausrivit))
   :id (:sitoutuminen-id (first lupausrivit))})

(extend-protocol tuck/Event

  HaeUrakanLupaustiedot
  (process-event [{urakka :urakka} app]
    (let [parametrit {:urakka-id (:id urakka)
                      :urakan-alkuvuosi 2021 ;M FIXME, vuosi app states kunhan tehty. (pvm/vuosi (:alkupvm urakka))
                      }]
      (-> app
          (tuck-apurit/post! :hae-urakan-lupaustiedot
                             parametrit
                             {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                              :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui}))))

  HaeUrakanLupaustiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotOnnnistui " vastaus)
    (assoc app :lupaustiedot vastaus
               :lupaus-sitoutuminen (sitoutumistiedot vastaus)))

  HaeUrakanLupaustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotEpaonnistui " vastaus)
    app)

  VaihdaLuvattujenPisteidenMuokkausTila
  (process-event [_ app]
    (let [arvo-nyt (:muokkaa-luvattuja-pisteita? app)]
     (assoc app :muokkaa-luvattuja-pisteita? (not arvo-nyt))))

  LuvattujaPisteitaMuokattu
  (process-event [{pisteet :pisteet} app]
    (assoc-in app [:lupaus-sitoutuminen :pisteet] pisteet))

  TallennaLupausSitoutuminen
  (process-event [_ app]
    (let [parametrit {:id (get-in app [:lupaus-sitoutuminen :id])
                      :pisteet (get-in app [:lupaus-sitoutuminen :pisteet])
                      :urakka-id (-> @tila/yleiset :urakka :id)}]
      (-> app
         (tuck-apurit/post! :tallenna-luvatut-pisteet
                            parametrit
                            {:onnistui ->TallennaLupausSitoutuminenOnnnistui
                             :epaonnistui ->TallennaLupausSitoutuminenEpaonnistui}))))

  TallennaLupausSitoutuminenOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "TallennaLupausSitoutuminenOnnnistui " vastaus)
    (assoc app :lupaustiedot vastaus
               :muokkaa-luvattuja-pisteita? false))

  TallennaLupausSitoutuminenEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "TallennaLupausSitoutuminenOnnnistui tallennus epäonnistui"
      :varoitus
      viesti/viestin-nayttoaika-aareton)
    app)


  NakymastaPoistuttiin
  (process-event [_ app]
    (println "NakymastaPoistuttiin ")
    app))