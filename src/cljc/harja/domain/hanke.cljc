(ns harja.domain.hanke
  "Määrittelee hankkeeseen liittyvät speksit"
  (:require [clojure.spec :as s]
            [harja.tyokalut.spec-apurit :as spec-apurit]
    #?@(:clj [
            [clojure.future :refer :all]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::alkupvm inst?)
(s/def ::loppupvm inst?)
(s/def ::liitetty-urakkaan (s/or :tyhja nil? :urakan-nimi string?))
(s/def ::nimi string?)

(s/def ::hanke
  (s/keys :req-un [::alkupvm ::loppupvm ::nimi]
          :opt-un [::id ::liitetty-urakkaan]))

;; Haut

(s/def ::hae-harjassa-luodut-hankkeet-vastaus
  (s/coll-of ::hanke))

;; Tallennus

(s/def ::tallenna-hanke-kysely
  (s/keys :req-un [::hanke]))

(s/def ::tallenna-hanke-vastaus (s/and ::hanke
                                       (s/keys :req-un [::id])))