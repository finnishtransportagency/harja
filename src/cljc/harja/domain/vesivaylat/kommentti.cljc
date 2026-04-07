(ns harja.domain.vesivaylat.kommentti
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.kayttaja :as kayttaja]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]])
    [harja.pvm :as pvm])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

#?(:clj
   (define-tables
     ["kommentin_tila" ::kommentin-tila (specql.transform/transform (specql.transform/to-keyword))]
     ["vv_hinnoittelu_kommentti" ::hinnoittelun-kommentti
      {::kayttaja (specql.rel/has-one ::kayttaja-id
                                      :harja.domain.kayttaja/kayttaja
                                      :harja.domain.kayttaja/id)}]))

(def perustiedot
  #{::tila
    ::aika
    ::kommentti})

(def kayttajan-tiedot
  #{[::kayttaja kayttaja/perustiedot]})

(defn hinnoittelun-tila [kommentit]
  (-> (sort-by ::aika pvm/jalkeen? kommentit) first ::tila))

(defn- tila->str* [tila]
  (or
    ({:luotu "Luo\u00ADtu"
      :muokattu "Muo\u00ADkat\u00ADtu"
      :hyvaksytty "Hy\u00ADväksyt\u00ADty"
      :hylatty "Hylä\u00ADtty"}
      tila)
    "Hin\u00ADnoit\u00ADtelema\u00ADton"))

(defn hinnoittelun-tila->str [kommentit]
  (tila->str* (hinnoittelun-tila kommentit)))
