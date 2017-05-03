(ns harja.tiedot.vesivaylat.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false}))