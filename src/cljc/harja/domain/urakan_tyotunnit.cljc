(ns harja.domain.urakan-tyotunnit
  "Urakan työtuntien skeemat."
  (:require [specql.impl.registry]
            [specql.data-types]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["urakan_tyotunnit" ::urakan-tyotunnit {"lahetys_onnistunut" ::lahetys-onnistunut}])
