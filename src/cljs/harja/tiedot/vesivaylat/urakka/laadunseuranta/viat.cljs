(ns harja.tiedot.vesivaylat.urakka.laadunseuranta.viat
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false}))

(defrecord Nakymassa? [nakymassa?])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?)))
