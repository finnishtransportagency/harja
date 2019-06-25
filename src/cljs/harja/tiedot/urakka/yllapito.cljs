(ns harja.tiedot.urakka.yllapito
  "Ylläpidon urakoiden yhteiset tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

;; Ylläpidon näkymien yhteiset suodattimet
(def tienumero (atom nil))
(def kohdenumero (atom nil))
