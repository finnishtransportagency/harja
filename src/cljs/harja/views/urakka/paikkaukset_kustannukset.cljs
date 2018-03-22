(ns harja.views.urakka.paikkaukset-kustannukset
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.paikkaukset-kustannukset :as tiedot]))

(defn kustannukset* [e! app]
  [:p "Viel ei oo mittää tääl"])

(defn kustannukset []
  [tuck/tuck tiedot/app kustannukset*])
