(ns harja.domain.vesivaylat.materiaali
  (:require
   [clojure.spec.alpha :as s]
   #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
             [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_materiaali" ::materiaali]
  ["vv_materiaali_muutos" ::muutos]
  ["vv_materiaalilistaus" ::materiaalilistaus])


(s/def ::materiaalilistauksen-haku (s/keys :req [::urakka-id]))
(s/def ::materiaalilistauksen-vastaus (s/coll-of ::materiaalilistaus))

(s/def ::materiaalikirjaus (s/and ::materiaali-insert
                                  (s/keys :req [::urakka-id ::nimi])))
