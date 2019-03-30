(ns harja.domain.urakka
  "Määrittelee urakka nimiavaruuden specit, jotta urakan tietoja voi käyttää namespacetuilla
  keywordeilla, esim. {:urakka/id 12}"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.organisaatio :as o]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.domain.sopimus :as sopimus]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ])
    #?(:clj
            [specql.rel :as rel]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["urakkatyyppi" ::urakkatyyppi]
  ["urakka" ::urakka
   {#?@(:clj [::sopimukset (rel/has-many ::id ::sopimus/sopimus ::sopimus/urakka-id)])
    "hanke_sampoid" ::hanke-sampoid
    "hallintayksikko" ::hallintayksikko-id
    "harjassa_luotu" ::harjassa-luotu?
    "hanke" ::hanke-id
    "urakoitsija" ::urakoitsija-id
    "takuu_loppupvm" ::takuu-loppupvm
    "ulkoinen_id" ::ulkoinen-id
    "luoja" ::luoja-id
    "muokkaaja" ::muokkaaja-id}])

;; Haut
;; PENDING: 2 eri muotoa urakan tyypille, specql generoima string setti sekä tämä kw setti
;; yhtenäistä, kunhan specql tukee custom read/write optiota.
(s/def ::urakkatyyppi-kw
  #{:hoito
    :tekniset-laitteet
    :valaistus
    :vesivayla-ruoppaus
    :vesivayla-hoito
    :vesivayla-kanavien-korjaus
    :siltakorjaus
    :paallystys
    :paikkaus
    :tiemerkinta
    :vesivayla-kanavien-hoito
    :vesivayla-turvalaitteiden-korjaus})

(def urakkatyyppi->otsikko
  {:hoito "Hoito"
   :paallystys "Päällystys"
   :valaistus "Valaistus"
   :paikkaus "Paikkaus"
   :tiemerkinta "Tiemerkintä"})

(s/def ::hae-harjassa-luodut-urakat-vastaus
  (s/coll-of (s/and ::urakka
                    (s/keys :req [::hallintayksikko ::urakoitsija ::sopimukset ::hanke]))))

;; Urakkakohtainen kysely, joka vaatii vain urakan id:n.
;; Tätä speciä on hyvä käyttää esim. palveluiden, jotka hakevat
;; urakan tietoja, kyselyspecinä.
(s/def ::urakka-kysely (s/keys :req [::id]))

;; Tallennukset

(s/def ::tallenna-urakka-kysely (s/keys :req [::sopimukset ::hallintayksikko ::urakoitsija
                                              ::nimi ::loppupvm ::alkupvm]
                                        :opt [::id]))

(s/def ::tallenna-urakka-vastaus (s/keys :req [::sopimukset ::hallintayksikko ::urakoitsija
                                               ::nimi ::loppupvm ::alkupvm ::id]))

;; Muut

(def vesivayla-urakkatyypit #{:vesivayla-hoito :vesivayla-ruoppaus :vesivayla-turvalaitteiden-korjaus
                              :vesivayla-kanavien-hoito :vesivayla-kanavien-korjaus})

(def vesivayla-urakkatyypit-ilman-kanavia #{:vesivayla-hoito :vesivayla-ruoppaus :vesivayla-turvalaitteiden-korjaus})

(def kanava-urakkatyypit #{:vesivayla-kanavien-hoito :vesivayla-kanavien-korjaus})

(def vesivayla-urakkatyypit-raporttinakyma #{:vesivayla})

(defn vesivaylaurakkatyyppi? [tyyppi]
  (boolean (vesivayla-urakkatyypit tyyppi)))

(defn vesivaylaurakka-ei-kanavatyyppi? [tyyppi]
  (boolean (vesivayla-urakkatyypit-ilman-kanavia tyyppi)))

(defn vesivaylaurakka-ei-kanava? [urakka]
  (vesivaylaurakka-ei-kanavatyyppi? (:tyyppi urakka)))

(defn vesivaylaurakka? [urakka]
  (vesivaylaurakkatyyppi? (:tyyppi urakka)))

(defn kanavaurakkatyyppi? [tyyppi]
  (boolean (kanava-urakkatyypit tyyppi)))

(defn kanavaurakka? [urakka]
  (kanavaurakkatyyppi? (:tyyppi urakka)))

(defn urakkatyyppi [urakka]
  (let [tyyppi (:tyyppi urakka)]
    (cond
      (kanavaurakkatyyppi? tyyppi) :kanava
      (vesivaylaurakkatyyppi? tyyppi) :vv
      :else :hoito)))
