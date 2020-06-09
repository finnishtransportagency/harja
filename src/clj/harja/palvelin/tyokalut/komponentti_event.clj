(ns harja.palvelin.tyokalut.komponentti-event
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.walk :as walk]
            [harja.palvelin.tyokalut.interfact :as i]))

(defonce ^{:private true
           :doc "Halutaan luoda singleton KomponenttiEvent:istä, joten pakotetaan se ottamaan parametrinsa
                täältä. Tehdään tästä myös private, jotta atomin arvoja voidaan muokata vain KomponenttiEvent
                rekordin kautta."}
         komponentti-event-parametrit
         (atom {}))

(defrecord KomponenttiEvent [aiheet]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    (walk/postwalk @aiheet
                   (fn [x]
                     (when (map-entry? x)
                       (let [[k v] x]
                         (case k
                           ::subs (doseq [kanava v]
                                    (async/close! kanava))
                           ::kanava (async/close! v)
                           nil)))))
    (reset! komponentti-event-parametrit {})
    this)
  i/IEvent
  (lisaa-aihe [this aihe aihe-fn]
    (swap! aiheet
           (fn [aiheet]
             ;; Tämä tarkistus pitää tehdä swap! sisällä, jotta idempotent käytös voidaan taata.
             ;; Useammasta threadista ajettaessa, swapin body saatetaan ajaa useaan otteeseen.
             (if (get aiheet aihe)
               aiheet
               (let [kanava (async/chan (async/sliding-buffer 1000))]
                 (assoc aiheet aihe {::kanava kanava
                                     ::pub (async/pub kanava aihe-fn (constantly (async/sliding-buffer 1000)))})))))
    this)
  (lisaa-aihe [this aihe]
    (i/lisaa-aihe this aihe aihe))
  (eventin-kuuntelija [this aihe event]
    (when (get @aiheet aihe)
      (let [kuuntelija (async/chan 1 (map ::data))]
        (swap! aiheet
               (fn [aiheet]
                 (update aiheet
                         aihe
                         (fn [{::keys [pub subs] :as aiheen-osat}]
                           (async/sub pub event kuuntelija)
                           (assoc aiheen-osat ::subs (conj subs kuuntelija))))))
        kuuntelija)))
  (julkaise-event [_ aihe event data]
    (if-let [julkaisu-kanava (get-in @aiheet [aihe ::kanava])]
      (boolean (async/put! julkaisu-kanava {aihe event ::data data}))
      false)))

(defn komponentti-event []
  (->KomponenttiEvent komponentti-event-parametrit))
