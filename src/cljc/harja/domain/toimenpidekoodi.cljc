(ns harja.domain.toimenpidekoodi
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))


(define-tables
  ["toimenpidekoodi" ::toimenpidekoodi
   m/muokkaus-ja-poistotiedot
   {"emo" ::emo-id}
   {#?@(:clj [::toimenpidekoodi (rel/has-one
                                  ::emo-id
                                  :harja.domain.toimenpidekoodi/toimenpidekoodi
                                  :harja.domain.toimenpidekoodi/id)])}])


(defn tuotteen-jarjestys [t2-koodi]
  (case t2-koodi
    "23100" 1 ; Talvihoito ensimmäisenä
    "23110" 2 ; Liikenneympäristön hoito toisena
    "23120" 3 ; Soratien hoito kolmantena
    ;; kaikki muut sen jälkeen
    4))
