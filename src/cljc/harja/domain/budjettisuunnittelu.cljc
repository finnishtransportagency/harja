(ns harja.domain.budjettisuunnittelu
  (:require [clojure.spec.alpha :as s]
            [specql.transform :as tx]
            [specql.rel]
            [harja.domain.toimenpideinstanssi :as tpi]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

#_(defrecord GeometryTierekisteri []
  tx/Transform
  (from-sql [_ geometry]
    #?(:clj (geo/pg->clj geometry)
       :cljs (identity geometry)))
  (to-sql [_ geometry]
    (identity geometry))
  (transform-spec [_ input-spec]
    ;; Ei osata specata geometriatyyppejä, joten spec olkoon any?
    any?))

#?(:clj (defrecord Foo []
          tx/Transform
          (from-sql [_ num]
            (long num))
          (to-sql [_ num]
            (bigdec num))
          (transform-spec [_ input-spec]
            number?)))

(define-tables
  ["yksikkohintainen_tyo" ::yksikkohintainen-tyo
   {"muokkaaja" ::text-muokkaaja-id}
   #?(:clj {::maara (specql.transform/transform (->Foo))
            ::yksikkohinta (specql.transform/transform (->Foo))
            ::arvioitu_kustannus (specql.transform/transform (->Foo))})]
  ["kustannusarvioitu_tyo" ::kustannusarvioitu-tyo
   {"kuukausi" ::smallint-kk
    "vuosi" ::smallint-v
    ::tyyppi (specql.transform/transform (specql.transform/to-keyword))
    ::tpi-id (specql.rel/has-one ::toimenpideinstanssi
                                 ::tpi/toimenpideinstanssi
                                 ::tpi/id)}]
  ["kiinteahintainen_tyo" ::kiinteahintainen-tyo
   {"kuukausi" ::smallint-kk
    "vuosi" ::smallint-v}]
  ["johto_ja_hallintokorvaus" ::johto-ja-hallintokorvaus
   {::maksukausi (specql.transform/transform (specql.transform/to-keyword))}])

