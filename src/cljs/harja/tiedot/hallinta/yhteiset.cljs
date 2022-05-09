(ns harja.tiedot.hallinta.yhteiset
  "Hallintaosion yhteiset tiedot"
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce valittu-urakkatyyppi (atom (or
                                      @nav/urakkatyyppi
                                      (first nav/+urakkatyypit+))))

;; oletuksena käynnissäoleva hoitokausi
(defonce valittu-aikavali (atom (pvm/paivamaaran-hoitokausi (pvm/nyt))))

(defn valitse-aikavali! [aikavali]
  (reset! valittu-aikavali aikavali))