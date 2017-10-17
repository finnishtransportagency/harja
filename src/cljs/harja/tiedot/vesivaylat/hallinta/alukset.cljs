(ns harja.tiedot.vesivaylat.hallinta.alukset
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [tuck.core :as t]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def tila (atom {:alukset nil}))