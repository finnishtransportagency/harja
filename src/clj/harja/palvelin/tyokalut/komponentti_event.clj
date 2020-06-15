(ns harja.palvelin.tyokalut.komponentti-event
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.palvelin.tyokalut.interfact :as i]
            [taoensso.timbre :as log])
  #_(:gen-class
    :main false
    :init init
    :post-init pinit
    :state state
    :constructors {[] []}
    ;:methods [[komponentti [] harja.palvelin.tyokalut.komponentti_event.KomponenttiEvent]]
    ))

(s/def ::tyyppi-spec #{:perus :viimeisin})
(s/def ::event-spec (s/or :keyword keyword?
                          :string string?))

(defn- komponentti-event-parametrien-alustus []
  {:eventit (atom {})
   :perus-broadcast (i/perus-broadcast ::event)
   :viimeisin-broadcast (i/viimeisin-broadcast ::event)})

#_(defonce ^{:private true} komponentti-event-parametrit (komponentti-event-parametrien-alustus))

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
    ;(alter-var-root komponentti-event-parametrit (komponentti-event-parametrien-alustus))
    (merge this
           ;komponentti-event-parametrit
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
    (when (get @eventit event)
      (let [kuuntelija-kanava (async/chan 1000
                                          (map (fn [v]
                                                 (log/debug (str "[KOMPONENTTI-EVENT] Saatiin tiedot\n"
                                                                 "  event: " event "\n"
                                                                 "  tiedot: " v))
                                                 (::data v)))
                                          (fn [t]
                                            (log/error t (str "Kuuntelija kanavassa error eventille " event))))]
        (kuuntelija! (get @eventit event) this event kuuntelija-kanava)
        kuuntelija-kanava)))
  (julkaise-event [_ event data]
    (let [julkaisu-kanavan-tyyppi (get @eventit event)
          julkaisu-kanava (case julkaisu-kanavan-tyyppi
                            :perus (.kanava perus-broadcast)
                            :viimeisin (.kanava viimeisin-broadcast))]
      (if julkaisu-kanavan-tyyppi
        (boolean (async/put! julkaisu-kanava {::event event ::data data}))
        false))))

(def foo 2)

(def ^{:private true
       :static true
       :doc "Halutaan luoda singleton KomponenttiEvent:istä, joten pakotetaan se ottamaan parametrinsa
                täältä. Tehdään tästä myös private, jotta atomin arvoja voidaan muokata vain KomponenttiEvent
                rekordin kautta."}
  komponentti-event-singleton
  #_nil
  (let [{:keys [eventit perus-broadcast viimeisin-broadcast]} (komponentti-event-parametrien-alustus)]
    (->KomponenttiEvent eventit perus-broadcast viimeisin-broadcast)))

#_(defn -init []
  [[] nil])

#_(defn -pinit [this]
  (when (nil? komponentti-event-singleton)
    (let [{:keys [eventit perus-broadcast viimeisin-broadcast]} (komponentti-event-parametrien-alustus)]
      (alter-var-root komponentti-event-singleton (->KomponenttiEvent eventit perus-broadcast viimeisin-broadcast)))))

#_(defn -komponentti [this]
  (.state this))

(defn komponentti-event []
  #_(when (nil? komponentti-event-singleton)
    (let [{:keys [eventit perus-broadcast viimeisin-broadcast]} (komponentti-event-parametrien-alustus)]
      (alter-var-root komponentti-event-singleton (->KomponenttiEvent eventit perus-broadcast viimeisin-broadcast))))
  #_(new harja.palvelin.tyokalut.komponentti_event)
  komponentti-event-singleton)
