(ns harja.domain.urakka
  "Määrittelee urakka nimiavaruuden specit, jotta urakan tietoja voi käyttää namespacetuilla
  keywordeilla, esim. {:urakka/id 12}"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.organisaatio :as o]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.domain.sopimus :as sopimus]
            [clojure.string :as str]
            [harja.pvm :as pvm]
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

;; FIXME: Omituinen apufunktio, jota käytetään vain suunnittelussa yksikkohintaiset_tyot osastolla
(defn urakkatyyppi [urakka]
  (let [tyyppi (:tyyppi urakka)]
    (cond
      (kanavaurakkatyyppi? tyyppi) :kanava
      (vesivaylaurakkatyyppi? tyyppi) :vv
      :else :hoito)))

(defn yllapitourakka?
  "Onko urakka tyyppiä ylläpidon urakka"
  [urakan-tyyppi]
  (boolean (some #{urakan-tyyppi} #{:paallystys :paikkaus :tiemerkinta :valaistus})))

(defn paallystysurakka?
  [urakka]
  (= (:tyyppi urakka) :paallystys))

(defn paallystyksen-palvelusopimus?
  [urakka]
  (and
    (paallystysurakka? urakka)
    (= :palvelusopimus (:sopimustyyppi urakka))))

(defn paikkausurakka?
  [urakka]
  (and
    ;; kyllä: paikkausurakoiden urakkatyyppi on päällystys
    (paallystysurakka? urakka)
    (:nimi urakka)
    (str/includes? (:nimi urakka) "paikkaus")))

(defn mh-urakka?
  "Onko urakka tyyppiä MHU (Maanteiden hoitourakka)"
  [urakan-tyyppi]
  (= :teiden-hoito urakan-tyyppi))

(defn alueurakka?
  "Onko urakka tyyppiä alueurakka (poistuva)"
  [urakan-tyyppi]
  (= :hoito urakan-tyyppi))

(defn mh-tai-hoitourakka?
  "Onko urakka MHU (Maanteiden hoitourakka) tai vanhantyyppinen alueurakka (poistuva)"
  [urakan-tyyppi]
  (or (mh-urakka? urakan-tyyppi) (alueurakka? urakan-tyyppi)))

(defn hj-urakka?
  "Onko urakka tyyppiä 'Hoidon johdon' urakka"
  [urakan-tyyppi alkupvm]
  (and (= :teiden-hoito urakan-tyyppi) (< (pvm/vuosi alkupvm) 2019)))
