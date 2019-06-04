(ns harja.domain.vesivaylat.materiaali
  (:require
    [harja.domain.muokkaustiedot :as m]
    [clojure.spec.alpha :as s]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_materiaali" ::materiaali
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot]
  ["vv_materiaali_muutos" ::muutos]
  ["vv_materiaalilistaus" ::materiaalilistaus])

(s/def ::materiaalilistauksen-haku (s/keys :req [::urakka-id]))
(s/def ::materiaalilistauksen-vastaus (s/coll-of ::materiaalilistaus))

(s/def ::materiaalikirjaus (s/keys :req [::urakka-id ::nimi ::maara ::pvm ::yksikko]
                                   :opt [::lisatieto ::halytysraja ::hairiotilanne ::toimenpide]))

(s/def ::materiaalikirjaukset (s/coll-of ::materiaalikirjaus))

(s/def ::poista-materiaalikirjaus (s/keys :req [::id ::urakka-id]))
(s/def ::muuta-materiaalien-alkuperaiset-tiedot (s/keys :req [::urakka-id]
                                                        :req-un [::uudet-alkuperaiset-tiedot]))

(s/def ::poista-materiaalikirjauksia (s/coll-of ::poista-materiaalikirjaus))
