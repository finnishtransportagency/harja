(ns harja.domain.kanavat.liikennetapahtuma
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set :as set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.urakka :as ur]
    [harja.domain.sopimus :as sop]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.kohteenosa :as osa]
    [harja.domain.kanavat.lt-alus :as lt-alus]
    [harja.domain.kanavat.lt-toiminto :as toiminto]
    [harja.domain.kanavat.lt-ketjutus :as ketjutus])
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
    ::toiminnot (specql.rel/has-many ::id
                                :harja.domain.kanavat.lt-toiminto/liikennetapahtuman-toiminto
                                :harja.domain.kanavat.lt-toiminto/liikennetapahtuma-id)
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}])

(def +rajoita-tapahtumien-maara+ 10000)

(def perustiedot
  #{::id
    ::aika
    ::lisatieto
    ::vesipinta-ylaraja
    ::vesipinta-alaraja})

(def kuittaajan-tiedot
  #{[::kuittaaja kayttaja/perustiedot]})

(def kohteen-tiedot
  #{[::kohde kohde/perustiedot]})

(def alusten-tiedot
  #{[::alukset (set/union lt-alus/perustiedot lt-alus/metatiedot)]})

(def toimintojen-tiedot
  #{[::toiminnot toiminto/perustiedot]})

(def sopimuksen-tiedot
  #{[::sopimus sop/perustiedot]})

(def kaikki-toimenpiteet*
  ^{:private true}
  {:sulutus "Sulutus"
   :tyhjennys "Tyhjennys"
   :veden-korkeus "Vedenkorkeus"
   :avaus "Sillan avaus"
   :ei-avausta "Ei avausta"
   :ei-maaritelty "Ei määritelty"})

(defn kaikki-toimenpiteet->str [toimenpide]
  (kaikki-toimenpiteet*
   toimenpide))

(def sulku-toimenpiteet*
  ^{:private true}
  {:sulutus "Sulutus"
   :tyhjennys "Tyhjennys"
   :veden-korkeus "Vedenkorkeus"
   :ei-maaritelty "Ei määritelty"})

(def silta-toimenpiteet*
  ^{:private true}
  {:avaus "Sillan avaus"
   :ei-avausta "Ei avausta"})

(defn sulku-toimenpide->str [toimenpide]
  (sulku-toimenpiteet*
    toimenpide))

(defn silta-toimenpide->str [toimenpide]
  (silta-toimenpiteet*
    toimenpide))

(defn toimenpide->str [toimenpide]
  (or (sulku-toimenpide->str toimenpide)
      (silta-toimenpide->str toimenpide)))

(def sulku-toimenpide-vaihtoehdot (keys sulku-toimenpiteet*))
(def silta-toimenpide-vaihtoehdot (keys silta-toimenpiteet*))

(defn toimenpide-vaihtoehdot [osa]
  (if (osa/sulku? osa)
    sulku-toimenpide-vaihtoehdot
    silta-toimenpide-vaihtoehdot))

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

(defn fmt-palvelumuoto [toiminto]
  (str (palvelumuoto->str (::toiminto/palvelumuoto toiminto))
       (when (= :itse (::toiminto/palvelumuoto toiminto))
         (str " (" (::toiminto/lkm toiminto) " kpl)"))))

(def suunnat-atom (atom
                   {:ylos "Ylös"
                    :alas "Alas"
                    :ei-suuntaa "Ei määritelty"}))

(defn paivita-suunnat-ja-toimenpide! [paivitetyt-tiedot]
  (let [toiminnot (:harja.domain.kanavat.liikennetapahtuma/toiminnot paivitetyt-tiedot)
        ;; Onko tyhjennys toimenpidettä valittuna kohteen osissa
        tyhjennys-olemassa? (some #(= (::toiminto/toimenpide %) :tyhjennys) toiminnot)
        ;; Onko itsepalvelu tai vedenkorkus/tyhjennys valittuna tapahtuman kohdeosissa
        lisataan-ei-maaritelty? (some #(or
                                         (= (::toiminto/palvelumuoto %) :itse)
                                         (= (::toiminto/toimenpide %) :veden-korkeus)
                                         (= (::toiminto/toimenpide %) :tyhjennys)) toiminnot)]

    ;; Jos toimenpide tyhjennys olemassa, lisää "ei aluslajia" vaihtoehto 
    (if tyhjennys-olemassa?
      (swap! lt-alus/aluslajit* assoc :EI [lt-alus/lajittamaton-alus])
      (swap! lt-alus/aluslajit* dissoc :EI))

    ;; Jos tyhjennys/vedenkorkeus tai palvelumuoto itsepalvelu valittuna kohdeosissa, lisätään "ei määritelty" suunta alukselle
    (if lisataan-ei-maaritelty?
      (swap! suunnat-atom assoc :ei-suuntaa "Ei määritelty")
      (swap! suunnat-atom dissoc :ei-suuntaa))))

(s/def ::alukset (s/coll-of ::lt-alus/liikennetapahtuman-alus))
(s/def ::toiminnot (s/coll-of ::toiminto/liikennetapahtuman-toiminto))
(s/def ::urakka-idt (s/coll-of integer? :kind set?))

(s/def ::hae-liikennetapahtumat-kysely (s/keys :opt [::kohde
                                                     ::lt-alus/laji
                                                     ::lt-alus/suunta
                                                     ::toiminto/toimenpiteet]
                                               :req-un [::urakka-idt]
                                               :opt-un [::aikavali ::niput?]))

(s/def ::hae-liikennetapahtumat-vastaus (s/coll-of
                                          (s/keys :req
                                                  [::id
                                                   ::aika
                                                   ::sopimus
                                                   ::kuittaaja
                                                   ::toiminnot]
                                                  :opt
                                                  [::alukset
                                                   ::lisatieto
                                                   ::vesipinta-ylaraja
                                                   ::vesipinta-alaraja])))

(s/def ::hakuparametrit ::hae-liikennetapahtumat-kysely)

;; Override koska frontilta ei saa bigdecimal-muodossa. Kannassa NUMERIC.
(s/def ::vesipinta-alaraja (s/nilable number?))
(s/def ::vesipinta-ylaraja (s/nilable number?))

(s/def ::tallenna-liikennetapahtuma-kysely (s/keys :req [::aika
                                                         ::sopimus-id
                                                         ::urakka-id
                                                         ::kuittaaja-id
                                                         ::kohde-id
                                                         ::toiminnot]
                                                   :opt [::id
                                                         ::vesipinta-ylaraja
                                                         ::vesipinta-alaraja
                                                         ::lisatieto
                                                         ::m/poistettu?
                                                         ::alukset]
                                                   :req-un [::hakuparametrit]))

(s/def ::tallenna-liikennetapahtuma-vastaus ::hae-liikennetapahtumat-vastaus)

(s/def ::hae-edelliset-tapahtumat-kysely (s/keys :req [::urakka-id
                                                       ::kohde-id
                                                       ::sopimus-id]))

(s/def ::edellinen (s/nilable ::liikennetapahtuma))

(s/def ::edellinen-alustieto (s/keys :req [::lt-alus/id
                                           ::lt-alus/suunta
                                           ::lt-alus/laji
                                           ::lt-alus/lkm
                                           ::aika]
                                     :opt [::lt-alus/matkustajalkm
                                           ::lt-alus/nimi
                                           ::lt-alus/nippulkm
                                           ::lisatieto
                                           ::ketjutus/alus-id
                                           ::ketjutus/kohteelle-id
                                           ::ketjutus/kohteelta-id
                                           ::ketjutus/sopimus-id
                                           ::ketjutus/urakka-id
                                           ::kohde/id
                                           ::kohde/nimi]))

(s/def ::edelliset-alukset (s/coll-of ::edellinen-alustieto))

(s/def ::ylos (s/nilable (s/keys
                           :req [::kohde/nimi
                                 ::kohde/id]
                           :req-un [::edelliset-alukset])))

(s/def ::alas ::ylos)

(s/def ::hae-edelliset-tapahtumat-vastaus (s/keys :req-un [::ylos
                                                           ::alas
                                                           ::edellinen]))

(s/def ::poista-ketjutus-kysely (s/keys :req [::lt-alus/id
                                              ::urakka-id]))

(s/def ::poista-ketjutus-vastaus some?)
