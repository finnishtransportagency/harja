(ns harja.palvelin.komponentit.metriikka
  "Tarjoaa metriikkaa tämän palvelininstanssin terveydentilasta"
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jmx :as jmx]
            [taoensso.timbre :as log]))

(def +mbean-prefix+ "Catalina:type=DataSource,name=")

(defprotocol Metriikka
  (lisaa-mittari! [this nimi mittari-ref]
    "Lisää numerotyyppinen mittari, jota pollataan. Mittari-ref on Clojure ref, joka sisältää mäpin.
     Refin kaikki avaimet julkaistaan arvoina."))


(defrecord JmxMetriikka []
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    this)

  Metriikka
  (lisaa-mittari! [this nimi mittari-ref]
    (jmx/register-mbean
     (jmx/create-bean mittari-ref)
     (str +mbean-prefix+ nimi))))

(defn luo-jmx-metriikka []
  (->JmxMetriikka))


;; Apureita mittarit ref käyttämiseksi
(defn luo-mittari-ref [alkuarvot]
  (assert (map? alkuarvot))
  (ref alkuarvot))

(defn inc! [mittari-ref avain]
  (dosync
   (alter mittari-ref update avain inc)))

(defn dec! [mittari-ref avain]
  (dosync
   (alter mittari-ref update avain dec)))

(defn with-counter-incremented [mittari-ref avain & body]
  `(let [ref# ~mittari-ref
         key# ~avain]
     (try
       (inc! ref# key#)
       (do ~@body)
       (finally
         (dec! ref# key#)))))
