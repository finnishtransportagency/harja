(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.grid :as grid]
            [harja.domain.paikkaus :as paikkaus]
            [taoensso.timbre :as log]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def app (atom {:paikkauskohteet [{:id 1
                                   :testinro "14566"
                                   :testinimi "Kaislajärven suora"
                                   :testitila "Ehdotettu"
                                   :testimenetelma "UREM"
                                   :testisijainti "23423/121231"
                                   :testiaikataulu "1.6. - 31.8.2021 (arv.)"
                                   :tierekisteriosoite {:tie 20, :aosa 19, :aet 1, :losa 19, :let 301}
                                   :sijainti {:type :multiline,
                                              :lines [{:type :line, :points [[505011.25093926466 7253055.999241401]
                                                                             [505012.2494422446 7253055.94454406]]}]
                                              :viivat [{:color "rgb(0, 0, 0)", :width 8}
                                                      {:color "rgb(0, 255, 0)", :width 6}]}}
                                  {:id 2
                                   :testinro "14567"
                                   :testinimi "Mount Utajärvi VT1"
                                   :testitila "Tilattu"
                                   :testimenetelma "KTVA"
                                   :testisijainti "23423/121231"
                                   :testiaikataulu "1.6. - 31.8.2021 (arv.)"
                                   :sijainti nil}
                                  {:id 3
                                   :testinro "14568"
                                   :testinimi "Kiikelin monttu"
                                   :testitila "Valmis"
                                   :testimenetelma "SIP"
                                   :testisijainti "23423/121231"
                                   :testiaikataulu "1.6. - 14.6.2021"
                                   :sijainti nil}]}))


(defrecord AvaaLomake [lomake])
(defrecord SuljeLomake [])

(extend-protocol tuck/Event
  AvaaLomake
  (process-event [lomake app]
    (-> app
        (assoc :lomake (:lomake lomake))))

  SuljeLomake
  (process-event [_ app]
    (dissoc app :lomake))
  )

