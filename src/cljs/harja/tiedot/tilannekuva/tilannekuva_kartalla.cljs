(ns harja.tiedot.tilannekuva.tilannekuva-kartalla
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tilannekuva (atom false))
(defonce haetut-asiat (atom nil))

(defonce tilannekuvan-asiat-kartalla
         (reaction
           @haetut-asiat
           (when @karttataso-tilannekuva
             (kartalla-esitettavaan-muotoon
               (concat (vals (:tyokoneet @haetut-asiat)) (apply concat (vals (dissoc @haetut-asiat :tyokoneet))))))))

