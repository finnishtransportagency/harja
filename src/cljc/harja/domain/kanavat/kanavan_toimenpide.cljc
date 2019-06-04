(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.kohteenosa :as osa]
    [harja.domain.kanavat.hinta :as hinta]
    [harja.domain.kanavat.tyo :as tyo]
    [harja.domain.kanavat.kommentti :as kommentti]
    [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
    [harja.domain.toimenpidekoodi :as toimenpidekoodi]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.kayttaja :as kayttaja]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])
    [clojure.set :as set]
    [clojure.string :as str])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_toimenpidetyyppi" ::kanava-toimenpidetyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_toimenpide" ::kanava-toimenpide
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "muu_toimenpide" ::muu-toimenpide
    ;;; ei saatu joineja toimimaan oikein meidän vanhan specql:n kanssa
    ::hinnat (specql.rel/has-many ::id
                                  :harja.domain.kanavat.hinta/toimenpiteen-hinta
                                  :harja.domain.kanavat.hinta/toimenpide-id)
    ::tyot (specql.rel/has-many ::id
                                :harja.domain.kanavat.tyo/toimenpiteen-tyo
                                :harja.domain.kanavat.tyo/toimenpide-id)
    ::kommentit (specql.rel/has-many ::id
                                     :harja.domain.kanavat.kommentti/kommentti
                                     :harja.domain.kanavat.kommentti/toimenpide-id)
    "toimenpideinstanssi" ::toimenpideinstanssi-id
    ::kohde (specql.rel/has-one ::kohde-id
                                :harja.domain.kanavat.kohde/kohde
                                :harja.domain.kanavat.kohde/id)

    ::kohteenosa (specql.rel/has-one ::kohteenosa-id
                                     :harja.domain.kanavat.kohteenosa/kohteenosa
                                     :harja.domain.kanavat.kohteenosa/id)

    "huoltokohde" ::huoltokohde-id
    ::huoltokohde (specql.rel/has-one ::huoltokohde-id
                                      :harja.domain.kanavat.kanavan-huoltokohde/huoltokohde
                                      :harja.domain.kanavat.kanavan-huoltokohde/id)

    "toimenpidekoodi" ::toimenpidekoodi-id
    ::toimenpidekoodi (specql.rel/has-one ::toimenpidekoodi-id
                                          :harja.domain.toimenpidekoodi/toimenpidekoodi
                                          :harja.domain.toimenpidekoodi/id)
    "kuittaaja" ::kuittaaja-id
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}])

(def viittaus-idt #{::urakka-id ::sopimus-id ::kohde-id ::toimenpidekoodi-id ::kuittaaja-id})

(def muokkaustiedot
  #{::muokkaustiedot/luoja-id
    ::muokkaustiedot/luotu
    ::muokkaustiedot/muokkaaja-id
    ::muokkaustiedot/muokattu
    ::muokkaustiedot/poistaja-id
    ::muokkaustiedot/poistettu?})

(def kohteen-tiedot
  #{[::kohde kohde/perustiedot]})

(def kohteenosan-tiedot
  #{[::kohteenosa osa/perustiedot]})

(def huoltokohteen-tiedot
  #{[::huoltokohde huoltokohde/perustiedot]})

(def toimenpiteen-tiedot
  #{[::toimenpidekoodi toimenpidekoodi/perustiedot]})

(def kuittaajan-tiedot
  #{[::kuittaaja kayttaja/perustiedot]})

(def perustiedot
  #{::id
    ::tyyppi
    ::pvm
    ::muu-toimenpide
    ::lisatieto
    ::suorittaja
    ::sopimus-id
    ::toimenpideinstanssi-id
    ::sijainti})

(def perustiedot-viittauksineen
  (set/union perustiedot
             muokkaustiedot
             kohteen-tiedot
             kohteenosan-tiedot
             huoltokohteen-tiedot
             toimenpiteen-tiedot
             kuittaajan-tiedot))

(s/def ::hae-kanavatoimenpiteet-kysely
  (s/keys :req [::urakka-id
                ::sopimus-id
                ::kanava-toimenpidetyyppi
                ::toimenpidekoodi/id
                ::kohde-id]
          :req-un [::alkupvm
                   ::loppupvm]))

(s/def ::toimenpide-idt (s/coll-of ::id))

(s/def ::hae-kanavatoimenpiteet-vastaus
  (s/coll-of ::kanava-toimenpide))

(s/def ::tallenna-kanavatoimenpide-vastaus
  (s/keys :req-un [::kanavatoimenpiteet ::materiaalilistaus]))

(s/def ::siirra-kanavatoimenpiteet-kysely
  (s/keys
    :req [::urakka-id ::toimenpide-idt ::tyyppi]))

(s/def ::siirra-kanavatoimenpiteet-vastaus
  ::toimenpide-idt)

(s/def ::tallenna-kanavatoimenpiteen-hinnoittelu-kysely
  (s/keys
    :req [::urakka-id
          ::id
          :harja.domain.kanavat.hinta/tallennettavat-hinnat
          :harja.domain.kanavat.tyo/tallennettavat-tyot]))

(s/def ::tallenna-kanavatoimenpiteen-hinnoittelu-vastaus
  ::kanava-toimenpide)

(s/def ::tallenna-kanavatoimenpiteen-hinnoittelun-kommentti-kysely
  (s/keys
    :req [::urakka-id
          ::kommentti/kommentti
          ::kommentti/tila
          ::kommentti/toimenpide-id]))

(s/def ::tallenna-kanavatoimenpiteen-hinnoittelun-kommentti-vastaus
  ::kanava-toimenpide)

(s/def ::tallennettava-kanava-toimenpide
  (s/keys :req [::urakka-id
                ::sopimus-id
                ::pvm
                ::huoltokohde-id
                ::toimenpideinstanssi-id
                ::suorittaja
                ::tyyppi]
          :opt [::kohteenosa-id
                ::lisatieto
                ::kohde-id
                ::id
                ::toimenpidekoodi-id
                ::muu-toimenpide
                ::sijainti
                ::materiaalikirjaukset
                ::materiaalipoistot]))

(s/def ::tallenna-kanavatoimenpide-kutsu
  (s/keys :req [::hae-kanavatoimenpiteet-kysely
                ::tallennettava-kanava-toimenpide]))

(defn korosta-ei-yksiloity
  "Korostaa ei yksilöidyt toimenpiteet gridissä"
  [toimenpide]
  (let [toimenpidekoodi-nimi (get-in toimenpide [::toimenpidekoodi ::toimenpidekoodi/nimi])]
    (if (= (str/lower-case toimenpidekoodi-nimi) "ei yksilöity")
      (assoc toimenpide :lihavoi true)
      toimenpide)))

(defn korosta-ei-yksiloidyt [toimenpiteet]
  (map korosta-ei-yksiloity toimenpiteet))

(defn fmt-toimenpiteen-kohde
  "Ottaa mapin, jossa on toimenpiteen kohde (ja kohdeosa).
   Mikäli toimenpide liittyy kohdeosaan, näyttää sen nimen, muussa tapauksessa näyttää vain
   kohteen nimen. Jos kohdetta ei ole, palauttaa tekstin 'Ei kohdetta'."
  [toimenpide]
  (let [kohde (::kohde toimenpide)
        kohdeosa (::kohteenosa toimenpide)]
    (or (kohde/fmt-kohde-ja-osa-nimi kohde kohdeosa)
        "Ei kohdetta")))
