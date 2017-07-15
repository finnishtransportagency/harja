(ns harja.domain.graylog
  #?@(:clj [(:require [clj-time.coerce :as tc]
                      [clj-time.format :as tf]
                      [clojure.spec.alpha :as s]
                      [clojure.spec.gen.alpha :as gen]
                      [harja.pvm :as pvm])]
      :cljs [(:require [cljs-time.coerce :as tc]
                       [cljs-time.format :as tf]
                       [cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]
                       [harja.pvm :as pvm])]))


(defn date-gen []
  (gen/fmap (fn [_]
              (let [pv (inc (rand-int 31))
                    kk (rand-int 12)
                    vuosi (inc (rand-int 2100))
                    max-paiva (pvm/paivia-kuukaudessa vuosi kk)
                    pv (if (> pv max-paiva) max-paiva pv)]
                (pvm/luo-pvm vuosi kk pv)))
            (gen/int)))

(defn iso8601-pvm-gen []
  (gen/fmap (fn [date]
              (pvm/aika-iso8601 date))
            (date-gen)))
(defn palvelu-gen []
  (gen/fmap (fn [satunnainen-teksti]
              (let [mahdollista-poistaa-palvelujen-alut ["hae-" "tallenna-" "urakan-"]
                    satunnainen-palvelun-alku (rand-nth mahdollista-poistaa-palvelujen-alut)
                    satunnainen-palvelu (if (>= (rand) 0.5)
                                          (str satunnainen-palvelun-alku satunnainen-teksti)
                                          satunnainen-teksti)]
                satunnainen-palvelu))
            (gen/such-that (fn [x] (not= "" x))
                           (gen/string-alphanumeric))))

(s/def ::pvm (s/with-gen (s/and string?
                                #(pvm/pvm? (pvm/iso-8601->pvm %)))
                         #(gen/fmap (fn [date]
                                      (let [vuosi (pvm/vuosi date)
                                            kk (pvm/kuukausi date)
                                            pv (pvm/paiva date)]
                                        (str vuosi "-" kk "-" pv)))
                                    (date-gen))))
(s/def ::kello (s/with-gen (s/and string?
                                  #(pvm/pvm? (tc/from-date (pvm/parsi pvm/fi-aika-sek %))))
                           #(gen/fmap (fn [_]
                                        (let [tunnit (inc (rand-int 24))
                                              minuutit (inc (rand-int 60))
                                              sekunnit (inc (rand-int 60))]
                                          (str tunnit ":" minuutit ":" sekunnit)))
                                      (gen/int))))
(s/def ::kayttaja string?)
(s/def ::palvelut (s/with-gen (s/coll-of string? :kind vector?)
                              #(gen/vector (palvelu-gen))))
(s/def ::katkokset (s/coll-of (s/and integer? pos?) :kind vector?))
(s/def ::ensimmaiset-katkokset (s/with-gen (s/coll-of (s/and string?
                                                             #(pvm/pvm? (tf/parse %)))
                                                      :kind vector?)
                                           #(gen/vector (iso8601-pvm-gen))))
(s/def ::viimeiset-katkokset (s/with-gen (s/coll-of (s/and string?
                                                           #(pvm/pvm? (tf/parse %)))
                                                    :kind vector?)
                                         #(gen/vector (iso8601-pvm-gen))))
(s/def ::parsittu-yhteyskatkos-data-itemi (s/and (s/keys :opt-un [::pvm ::kello ::kayttaja ::palvelut
                                                                  ::katkokset ::ensimmaiset-katkokset ::viimeiset-katkokset])
                                                 #(apply = (keep (fn [x]
                                                                  (when (not (nil? x))
                                                                    (count x)))
                                                                 [(:palvelut %) (:katkokset %) (:ensimmaiset-katkokset %) (:viimeiset-katkokset %)]))))
(s/def ::parsittu-yhteyskatkos-data (s/coll-of ::parsittu-yhteyskatkos-data-itemi :kind seq?))
