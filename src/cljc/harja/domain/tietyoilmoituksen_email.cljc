(ns harja.domain.tietyoilmoituksen-email
  "Määrittelee tietyöilmoituksen emailin"
  #?@(:clj [
            (:require [clojure.spec.alpha :as s]
                      [harja.id :refer [id-olemassa?]]
                      [harja.kyselyt.specql-db :refer [define-tables]]
                      [clojure.future :refer :all]
                      [specql.rel :as rel])]
      :cljs [(:require [clojure.spec.alpha :as s]
               [harja.id :refer [id-olemassa?]]
               [specql.impl.registry]
               [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [define-tables]])]))

(define-tables
  ["tietyoilmoituksen_email_lahetys" ::email-lahetys
   {"id" ::id
    "tietyoilmoitus" ::tietyoilmoitus-id
    "tiedostonimi" ::tiedostonimi
    "lahetetty" ::lahetetty
    "lahetysid" ::lahetysid
    "lahettaja" ::lahettaja-id
    ::lahettaja (specql.rel/has-one ::lahettaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)
    "kuitattu" ::kuitattu
    "lahetysvirhe" ::lahetysvirhe}])
