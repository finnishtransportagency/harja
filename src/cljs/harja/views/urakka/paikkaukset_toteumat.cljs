(ns harja.views.urakka.paikkaukset-toteumat
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]))

(defn toteumat* [e! app]
  [:p "Viel ei oo mittää tääl"])

(defn toteumat []
  [tuck/tuck tiedot/app toteumat*])
