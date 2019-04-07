(ns harja.domain.tietyoilmoituksen-email
  "Määrittelee tietyöilmoituksen email-lähetysten tiedot"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.kayttaja :as kayttaja]
            [harja.id :refer [id-olemassa?]]
    #?@(:clj  [
            [harja.kyselyt.specql-db :refer [define-tables]]
            
            [specql.rel :as rel]]
        :cljs [
               [specql.impl.registry]
               [specql.data-types]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

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

(def perustiedot
  #{::id
    ::tietyoilmoitus-id
    ::tiedostonimi
    ::lahetetty
    ::lahetysid
    [::lahettaja kayttaja/perustiedot]
    ::kuitattu
    ::lahetysvirhe})
