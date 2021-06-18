(ns harja.tiedot.urakka.lupaukset
  "Urakan lupausten tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(defn hae-urakan-lupaukset [urakka-id]
  (k/post! :hae-urakan-lupaukset urakka-id))