(ns harja.kyselyt.konversio-optimoitu
  "Optimoituja konversioita raskaisiin API kyselyihin, joissa muistin käyttö on kriittistä"
  (:require [clojure.string :as str]))


(def ^:private parsinta-str-cache
  ;; Cache jolla kasotaan ettei raskasta 
  ;; string splittiä tehdä kun kerran per uniikki avain
  (memoize
    (fn [k]
      (let [s (name k)]
        (if (str/includes? s "_")
          (mapv keyword (str/split s #"_"))
          [k])))))


(defn alaviiva->rakenne-nopea
  "Tekee alaviiva->rakenne sekä renamen, ilman että allokoidaan miljoonaa objektia muistiin."
  [m]
  (reduce-kv
    (fn [acc k v]
      (assoc-in acc (parsinta-str-cache k) v))
    {}
    m))


(defn rename-keys-opt
  "Sama kun clojure.set/rename-keys, mutta ilman overheadia."
  [m kmap]
  (reduce-kv
    (fn [acc k v]
      (assoc acc (get kmap k k) v))
    {}
    m))


(defn alaviiva-rename-opt
  "Sama kun rename-keys + alaviiva->rakenne,
   mutta non-transient (yksi läpikäynti) versio"
  [rivi rename-map]
  (reduce-kv
    (fn [acc k v]
      (let [avain (if (keyword? k) k (keyword k))
            uusi-avain (get rename-map avain k)]
        (assoc-in acc (parsinta-str-cache uusi-avain) v)))
    {}
    rivi))
