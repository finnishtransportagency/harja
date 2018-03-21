(ns harja.views.urakka.paikkaukset
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.paikkaukset :as tiedot]))

(defn paikkaukset* [e! app]
  [:p "Viel ei oo mittää tääl"])

(defn paikkaukset []
  [tuck/tuck tiedot/app paikkaukset*])
