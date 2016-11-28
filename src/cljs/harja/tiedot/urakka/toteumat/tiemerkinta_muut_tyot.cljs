(ns harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def muut-tyot (atom {:valittu-tyo nil
                      :muut-tyot nil}))
