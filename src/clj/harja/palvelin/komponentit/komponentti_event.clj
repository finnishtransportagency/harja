(ns harja.palvelin.komponentit.komponentti-event
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.walk :as walk]

            [harja.palvelin.tyokalut.event-apurit :as event-apurit]))

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
  event-apurit/IEvent
  (lisaa-aihe [this aihe aihe-fn]
    {:pre [(ifn? aihe-fn)]
     :post [(instance? KomponenttiEvent %)]}
    (swap! aiheet
           (fn [aiheet]
             ;; Tämä tarkistus pitää tehdä swap! sisällä, jotta idempotent käytös voidaan taata.
             ;; Useammasta threadista ajettaessa, swapin body saatetaan ajaa useaan otteeseen.
             (if (get aiheet aihe)
               aiheet
               (let [kanava (async/chan)]
                 (assoc aiheet aihe {::kanava kanava
                                     ::pub (async/pub kanava aihe-fn)})))))
    this)
  (lisaa-aihe [this aihe]
    (event-apurit/lisaa-aihe this aihe aihe))
  (eventin-kuuntelija [this aihe event]
    {:post [(instance? KomponenttiEvent %)]}
    (when (get @aiheet aihe)
      (let [kuuntelija (async/chan)]
        (swap! aiheet
               (fn [aiheet]
                 (update aiheet
                         aihe
                         (fn [{::keys [pub subs] :as aiheen-osat}]
                           (async/sub pub event kuuntelija)
                           (assoc aiheen-osat ::subs (conj subs kuuntelija))))))
        kuuntelija)))
  (julkaise-event [_ aihe nimi data]
    (if-let [julkaisu-kanava (get-in @aiheet [aihe ::kanava])]
      (do (async/put! julkaisu-kanava {nimi data})
          true)
      false)))

(defn komponentti-event []
  (->KomponenttiEvent komponentti-event-parametrit))