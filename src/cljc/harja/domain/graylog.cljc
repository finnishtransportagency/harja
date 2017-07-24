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
(s/def ::palvelut-havainto (s/with-gen (s/nilable string?)
                                       #(palvelu-gen)))
(s/def ::palvelut (s/coll-of ::palvelut-havainto :kind vector?))
(s/def ::katkokset-havainto (s/nilable (s/and integer? pos?)))
(s/def ::katkokset (s/coll-of ::katkokset-havainto :kind vector?))
(s/def ::ensimmaiset-katkokset-havainto (s/with-gen (s/nilable (s/and string?
                                                                      #(pvm/pvm? (tf/parse %))))
                                                    #(iso8601-pvm-gen)))
(s/def ::ensimmaiset-katkokset (s/coll-of ::ensimmaiset-katkokset-havainto :kind vector?))
(s/def ::viimeiset-katkokset-havainto (s/with-gen (s/nilable (s/and string?
                                                                    #(pvm/pvm? (tf/parse %))))
                                                  #(iso8601-pvm-gen)))
(s/def ::viimeiset-katkokset (s/coll-of ::viimeiset-katkokset-havainto :kind vector?))
(s/def ::yhteyskatkokset (s/coll-of (s/keys :req-un [::katkokset-havainto]
                                            :opt-un [::palvelut-havainto ::ensimmaiset-katkokset-havainto ::viimeiset-katkokset-havainto])
                                    :kind vector?))
(s/def ::parsittu-yhteyskatkos-data-itemi (s/and (s/with-gen (s/keys :req-un [::yhteyskatkokset]
                                                                     :opt-un [::pvm ::kello ::kayttaja])
                                                             #(let [opt-avaimet #{:pvm :kello :kayttaja :palvelut :ensimmaiset-katkokset :viimeiset-katkokset}
                                                                    vec-arvot #{:palvelut :ensimmaiset-katkokset :viimeiset-katkokset}
                                                                    ensimmainen-opt-avain (rand-nth (into '() opt-avaimet))
                                                                    toinen-opt-avain (rand-nth (into '() (disj opt-avaimet ensimmainen-opt-avain)))
                                                                    rand-numero (rand-int 10)
                                                                    apu-fn (fn [avain]
                                                                            (if (vec-arvot avain)
                                                                              (s/coll-of (keyword "harja.domain.graylog" (str (name avain) "-havainto"))
                                                                                         :kind vector? :count rand-numero)
                                                                              (keyword "harja.domain.graylog" (name avain))))]
                                                                  (apply gen/hash-map :katkokset (s/gen (s/coll-of ::katkokset-havainto :kind vector? :count rand-numero))
                                                                                      ensimmainen-opt-avain (s/gen (apu-fn ensimmainen-opt-avain))
                                                                                      toinen-opt-avain (s/gen (apu-fn toinen-opt-avain))
                                                                                      (mapcat (fn [x] [x (s/gen (apu-fn x))])
                                                                                              (random-sample 0.5 (disj opt-avaimet ensimmainen-opt-avain toinen-opt-avain))))))
                                                 #(apply = (keep (fn [x]
                                                                  (when (not (nil? x))
                                                                    (count x)))
                                                                 [(:palvelut %) (:katkokset %) (:ensimmaiset-katkokset %) (:viimeiset-katkokset %)]))
                                                 #(-> % count (>= 3))))
(s/def ::parsittu-yhteyskatkos-data (s/and (s/coll-of ::parsittu-yhteyskatkos-data-itemi :kind seq?)
                                           #(apply = (map keys %))))
