(ns harja.tiedot.urakka.toteumat.velho-varusteet-tiedot
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.urakka :as urakka]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn hoitokausi-rajat [alkuvuosi]
  [(pvm/hoitokauden-alkupvm alkuvuosi)
   (pvm/hoitokauden-loppupvm (inc alkuvuosi))])

(defrecord ValitseHoitokausi [urakka-id hoitokauden-alkuvuosi])
(defrecord ValitseHoitokaudenKuukausi [urakka-id hoitokauden-kuukausi])
(defrecord HaeVarusteet [])
(defrecord HaeVarusteetOnnistui [vastaus])
(defrecord HaeVarusteetEpaonnistui [vastaus])

(def fin-hk-alkupvm "01.10.")
(def fin-hk-loppupvm "30.09.")


(extend-protocol tuck/Event

  ValitseHoitokausi
  (process-event [{urakka-id :urakka-id hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (-> app
        (assoc-in [:valinnat :hoitokauden-alkuvuosi] hoitokauden-alkuvuosi)
        (assoc-in [:valinnat :hoitokauden-kuukausi] (hoitokausi-rajat hoitokauden-alkuvuosi))))

  ValitseHoitokaudenKuukausi
  (process-event [{urakka-id :urakka-id hoitokauden-kuukausi :hoitokauden-kuukausi} app]
    (do
      (assoc-in app [:valinnat :hoitokauden-kuukausi] hoitokauden-kuukausi)))

  HaeVarusteet
  (process-event [{urakka-id :urakka-id hoitokauden-alkuvuosi :hoitokauden-alkuvuosi hoitokauden-kuukausi :hoitokauden-kuukausi} app]
    (let [urakka-id (-> @tila/tila :yleiset :urakka :id)]
      (-> app
          (tuck-apurit/post! :hae-urakan-varustetoteuma-ulkoiset
                             {:urakka-id urakka-id}
                             {:onnistui ->HaeVarusteetOnnistui
                              :epaonnistui ->HaeVarusteetEpaonnistui})))
    app)

  HaeVarusteetOnnistui
  (process-event [{:keys [vastaus] :as jotain} app]
    (println "petrisi1225: jotain onnistui: " jotain)
    (assoc app :varusteet (:toteumat vastaus)))

  HaeVarusteetEpaonnistui
  (process-event [{:keys [vastaus] :as jotain-muuta} app]
    (println "petrisi1226: jotain-muuta epäonnistui: " jotain-muuta)
    (viesti/nayta! "Varusteiden haku epäonnistui!" :danger)
    app))
