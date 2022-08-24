(ns harja.tiedot.urakka.suunnittelu.tehtavamaarat
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn hae-urakan-tehtavamaarat-hierarkiassa [urakka-id hoitokauden-alkuvuosi]
  (let [tiedot {:urakka-id  urakka-id
                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}]
    (k/post! :tehtavamaarat-hierarkiassa
             tiedot)))

(defn tallenna-tehtavamaarat
  "Tallentaa tehtävä- ja määräluettelossa suunnitellut urakan hoitokausikohtaiset tehtävämäärät."
  [urakka-id hoitokausi tehtavamaarat]
  (let [tiedot {:urakka-id     urakka-id
                :hoitokausi    hoitokausi
                :tehtavamaarat tehtavamaarat}]
    (k/post! :tallenna-tehtavamaarat
             tiedot)))


