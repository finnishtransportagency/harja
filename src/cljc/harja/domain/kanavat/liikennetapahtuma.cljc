(ns harja.domain.kanavat.liikennetapahtuma
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set :as set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.urakka :as ur]
    [harja.domain.sopimus :as sop]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.lt-alus :as lt-alus]
    [harja.domain.kanavat.lt-osa :as lt-osa])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_liikennetapahtuma" ::liikennetapahtuma
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::urakka (specql.rel/has-one ::urakka-id
                                 :harja.domain.urakka/urakka
                                 :harja.domain.urakka/id)
    ::sopimus (specql.rel/has-one ::sopimus-id
                                  :harja.domain.sopimus/sopimus
                                  :harja.domain.sopimus/id)
    ::kohde (specql.rel/has-one ::kohde-id
                                :harja.domain.kanavat.kohde/kohde
                                :harja.domain.kanavat.kohde/id)
    ::alukset (specql.rel/has-many ::id
                                   :harja.domain.kanavat.lt-alus/liikennetapahtuman-alus
                                   :harja.domain.kanavat.lt-alus/liikennetapahtuma-id)
    ::osat (specql.rel/has-many ::id
                                :harja.domain.kanavat.lt-osa/liikennetapahtuman-osa
                                :harja.domain.kanavat.lt-osa/liikennetapahtuma-id)
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}])

(def perustiedot
  #{::id
    ::aika
    ::lisatieto
    ::vesipinta-ylaraja
    ::vesipinta-alaraja})

(def kuittaajan-tiedot
  #{[::kuittaaja kayttaja/perustiedot]})

(def kohteen-tiedot
  #{[::kohde (set/union kohde/perustiedot kohde/kohteenosat)]})

(def alusten-tiedot
  #{[::alukset (set/union lt-alus/perustiedot lt-alus/metatiedot)]})

(def osien-tiedot
  #{[::osat lt-osa/perustiedot]})

(def sopimuksen-tiedot
  #{[::sopimus sop/perustiedot]})

(def sulku-toimenpiteet*
  ^{:private true}
  {:sulutus "Sulutus"
   :tyhjennys "Tyhjennys"})

(defn sulku-toimenpide->str [toimenpide]
  (sulku-toimenpiteet*
    toimenpide))

(def sulku-toimenpide-vaihtoehdot (keys sulku-toimenpiteet*))

(def palvelumuodot*
  ^{:private true}
  {:kauko "Kauko"
   :itse "Itsepalvelu"
   :paikallis "Paikallis"
   :muu "Muu"})

(def palvelumuoto-vaihtoehdot (keys palvelumuodot*))

(defn palvelumuoto->str [palvelumuoto]
  (palvelumuodot*
    palvelumuoto))

(def suunta*
  ^{:private true}
  {:ylos "Ylös"
   :alas "Alas"})

(defn suunta->str [suunta]
  (suunta*
    suunta))

(def suunta-vaihtoehdot (keys suunta*))

(s/def ::hae-liikennetapahtumat-kysely (s/keys :req [::ur/id ::sop/id]
                                               :opt [::sulku-toimenpide
                                                     ::kohde
                                                     ::lt-alus/laji
                                                     ::lt-alus/suunta]
                                               :opt-un [::aikavali ::niput?]))
(s/def ::hae-liikennetapahtumat-vastaus (s/coll-of
                                          (s/keys :req
                                                  [::id
                                                   ::aika
                                                   ::vesipinta-ylaraja
                                                   ::vesipinta-alaraja
                                                   ::sopimus
                                                   ::kuittaaja]
                                                  :opt
                                                  [::alukset
                                                   ::lisatieto])))

(s/def ::hakuparametrit ::hae-liikennetapahtumat-kysely)

(s/def ::tallenna-liikennetapahtuma-kysely (s/keys :req [::aika
                                                         (or
                                                           (and
                                                             ::sulku-toimenpide
                                                             ::sulku-palvelumuoto
                                                             ::sulku-lkm)
                                                           (and
                                                             ::silta-avaus
                                                             ::silta-palvelumuoto
                                                             ::silta-lkm))
                                                         ::vesipinta-ylaraja
                                                         ::vesipinta-alaraja
                                                         ::sopimus-id
                                                         ::urakka-id
                                                         ::kuittaaja-id
                                                         ::kohde-id]
                                                   :opt [::id
                                                         ::lisatieto
                                                         ::m/poistettu?
                                                         ::alukset]
                                                   :req-un [::hakuparametrit]))
(s/def ::tallenna-liikennetapahtuma-vastaus ::hae-liikennetapahtumat-vastaus)

(s/def ::hae-edelliset-tapahtumat-kysely (s/keys :req [::urakka-id
                                                       ::kohde-id
                                                       ::sopimus-id]))

(s/def ::ylos (s/nilable (s/coll-of ::lt-alus/liikennetapahtuman-alus)))
(s/def ::alas (s/nilable (s/coll-of ::lt-alus/liikennetapahtuman-alus)))
(s/def ::kohde (s/nilable ::liikennetapahtuma))
(s/def ::hae-edelliset-tapahtumat-vastaus (s/keys :req-un [::ylos
                                                           ::alas
                                                           ::kohde]))