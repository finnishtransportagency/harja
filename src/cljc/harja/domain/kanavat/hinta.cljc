(ns harja.domain.kanavat.hinta
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables

  ["kan_hinta" ::toimenpiteen-hinta
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::ryhma (specql.transform/transform (specql.transform/to-keyword))}]
  ["kan_toimenpide_hinta" ::toimenpide<->hinta
   {::hinta-tiedot (specql.rel/has-one ::hinta ::toimenpiteen-hinta ::id)}])

;; Löysennetään tyyppejä numeroiksi, koska JS-maailmassa ei ole BigDeccejä
(s/def ::summa (s/nilable number?))
(s/def ::maara (s/nilable number?))
(s/def ::yksikkohinta (s/nilable number?))
(s/def ::yleiskustannuslisa number?)

(def perustiedot
  #{::otsikko
    ::summa
    ::yleiskustannuslisa
    ::maara
    ::yksikkohinta
    ::yksikko
    ::ryhma
    ::id})

(def viittaus-idt #{})

(def metatiedot m/muokkauskentat)

;; Yleinen yleiskustannuslisä (%), joka käytössä sopimuksissa
(def yleinen-yleiskustannuslisa 12)
