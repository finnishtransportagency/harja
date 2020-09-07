(ns harja.palvelin.tyokalut.komponentti-event
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.palvelin.tyokalut.interfact :as i]
            [taoensso.timbre :as log]))

(s/def ::tyyppi-spec #{:perus :viimeisin})
(s/def ::event-spec (s/or :keyword keyword?
                          :string string?))

(defn- komponentti-event-parametrien-alustus []
  {:eventit (atom {})
   :perus-broadcast (i/perus-broadcast ::event)
   :viimeisin-broadcast (i/viimeisin-broadcast ::event)})

(defmulti kuuntelija!
          (fn [tyyppi _ _ _]
            tyyppi))

(defmethod kuuntelija! :perus
  [_ {bc :perus-broadcast} event kuuntelija-kanava]
  (async/sub (.broadcast bc) event kuuntelija-kanava)
  (swap! (.subscribers bc) conj kuuntelija-kanava))

(defmethod kuuntelija! :viimeisin
  [_ {bc :viimeisin-broadcast} event kuuntelija-kanava]
  (when-let [arvo (get @(.cache bc) event)]
    (async/put! kuuntelija-kanava arvo))
  (async/sub (.broadcast bc) event kuuntelija-kanava)
  (swap! (.subscribers bc) conj kuuntelija-kanava))

(defrecord KomponenttiEvent [eventit perus-broadcast viimeisin-broadcast]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    (doseq [bc [perus-broadcast viimeisin-broadcast]]
      (doseq [sub @(.subscribers bc)]
        (async/close! sub))
      (reset! (.subscribers bc) [])
      (async/close! (.kanava bc)))
    (reset! (.cache viimeisin-broadcast) nil)
    (reset! (:eventit this) {})
    (merge this
           (komponentti-event-parametrien-alustus)))

  i/IEvent
  (lisaa-jono [this event tyyppi]
    (swap! eventit
           (fn [eventit]
             (assoc eventit event tyyppi)))
    this)
  (lisaa-jono [this event]
    (i/lisaa-jono this event :perus))
  (eventin-kuuntelija [this event]
    (when-let [eventin-tyyppi (get @eventit event)]
      (let [kuuntelija-kanava (async/chan 1000
                                          (map (fn [v]
                                                 (log/debug (str "[KOMPONENTTI-EVENT] Saatiin tiedot\n"
                                                                 "  event: " event "\n"
                                                                 "  tiedot: " v))
                                                 (::data v)))
                                          (fn [t]
                                            (log/error t (str "Kuuntelija kanavassa error eventille " event))))]
        (kuuntelija! eventin-tyyppi this event kuuntelija-kanava)
        kuuntelija-kanava)))
  (julkaise-event [_ event data]
    (let [julkaisu-kanavan-tyyppi (get @eventit event)
          julkaisu-kanava (case julkaisu-kanavan-tyyppi
                            :perus (.kanava perus-broadcast)
                            :viimeisin (.kanava viimeisin-broadcast)
                            nil)]
      (if julkaisu-kanavan-tyyppi
        (boolean (async/put! julkaisu-kanava {::event event ::data data}))
        false))))

(def ^{:private true
       :doc "Halutaan luoda singleton KomponenttiEvent:istä, joten pakotetaan se ottamaan parametrinsa
                täältä. Tehdään tästä myös private, jotta atomin arvoja voidaan muokata vain KomponenttiEvent
                rekordin kautta."}
  komponentti-event-singleton
  (let [{:keys [eventit perus-broadcast viimeisin-broadcast]} (komponentti-event-parametrien-alustus)]
    (->KomponenttiEvent eventit perus-broadcast viimeisin-broadcast)))

(defn komponentti-event []
  komponentti-event-singleton)
