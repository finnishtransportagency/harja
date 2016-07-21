(ns harja.tyokalut.makrot
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as log]))

(defmacro doseq-with-timeout [{:keys [timeout on-timed-out] :or {timeout 10000 on-timed-out nil}}
                              bindings & body]
  `(let [timeout# (+ (System/currentTimeMillis) ~timeout)]
     (try
       (doseq ~bindings
         (when (> (System/currentTimeMillis) timeout#)
           (throw (java.util.concurrent.TimeoutException.)))
         ~@body)
       (catch java.util.concurrent.TimeoutException t#
         ~on-timed-out))))

(defmacro go-loop-timeout
  "Lukee asioita annetusta kanavasta kuten go-loop, mutta lopettaa
  lukemisen annetun timeoutin jälkeen ja sulkee kanavan. Jos käsittelyssä
  tapahtuu virhe, se lokitetaan ja kanava suljetaan."
  [{:keys [timeout on-timed-out]
     :or {timeout 10000 on-timed-out nil}} [name chan-to-read-from] & body]
  `(let [timeout# (async/timeout ~timeout)
         chan# ~chan-to-read-from
         lue# #(async/alts!! [chan# timeout#])]
     (try
       (loop [[v# ch#] (lue#)]
         (if-not v#
           (do
             (async/close! chan#)
             (when (= ch# timeout#)
               (do
                 ;; Imetään tyhjäksi kanava, jotta pending puts
                 ;; menevät läpi.
                 (async/go-loop []
                     (when (async/<! chan#)
                       (recur)))
                 ~on-timed-out)))
           (let [~name v#]
             ~@body
             (recur (lue#)))))
       (catch Throwable t#
         (log/warn t# "Virhe go-loop-timeout lohkossa!")
         (async/close! chan#)))))
