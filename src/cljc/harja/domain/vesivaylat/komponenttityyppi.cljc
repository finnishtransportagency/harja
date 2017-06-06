(ns harja.domain.vesivaylat.komponenttityyppi
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_komponenttityyppi" ::komponenttityyppi])
