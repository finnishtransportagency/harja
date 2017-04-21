(ns harja.domain.urakka
  "Määrittelee urakka nimiavaruuden specit, jotta urakan tietoja voi käyttää namespacetuilla
  keywordeilla, esim. {:urakka/id 12}"
  (:require [clojure.spec :as s]
            [harja.domain.organisaatio :as o]
            [harja.tyokalut.spec-apurit :as spec-apurit]
    #?@(:clj [
            [clojure.future :refer :all]])))

;; TODO Tämä on generoitu käyttäen macroa (define-tables db ["urakka" ::urakka]).
;; Jouduttiin expandoimaan käsin, koska figwheel / phantom ei osannut käsitellä makroa sellaisenaan.
;; Vaatii pohtimista miten ratkaistaan.
(do
  (clojure.core/swap!
    specql.impl.registry/table-info-registry
    clojure.core/merge
    {:harja.domain.urakka/urakka {:name "urakka",
                                  :type :table,
                                  :columns {:harja.domain.urakka/alkupvm {:name "alkupvm",
                                                                          :number 5,
                                                                          :not-null? true,
                                                                          :has-default? false,
                                                                          :type-specific-data -1,
                                                                          :type "date",
                                                                          :category "D",
                                                                          :primary-key? false,
                                                                          :enum? false},
                                            :harja.domain.urakka/urakkanro {:name "urakkanro",
                                                                            :number 17,
                                                                            :not-null? false,
                                                                            :has-default? false,
                                                                            :type-specific-data 20,
                                                                            :type "varchar",
                                                                            :category "S",
                                                                            :primary-key? false,
                                                                            :enum? false},
                                            :harja.domain.urakka/hanke_sampoid {:name "hanke_sampoid",
                                                                                :number 12,
                                                                                :not-null? false,
                                                                                :has-default? false,
                                                                                :type-specific-data 20,
                                                                                :type "varchar",
                                                                                :category "S",
                                                                                :primary-key? false,
                                                                                :enum? false},
                                            :harja.domain.urakka/sopimustyyppi {:name "sopimustyyppi",
                                                                                :number 11,
                                                                                :not-null? false,
                                                                                :has-default? false,
                                                                                :type-specific-data -1,
                                                                                :type "sopimustyyppi",
                                                                                :category "E",
                                                                                :primary-key? false,
                                                                                :enum? true},
                                            :harja.domain.urakka/hallintayksikko {:name "hallintayksikko",
                                                                                  :number 8,
                                                                                  :not-null? false,
                                                                                  :has-default? false,
                                                                                  :type-specific-data -1,
                                                                                  :type "int4",
                                                                                  :category "N",
                                                                                  :primary-key? false,
                                                                                  :enum? false},
                                            :harja.domain.urakka/luoja {:name "luoja",
                                                                        :number 23,
                                                                        :not-null? false,
                                                                        :has-default? false,
                                                                        :type-specific-data -1,
                                                                        :type "int4",
                                                                        :category "N",
                                                                        :primary-key? false,
                                                                        :enum? false},
                                            :harja.domain.urakka/nimi {:name "nimi",
                                                                       :number 4,
                                                                       :not-null? true,
                                                                       :has-default? false,
                                                                       :type-specific-data 132,
                                                                       :type "varchar",
                                                                       :category "S",
                                                                       :primary-key? false,
                                                                       :enum? false},
                                            :harja.domain.urakka/id {:name "id",
                                                                     :number 1,
                                                                     :not-null? true,
                                                                     :has-default? true,
                                                                     :type-specific-data -1,
                                                                     :type "int4",
                                                                     :category "N",
                                                                     :primary-key? true,
                                                                     :enum? false},
                                            :harja.domain.urakka/muokattu {:name "muokattu",
                                                                           :number 22,
                                                                           :not-null? false,
                                                                           :has-default? false,
                                                                           :type-specific-data -1,
                                                                           :type "timestamp",
                                                                           :category "D",
                                                                           :primary-key? false,
                                                                           :enum? false},
                                            :harja.domain.urakka/harjassa_luotu {:name "harjassa_luotu",
                                                                                 :number 20,
                                                                                 :not-null? true,
                                                                                 :has-default? true,
                                                                                 :type-specific-data -1,
                                                                                 :type "bool",
                                                                                 :category "B",
                                                                                 :primary-key? false,
                                                                                 :enum? false},
                                            :harja.domain.urakka/loppupvm {:name "loppupvm",
                                                                           :number 6,
                                                                           :not-null? true,
                                                                           :has-default? false,
                                                                           :type-specific-data -1,
                                                                           :type "date",
                                                                           :category "D",
                                                                           :primary-key? false,
                                                                           :enum? false},
                                            :harja.domain.urakka/sampoid {:name "sampoid",
                                                                          :number 3,
                                                                          :not-null? false,
                                                                          :has-default? false,
                                                                          :type-specific-data 36,
                                                                          :type "varchar",
                                                                          :category "S",
                                                                          :primary-key? false,
                                                                          :enum? false},
                                            :harja.domain.urakka/hanke {:name "hanke",
                                                                        :number 10,
                                                                        :not-null? false,
                                                                        :has-default? false,
                                                                        :type-specific-data -1,
                                                                        :type "int4",
                                                                        :category "N",
                                                                        :primary-key? false,
                                                                        :enum? false},
                                            :harja.domain.urakka/urakoitsija {:name "urakoitsija",
                                                                              :number 9,
                                                                              :not-null? false,
                                                                              :has-default? false,
                                                                              :type-specific-data -1,
                                                                              :type "int4",
                                                                              :category "N",
                                                                              :primary-key? false,
                                                                              :enum? false},
                                            :harja.domain.urakka/tyyppi {:name "tyyppi",
                                                                         :number 19,
                                                                         :not-null? true,
                                                                         :has-default? false,
                                                                         :type-specific-data -1,
                                                                         :type "urakkatyyppi",
                                                                         :category "E",
                                                                         :primary-key? false,
                                                                         :enum? true},
                                            :harja.domain.urakka/alue {:name "alue",
                                                                       :number 7,
                                                                       :not-null? false,
                                                                       :has-default? false,
                                                                       :type-specific-data -1,
                                                                       :type "geometry",
                                                                       :category "U",
                                                                       :primary-key? false,
                                                                       :enum? false},
                                            :harja.domain.urakka/takuu_loppupvm {:name "takuu_loppupvm",
                                                                                 :number 15,
                                                                                 :not-null? false,
                                                                                 :has-default? false,
                                                                                 :type-specific-data -1,
                                                                                 :type "date",
                                                                                 :category "D",
                                                                                 :primary-key? false,
                                                                                 :enum? false},
                                            :harja.domain.urakka/muokkaaja {:name "muokkaaja",
                                                                            :number 24,
                                                                            :not-null? false,
                                                                            :has-default? false,
                                                                            :type-specific-data -1,
                                                                            :type "int4",
                                                                            :category "N",
                                                                            :primary-key? false,
                                                                            :enum? false},
                                            :harja.domain.urakka/indeksi {:name "indeksi",
                                                                          :number 16,
                                                                          :not-null? false,
                                                                          :has-default? false,
                                                                          :type-specific-data -1,
                                                                          :type "text",
                                                                          :category "S",
                                                                          :primary-key? false,
                                                                          :enum? false},
                                            :harja.domain.urakka/poistettu {:name "poistettu",
                                                                            :number 25,
                                                                            :not-null? true,
                                                                            :has-default? true,
                                                                            :type-specific-data -1,
                                                                            :type "bool",
                                                                            :category "B",
                                                                            :primary-key? false,
                                                                            :enum? false},
                                            :harja.domain.urakka/luotu {:name "luotu",
                                                                        :number 21,
                                                                        :not-null? false,
                                                                        :has-default? false,
                                                                        :type-specific-data -1,
                                                                        :type "timestamp",
                                                                        :category "D",
                                                                        :primary-key? false,
                                                                        :enum? false}},
                                  :insert-spec-kw :harja.domain.urakka/urakka-insert,
                                  :rel nil}})
  (do
    (clojure.spec/def
      :harja.domain.urakka/urakka
      (clojure.spec/keys
        :opt
        [:harja.domain.urakka/alkupvm
         :harja.domain.urakka/urakkanro
         :harja.domain.urakka/hanke_sampoid
         :harja.domain.urakka/sopimustyyppi
         :harja.domain.urakka/hallintayksikko
         :harja.domain.urakka/luoja
         :harja.domain.urakka/nimi
         :harja.domain.urakka/id
         :harja.domain.urakka/muokattu
         :harja.domain.urakka/harjassa_luotu
         :harja.domain.urakka/loppupvm
         :harja.domain.urakka/sampoid
         :harja.domain.urakka/hanke
         :harja.domain.urakka/urakoitsija
         :harja.domain.urakka/tyyppi
         :harja.domain.urakka/alue
         :harja.domain.urakka/takuu_loppupvm
         :harja.domain.urakka/muokkaaja
         :harja.domain.urakka/indeksi
         :harja.domain.urakka/poistettu
         :harja.domain.urakka/luotu]))
    (clojure.spec/def
      :harja.domain.urakka/urakka-insert
      (clojure.spec/keys
        :req
        [:harja.domain.urakka/alkupvm :harja.domain.urakka/nimi :harja.domain.urakka/loppupvm :harja.domain.urakka/tyyppi]
        :opt
        [:harja.domain.urakka/urakkanro
         :harja.domain.urakka/hanke_sampoid
         :harja.domain.urakka/sopimustyyppi
         :harja.domain.urakka/hallintayksikko
         :harja.domain.urakka/luoja
         :harja.domain.urakka/id
         :harja.domain.urakka/muokattu
         :harja.domain.urakka/harjassa_luotu
         :harja.domain.urakka/sampoid
         :harja.domain.urakka/hanke
         :harja.domain.urakka/urakoitsija
         :harja.domain.urakka/alue
         :harja.domain.urakka/takuu_loppupvm
         :harja.domain.urakka/muokkaaja
         :harja.domain.urakka/indeksi
         :harja.domain.urakka/poistettu
         :harja.domain.urakka/luotu]))
    (clojure.spec/def :harja.domain.urakka/alkupvm :specql.data-types/date)
    (clojure.spec/def
      :harja.domain.urakka/urakkanro
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23499__auto__] (clojure.core/<= (clojure.core/count s__23499__auto__) 16)))))
    (clojure.spec/def
      :harja.domain.urakka/hanke_sampoid
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23499__auto__] (clojure.core/<= (clojure.core/count s__23499__auto__) 16)))))
    (clojure.spec/def :harja.domain.urakka/sopimustyyppi (clojure.spec/nilable #{"palvelusopimus" "kokonaisurakka"}))
    (clojure.spec/def :harja.domain.urakka/hallintayksikko (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.urakka/luoja (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def
      :harja.domain.urakka/nimi
      (clojure.spec/and
        :specql.data-types/varchar
        (clojure.core/fn [s__23499__auto__] (clojure.core/<= (clojure.core/count s__23499__auto__) 128))))
    (clojure.spec/def :harja.domain.urakka/id :specql.data-types/int4)
    (clojure.spec/def :harja.domain.urakka/muokattu (clojure.spec/nilable :specql.data-types/timestamp))
    (clojure.spec/def :harja.domain.urakka/harjassa_luotu :specql.data-types/bool)
    (clojure.spec/def :harja.domain.urakka/loppupvm :specql.data-types/date)
    (clojure.spec/def
      :harja.domain.urakka/sampoid
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23499__auto__] (clojure.core/<= (clojure.core/count s__23499__auto__) 32)))))
    (clojure.spec/def :harja.domain.urakka/hanke (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.urakka/urakoitsija (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def
      :harja.domain.urakka/tyyppi
      #{"hoito"
        "tekniset-laitteet"
        "valaistus"
        "vesivayla-ruoppaus"
        "vesivayla-hoito"
        "vesivayla-kanavien-korjaus"
        "siltakorjaus"
        "paallystys"
        "paikkaus"
        "tiemerkinta"
        "vesivayla-kanavien-hoito"
        "vesivayla-turvalaitteiden-korjaus"})
    (clojure.spec/def :harja.domain.urakka/alue (clojure.spec/nilable :specql.data-types/geometry))
    (clojure.spec/def :harja.domain.urakka/takuu_loppupvm (clojure.spec/nilable :specql.data-types/date))
    (clojure.spec/def :harja.domain.urakka/muokkaaja (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.urakka/indeksi (clojure.spec/nilable :specql.data-types/text))
    (clojure.spec/def :harja.domain.urakka/poistettu :specql.data-types/bool)
    (clojure.spec/def :harja.domain.urakka/luotu (clojure.spec/nilable :specql.data-types/timestamp))))

;; Haut

(s/def ::hae-harjassa-luodut-urakat
  (s/coll-of (s/and ::urakka
                    (s/keys :req [::hallintayksikko ::urakoitsija ::sopimukset ::hanke]))))

;; Urakkakohtainen kysely, joka vaatii vain urakan id:n.
;; Tätä speciä on hyvä käyttää esim. palveluiden, jotka hakevat
;; urakan tietoja, kyselyspecinä.
(s/def ::urakka-kysely (s/keys :req [::id]))

;; Muut

(defn vesivayla-urakka? [urakka]
  (#{:vesivayla-hoito :vesivayla-ruoppaus :vesivayla-turvalaitteiden-korjaus
     :vesivayla-kanavien-hoito :vesivayla-kanavien-korjaus}
    (:tyyppi urakka)))
