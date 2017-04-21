(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.kyselyt.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
                       [specql.impl.registry]
                       [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [db]]
               [specql.core :refer [define-tables]])]))

(define-tables db ["organisaatio" ::organisaatio])

;; Haut

(s/def ::vesivayla-urakoitsijat-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::ytunnus ::katuosoite
                           ::postinumero ::tyyppi])))

;; Tallennus

(s/def ::tallenna-urakoitsija-kysely (s/keys :req [::nimi ::ytunnus ::katuosoite ::postinumero]
                                             :opt [::id]))

(s/def ::tallenna-urakoitsija-vastaus (s/keys :req [::id ::nimi ::ytunnus ::katuosoite
                                                    ::postinumero ::tyyppi]))