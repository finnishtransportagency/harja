(ns harja.palvelin.tyokalut.event-apurit
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.palvelin.tyokalut.interfact :as i]
            [harja.palvelin.tyokalut.komponentti-event :as ke])
  (:import (harja.palvelin.tyokalut.komponentti_event KomponenttiEvent)))

(defn lisaa-jono!
  ([this event]
   {:pre [(instance? KomponenttiEvent this)
          (s/valid? ::ke/event-spec event)]
    :post [(instance? KomponenttiEvent %)]}
   (i/lisaa-jono this event))
  ([this event tyyppi]
   {:pre [(instance? KomponenttiEvent this)
          (s/valid? ::ke/event-spec event)
          (s/valid? ::ke/tyyppi-spec tyyppi)]
    :post [(instance? KomponenttiEvent %)]}
   (i/lisaa-jono this event tyyppi)))

(defn eventin-kuuntelija!
  [this event]
  {:pre [(instance? KomponenttiEvent this)
         (s/valid? ::ke/event-spec event)]}
  (i/eventin-kuuntelija this event))

(defn julkaise-event
  [this event data]
  {:pre [(instance? KomponenttiEvent this)
         (s/valid? ::ke/event-spec event)
         (not (nil? data))]
   :post [(boolean? %)]}
  (i/julkaise-event this event data))

(defn event-julkaisija [this event]
  (fn [data]
    (julkaise-event this event data)))

(defn tarkkaile [lopeta-tarkkailu-kanava timeout-ms f]
  (async/go
    (loop [[lopetetaan? _] (async/alts! [lopeta-tarkkailu-kanava]
                                        :default false)]
      (when-not lopetetaan?
        (f)
        (async/<! (async/timeout timeout-ms))
        (recur (async/alts! [lopeta-tarkkailu-kanava]
                            :default false))))))

(defn kuuntele-eventtia [this event f & args]
  (println (str "----> KUUNTELE EVENTTIÄ: " event))
  (let [kuuntelija (eventin-kuuntelija! this event)]
    (when kuuntelija
      (async/go
        (println (str "----> ALOITETAAN KUUNTELEMAAN EVENTTIÄ: " event))
        (loop [arvo (async/<! kuuntelija)]
          (println (str "----> EVENTISTÄ: " event " SAATIIN ARVO: " arvo))
          (apply f arvo args)
          (recur (async/<! kuuntelija)))))
    kuuntelija))

(defn lopeta-eventin-kuuntelu [kuuntelija]
  (async/close! kuuntelija))