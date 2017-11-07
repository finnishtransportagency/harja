(ns harja.domain.kayttaja
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kayttaja" ::kayttaja])

(defn kokonimi [kayttaja]
  (str (::kayttaja/etunimi henkilo) " " (::kayttaja/sukunimi henkilo)))