(ns harja.domain.vesivaylat.turvalaitekomponentti
  (:require
    [harja.domain.vesivaylat.komponenttityyppi :as ktyyppi]
    [clojure.spec.alpha :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(def kentat
  #{::id
    ::lisatiedot
    ::turvalaitenro
    ::komponentti-id
    ::sarjanumero
    ::paivitysaika
    ::luontiaika
    ::luoja
    ::muokkaaja
    ::muokattu
    ::alkupvm
    ::loppupvm
    ::valiaikainen})

(def komponenttityyppi #{[::komponenttityyppi #{::ktyyppi/nimi ::ktyyppi/luokan-nimi}]})

(defn turvalaitekomponentit-turvalaitenumerolla [turvalaitekomponentit turvalaitenumero]
  (filter #(= (::turvalaitenro %) turvalaitenumero) turvalaitekomponentit))
