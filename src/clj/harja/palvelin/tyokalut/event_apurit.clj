(ns harja.palvelin.tyokalut.event-apurit
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.palvelin.tyokalut.interfact :as i]
            [harja.palvelin.tyokalut.komponentti-event :as ke]
            [taoensso.timbre :as log]))

(def ^{:private true
       :dynamic true}
  *log-error* false)

(defn lisaa-jono!
  ([this event]
   {:pre [;(satisfies? i/IEvent this)
          (s/valid? ::ke/event-spec event)
          ]
    :post [(satisfies? i/IEvent %)]}
   (i/lisaa-jono this event))
  ([this event tyyppi]
   {:pre [;(satisfies? i/IEvent this)
          (s/valid? ::ke/event-spec event)
          (s/valid? ::ke/tyyppi-spec tyyppi)]
    :post [(satisfies? i/IEvent %)]}
   (i/lisaa-jono this event tyyppi)))

(defn eventin-kuuntelija!
  [this event]
  {:pre [;(satisfies? i/IEvent this)
         (s/valid? ::ke/event-spec event)]}
  (i/eventin-kuuntelija this event))

(defn julkaise-event
  [this event data]
  {:pre [;(satisfies? i/IEvent this)
         (s/valid? ::ke/event-spec event)
         (not (nil? data))]
   :post [(boolean? %)]}
  (i/julkaise-event this event data))

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
  (let [kuuntelija (eventin-kuuntelija! this event)]
    (when kuuntelija
      (async/go
        (loop [arvo (async/<! kuuntelija)]
          (apply f arvo args)
          (recur (async/<! kuuntelija)))))
    kuuntelija))

(defn lopeta-eventin-kuuntelu [kuuntelija]
  (async/close! kuuntelija))

(defn event-julkaisija
  "Palauttaa funktion, jolle annettava data julkaistaan aina tässä määritetylle eventille."
  [this event]
  (fn [data]
    (when *log-error*
      (log/error (str "Event: " event " sai dataksi: " data)))
    (julkaise-event this event data)))

(defn event-datan-spec
  "Tarkoitettu wrapperiksi event-julkaisija:lle. Julkaistavaksi tarkoitettu data ensin validoidaan tälle annettua spekkiä vasten.
   Jos validointi epäonnistuu, julkaistavaksi dataksi laitetaan {::validointi-epaonnistui [ feilannu data ]}, lisäksi virhe logitetaan."
  [event-julkaisija spec]
  (fn [data]
    (if (s/valid? spec data)
      (event-julkaisija data)
      (binding [*log-error* true]
        (event-julkaisija {::validointi-epaonnistui data})))))
