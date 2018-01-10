(ns harja.tiedot.urakka.yksityiskohtainen-aikataulu
  "Ylläpidon urakoiden yksityiskohtainen aikataulu"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn tallenna-aikataulu [rivit]
  (log "TODO!"))