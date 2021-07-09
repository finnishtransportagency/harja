(ns harja.tyokalut.dev-tyokalut
  "Tänne funktioita, jotka ovat hyödyllisiä devatessa, mutta ei tarvitse käyttää prodissa.
   Jos on käytetty tuota defn-tyokalu makroa, niin pitäisi olla turvallista vaikka jokin
   funktiokutsu jäisikin koodiin prodiin mentäessä"
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [harja.tyokalut.env :as env]
            [harja.kyselyt.konversio :as konv]))

(defonce ^{:private true
           :doc "Arvot, jotka haetaan environment:istä. Näiden tulisi siis olla saatavana jo käännösaikana"}
         envrionment
         {:dev? (true? (env/env "HARJA_DEV_YMPARISTO"))})

(defonce ^{:doc "Tässä pidetään runtime configuraatiota. Hyödyllinen REPL:in kanssa."}
         config
         (atom {:kirjoita-tiedostoon? true}))

(def dev-environment? (:dev? envrionment))

(defn merge-config! [conf]
  (swap! config merge conf))

(defn- luo-arity [fn-params]
  (let [args# (first fn-params)
        pre-post?# (and (map? (second fn-params))
                        (or (contains? (second fn-params) :pre)
                            (contains? (second fn-params) :post)))
        pre-post# (when pre-post?#
                    (second fn-params))
        body# (if pre-post?#
                (drop 2 fn-params)
                (rest fn-params))]
    (cond-> (list args#)
            (and dev-environment? pre-post?#) (concat [pre-post#])
            dev-environment? (concat body#))))

(defmacro defn-tyokalu
  "Luo annetun funktion. Funktion body luodaan vain, jos HARJA_DEV_YMPARISTO ympäristömuuttuja on true.
   Näin turhaan ajettavaa koodia ei synny tuotantoon."
  [& fn-params]
  (let [nimi (first fn-params)
        docstring? (string? (second fn-params))
        rest-fn-params (if docstring?
                         (drop 2 fn-params)
                         (rest fn-params))
        multiarity? (list? (first rest-fn-params))
        fn-body (if multiarity?
                  (map luo-arity rest-fn-params)
                  (luo-arity rest-fn-params))]
    `(defn ~nimi
       ~@(if docstring?
           (apply list
                  (second fn-params)
                  fn-body)
           fn-body))))

(defn- str-n [n st]
  (reduce str (repeat n st)))

(defn- viesti-*out*
  "Printtaa viestit *out* kanavaan. Output on muotoa:
   ##########
   #        #
   # rivi-1 #
   # ...    #
   # rivi-n #
   #        #
   ##########"
  [& viestit]
  (let [pisin-viesti (apply max (map count viestit))
        padding 1
        aloitus-ja-lopetus-rivi (str-n (+ (* 2 padding)
                                          2
                                          pisin-viesti)
                                       "#")
        tyhja-rivi (str "#"
                        (str-n (+ (* 2 padding)
                                  pisin-viesti)
                               " ")
                        "#")
        kirjoitettavat-rivit (reduce (fn [rivit viesti]
                                       (let [tyhjat-merkit (str-n (- pisin-viesti
                                                                     (count viesti))
                                                                  " ")]
                                         (conj rivit
                                               (str "#"
                                                    (str-n padding " ")
                                                    viesti
                                                    tyhjat-merkit
                                                    (str-n padding " ")
                                                    "#"))))
                                     []
                                     viestit)
        viesti (reduce (fn [koottu-viesti viesti]
                         (str koottu-viesti "\n" viesti))
                       (concat (cons aloitus-ja-lopetus-rivi
                                     (repeat padding tyhja-rivi))
                               kirjoitettavat-rivit
                               (repeat padding tyhja-rivi)
                               [aloitus-ja-lopetus-rivi]))]
    (println viesti)))

(defn-tyokalu kirjoita-tiedostoon
  "Kirjoittaa annetun Clojure datan .edn tiedostoon dev-resources/tmp kansioon"
  ([input tiedoston-nimi] (kirjoita-tiedostoon input tiedoston-nimi true))
  ([input tiedoston-nimi ylikirjoita?]
   (async/go
     (when (:kirjoita-tiedostoon? @config)
       (let [tiedoston-polku (str "dev-resources/tmp/" tiedoston-nimi ".edn")
             tiedosto-olemassa? (.exists (io/file tiedoston-polku))]
         (if (and tiedosto-olemassa?
                  (not ylikirjoita?))
           (viesti-*out* (str "TIEDOSTO: " tiedoston-polku " on jo olemassa")
                         "Ei ylikirjoiteta")
           (try (pprint/pprint input (io/writer tiedoston-polku))
                (catch Throwable t
                  (viesti-*out* (str "Datan kirjoittaminen tiedostoon: " tiedoston-polku " epäonnistui!"))))))))))

(defn-tyokalu lue-edn-tiedosto
  "Ottaa .edn tiedostopolun ja palauttaa Clojure datan.
   HUOM! .edn tiedosto luetaan muistiin, joten tiedoston sisätlämä
   datastruktuuri ei saisi olla aivan valtava."
  [tiedosto]
  (-> tiedosto slurp read-string))

(defn-tyokalu datan-tiedot
  "Palauttaa annetun datan hashit ja luokat mapissa"
  ([data] (datan-tiedot data nil))
  ([data {:keys [type-string?]}]
   (walk/postwalk (fn [x]
                    (if (map-entry? x)
                      x
                      (let [tyyppi (type x)]
                        {:tyyppi (if type-string? (str tyyppi) tyyppi)
                         :hash (konv/sha256 x)
                         :data x})))
                  data)))

(defn-tyokalu etsi-epakohta-datasta [payload lahetetty saatu]
  (let [parsimis-fn (fn [data]
                      (cond
                        (map? data) (mapv (fn [[k-data v-data]]
                                            [(:hash k-data) (:hash v-data)])
                                          data)
                        (sequential? data) (mapv :hash data)
                        :else (:hash data)))
        seuraava-datapoint (fn [data eridata-index hash toisen-hash]
                             (let [data (:data data)]
                               (cond
                                 (map? data) (let [k-tai-v (if (= (first hash) (first toisen-hash))
                                                             val
                                                             key)]
                                               (-> data seq (nth eridata-index) k-tai-v))
                                 (sequential? data) (nth data eridata-index)
                                 :else data)))]
    (loop [lahetetty lahetetty
           saatu saatu]
      (when-not (or (nil? lahetetty) (nil? saatu))
        (let [sama-hash? (= (:hash saatu) (:hash lahetetty))
              lahetetty-hash (parsimis-fn (:data lahetetty))
              saatu-hash (parsimis-fn (:data saatu))
              eridata-index (if (seqable? lahetetty-hash)
                              (count (take-while true? (map #(= %1 %2) lahetetty-hash saatu-hash))))]
          (if (and (not sama-hash?)
                   (= lahetetty-hash saatu-hash))
            [lahetetty
             saatu]
            (recur (seuraava-datapoint lahetetty eridata-index (get lahetetty-hash eridata-index) (get saatu-hash eridata-index))
                   (seuraava-datapoint saatu eridata-index (get saatu-hash eridata-index) (get lahetetty-hash eridata-index)))))))))