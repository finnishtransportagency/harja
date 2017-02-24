(ns harja.tyokalut.spec-apurit
  (:require [clojure.spec :as s]
    #?@(:clj [[clojure.future :refer :all]])))

(s/def ::postgres-int (s/and int? #(s/int-in-range? -2147483648 2147483647 %)))
(s/def ::postgres-serial (s/and nat-int? #(s/int-in-range? 1 2147483647 %)))