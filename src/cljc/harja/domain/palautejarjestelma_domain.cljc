(ns harja.domain.palautejarjestelma-domain
  (:require
    [specql.rel :as rel]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
                 :cljs [[specql.impl.registry]]))
  #?(:cljs
    (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["palautejarjestelma_aihe" ::aihe
   {"ulkoinen_id" ::aihe-id
    "nimi" ::nimi
    "jarjestys" ::jarjestys
    "kaytossa" ::kaytossa?
    ::tarkenteet (rel/has-many
                    ::aihe-id
                    ::tarkenne
                    ::aihe-id)}]
  ["palautejarjestelma_tarkenne" ::tarkenne
   {"ulkoinen_id" ::tarkenne-id
    "nimi" ::nimi
    "jarjestys" ::jarjestys
    "kaytossa" ::kaytossa?
    "aihe_id" ::aihe-id}])
