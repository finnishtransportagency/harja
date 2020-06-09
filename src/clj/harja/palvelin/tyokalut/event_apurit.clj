(ns harja.palvelin.tyokalut.event-apurit
  (:require [clojure.core.async :as async]
            [harja.palvelin.tyokalut.interfact :as i])
  (:import (harja.palvelin.tyokalut.komponentti_event KomponenttiEvent)))

(defn lisaa-aihe!
  ([this aihe]
   {:pre [(instance? KomponenttiEvent this)
          (ifn? aihe)]}
   (i/lisaa-aihe this aihe))
  ([this aihe aihe-fn]
   {:pre [(instance? KomponenttiEvent this)
          (ifn? aihe-fn)]
    :post [(instance? KomponenttiEvent %)]}
   (i/lisaa-aihe this aihe aihe-fn)))

(defn eventin-kuuntelija!
  [this aihe event]
  {:pre [(instance? KomponenttiEvent this)
         (or (string? event)
             (keyword? event))]}
  (i/eventin-kuuntelija this aihe event))

(defn julkaise-event
  [this aihe event data]
  {:pre [(instance? KomponenttiEvent this)
         (or (string? event)
             (keyword? event))
         (not (nil? data))]
   :post [(boolean? %)]}
  (i/julkaise-event this aihe event data))

(defn event-julkaisija [this aihe]
  (fn [nimi data]
    (julkaise-event this aihe nimi data)))

(defn tarkkaile [lopeta-tarkkailu-kanava timeout-ms f]
  (async/go
    (loop [[lopetetaan? _] (async/alts! [lopeta-tarkkailu-kanava]
                                        :default false)]
      (when-not lopetetaan?
        (f)
        (async/<! (async/timeout timeout-ms))
        (recur (async/alts! [lopeta-tarkkailu-kanava]
                            :default false))))))

(defn kuuntele-eventtia [this aihe event f & args]
  (let [kuuntelija (eventin-kuuntelija! this aihe event)]
    (async/go
      (loop [arvo (async/<! kuuntelija)]
        (apply f arvo args)
        (recur (async/<! kuuntelija))))
    kuuntelija))

(defn lopeta-eventin-kuuntelu [kuuntelija]
  (async/close! kuuntelija))