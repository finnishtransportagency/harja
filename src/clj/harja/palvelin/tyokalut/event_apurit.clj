(ns harja.palvelin.tyokalut.event-apurit
  (:require [clojure.core.async :as async]))

(defprotocol IEvent
  (lisaa-aihe [this aihe] [this aihe aihe-fn] "Lisää aiheen, jota voidaan kuunnella")
  (eventin-kuuntelija [this aihe event] "")
  (julkaise-event [this aihe nimi data] "Julkaise 'data' aiheeseeen 'nimi'"))

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
  (let [kuuntelija (eventin-kuuntelija this aihe event)]
    (async/go
      (loop [arvo (async/<! kuuntelija)]
        (apply f arvo args)
        (recur (async/<! kuuntelija))))
    kuuntelija))

(defn lopeta-eventin-kuuntelu [kuuntelija]
  (async/close! kuuntelija))