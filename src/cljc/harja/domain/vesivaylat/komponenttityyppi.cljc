(ns harja.domain.vesivaylat.komponenttityyppi
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_komponenttityyppi" ::komponenttityyppi])

(def kentat
  #{::id
    ::nimi
    ::lisatiedot
    ::luokan-id
    ::luokan-nimi
    ::luokan-lisatiedot
    ::luokan-paivitysaika
    ::luokan-luontiaika
    ::merk-cod
    ::paivitysaika
    ::luontiaika
    ::muokattu
    ::alkupvm
    ::loppupvm})
