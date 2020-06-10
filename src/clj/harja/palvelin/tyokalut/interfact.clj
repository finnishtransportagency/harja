(ns harja.palvelin.tyokalut.interfact
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as log]))

(defprotocol IEvent
  (lisaa-jono [this event] [this aihe tyyppi] "Lisää eventin, jota voidaan kuunnella")
  (eventin-kuuntelija [this event] "")
  (julkaise-event [this event data] "Julkaise 'data' aiheeseeen 'nimi'"))

(deftype PerusBroadcast [kanava broadcast subscribers])
(deftype ViimeisinBroadcast [kanava broadcast subscribers cache])

(defn perus-broadcast
  "Broadcast, joka lähettää eventin kaikille, jotka siihen ovat subscribanneet."
  [event-key]
  (let [kanava (async/chan (async/sliding-buffer 1000))]
    (->PerusBroadcast kanava
                      (async/pub kanava event-key (constantly (async/sliding-buffer 1000)))
                      (atom []))))
(defn viimeisin-broadcast
  "Broadcast, joka lähettää eventin kaikille, jotka siihen ovat subscribanneet.
   Lähettää myös viimesimmän arvonsa uusille subscribtioneille"
  [event-key]
  (let [cache (atom nil)
        kanava (async/chan (async/sliding-buffer 1000)
                           (map (fn [arvo]
                                  (println "--> KANAVAAN TULI ARVO: " arvo)
                                  (swap! cache
                                         (fn [kakutetut-arvot]
                                           (assoc kakutetut-arvot (get arvo event-key) arvo)))
                                  (println "ARVO KAKUTETTU!")
                                  arvo))
                           (fn [t]
                             (log/error t "viimeisin-broadcast kakuttamisessa tapahtui virhe!")))]
    (->ViimeisinBroadcast kanava
                          (async/pub kanava event-key (constantly (async/sliding-buffer 1000)))
                          (atom [])
                          cache)))
