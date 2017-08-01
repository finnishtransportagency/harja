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
            (s/gen integer?)))

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
                                        (let [tunnit (rand-int 24)
                                              minuutit (rand-int 60)
                                              sekunnit (rand-int 60)]
                                          (str tunnit ":" minuutit ":" sekunnit)))
                                      (s/gen integer?))))
(s/def ::kayttaja string?)
(s/def ::palvelu (s/with-gen (s/nilable string?)
                             #(palvelu-gen)))
(s/def ::katkokset (s/nilable (s/and integer? pos?)))
(s/def ::ensimmainen-katkoks (s/with-gen (s/nilable (s/and string?
                                                           #(pvm/pvm? (tf/parse %))))
                                         #(iso8601-pvm-gen)))
(s/def ::viimeinen-katkos (s/with-gen (s/nilable (s/and string?
                                                        #(pvm/pvm? (tf/parse %))))
                                      #(iso8601-pvm-gen)))
(s/def ::yhteyskatkokset (s/coll-of (s/keys :req-un [::katkokset]
                                            :opt-un [::palvelu ::ensimmainen-katkos ::viimeinen-katkos])
                                    :kind vector?))
(s/def ::yhteyskatkokset-lokitus-mappina (s/and (s/keys :req-un [::yhteyskatkokset]
                                                        :opt-un [::pvm ::kello ::kayttaja])
                                                #(-> (merge (first (:yhteyskatkokset %))
                                                            (dissoc % :yhteyskatkokset))
                                                     count
                                                     (>= 3))))
