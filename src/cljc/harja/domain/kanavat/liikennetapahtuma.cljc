(ns harja.domain.kanavat.liikennetapahtuma
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.urakka :as ur]
    [harja.domain.sopimus :as sop]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.kanavat.kanavan-kohde :as kohde]
    [harja.domain.kanavat.lt-alus :as lt-alus]
    [harja.domain.kanavat.lt-nippu :as lt-nippu])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["liikennetapahtuma_toimenpidetyyppi" ::lt-toimenpidetyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["liikennetapahtuma_palvelumuoto" ::lt-palvelumuoto (specql.transform/transform (specql.transform/to-keyword))]
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
                                :harja.domain.kanavat.kanavan-kohde/kohde
                                :harja.domain.kanavat.kanavan-kohde/id)
    ::alukset (specql.rel/has-many ::id
                                   :harja.domain.kanavat.lt-alus/liikennetapahtuman-alus
                                   :harja.domain.kanavat.lt-alus/liikennetapahtuma-id)
    ::niput (specql.rel/has-many ::id
                                 :harja.domain.kanavat.lt-nippu/liikennetapahtuman-nippu
                                 :harja.domain.kanavat.lt-nippu/liikennetapahtuma-id)
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}])

(def perustiedot
  #{::id
    ::aika
    ::toimenpide
    ::palvelumuoto
    ::palvelumuoto-lkm
    ::lisatieto
    ::vesipinta-ylaraja
    ::vesipinta-alaraja})

(def kuittaajan-tiedot
  #{[::kuittaaja kayttaja/perustiedot]})

(def kohteen-tiedot
  #{[::kohde kohde/perustiedot-ja-kanava]})

(def alusten-tiedot
  #{[::alukset lt-alus/perustiedot]})

(def nippujen-tiedot
  #{[::niput lt-nippu/perustiedot]})

(def toimenpiteet*
  ^{:private true}
  {:sulutus "Sulutus"
   :tyhjennys "Tyhjennys"
   :sillan-avaus "Sillan avaus"})

(defn toimenpide->str [toimenpide]
  (toimenpiteet*
    toimenpide))

(def toimenpide-vaihtoehdot (keys toimenpiteet*))

(defn palvelumuoto->str [palvelumuoto]
  ({:kauko "Kauko"
    :itse "Itsepalvelu"
    :paikallis "Paikallis"
    :muu "Muu"}
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
                                               :opt [::toimenpide
                                                     ::kohde
                                                     ::lt-alus/laji
                                                     ::lt-alus/suunta]
                                               :opt-un [::aikavali ::niput?]))
(s/def ::hae-liikennetapahtumat-vastaus (s/coll-of
                                          (s/keys :req
                                                  [::id ::aika ::toimenpide ::lisatieto ::vesipinta-alaraja
                                                   ::vesipinta-ylaraja ::kuittaaja
                                                   ::kohde]
                                                  :opt
                                                  [::niput ::alukset])))