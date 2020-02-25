(ns harja.domain.budjettisuunnittelu
  (:require [clojure.spec.alpha :as s]
            [specql.transform :as tx]
            [specql.rel]
            [harja.kyselyt.specql]
            [harja.domain.toimenpideinstanssi :as tpi]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["yksikkohintainen_tyo" ::yksikkohintainen-tyo
   {"muokkaaja" ::text-muokkaaja-id}
   #?(:clj {::maara (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))
            ::yksikkohinta (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))
            ::arvioitu_kustannus (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))})]
  ["kustannusarvioitu_tyo" ::kustannusarvioitu-tyo
   {"kuukausi" ::smallint-kk
    "vuosi" ::smallint-v
    ::tyyppi (specql.transform/transform (specql.transform/to-keyword))
    ::tpi-id (specql.rel/has-one ::toimenpideinstanssi
                                 ::tpi/toimenpideinstanssi
                                 ::tpi/id)}
   #?(:clj {::summa (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))})]
  ["kiinteahintainen_tyo" ::kiinteahintainen-tyo
   {"kuukausi" ::smallint-kk
    "vuosi" ::smallint-v}
   #?(:clj {::summa (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))})]
  ["johto_ja_hallintokorvaus_toimenkuva" ::johto-ja-hallintokorvaus-toimenkuva]
  ["johto_ja_hallintokorvaus" ::johto-ja-hallintokorvaus
   {::maksukausi (specql.transform/transform (specql.transform/to-keyword))
    ::toimenkuva (specql.rel/has-one ::toimenkuva-id
                                     ::johto-ja-hallintokorvaus-toimenkuva
                                     ::id)}
   #?(:clj {::tunnit (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))
            ::tuntipalkka (specql.transform/transform (harja.kyselyt.specql/->NumberTransform))})])

