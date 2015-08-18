(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]))

(def tilannekuvassa? (atom (atom false)))

(def valittu-valilehti (atom :nykytilanne))