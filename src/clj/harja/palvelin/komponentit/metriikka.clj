(ns harja.palvelin.komponentit.metriikka
  "Tarjoaa metriikkaa tämän palvelininstanssin terveydentilasta"
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jmx :as jmx]
            [taoensso.timbre :as log]))

;; JMX raportoija ottaa custom metriikat tämän alta, joten
;; käytetään sitä prefixinä.
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
    (try
      (jmx/register-mbean
       (jmx/create-bean mittari-ref)
       (str +mbean-prefix+ nimi))
      (catch javax.management.InstanceAlreadyExistsException t
        (log/debug "mittari" nimi "oli jo olemassa")))))

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

(defn muuta!
  "Muuta monta mittariavainta samalla kertaa.
  Ottaa mittari-ref viittauksen sekä vaihtuvat
  avaimet ja muutosfunktiot. Avaimelle ajetaan update
  aina sitä vastaavalla muutosfunktiolla."
  [mittari-ref & avaimet-ja-muutosfunktiot]
  (dosync
   (alter mittari-ref
          #(reduce (fn [arvot [avain muutos-fn]]
                     (assoc arvot avain
                            (muutos-fn (get arvot avain))))
                   %
                   (partition 2 avaimet-ja-muutosfunktiot)))))

(defn with-counter-incremented [mittari-ref avain & body]
  `(let [ref# ~mittari-ref
         key# ~avain]
     (try
       (inc! ref# key#)
       (do ~@body)
       (finally
         (dec! ref# key#)))))
