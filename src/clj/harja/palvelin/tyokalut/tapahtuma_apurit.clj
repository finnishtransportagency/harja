(ns harja.palvelin.tyokalut.tapahtuma-apurit
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.fmt :as fmt]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.tyokalut.predikaatti :as p]
            [taoensso.timbre :as log])
  (:import [java.net InetAddress]))

(def ^{:private true
       :dynamic true}
  *log-error* false)

(def host-nimi (fmt/leikkaa-merkkijono 512
                                       (.toString (InetAddress/getLocalHost))))

(defn tapahtuman-julkaisia!
  ([tapahtuma]
   (tapahtuman-julkaisia! tapahtuma :perus))
  ([tapahtuma tyyppi]
   {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
          (s/valid? ::tapahtumat/tyyppi tyyppi)]
    :post [(p/chan? %)]}
   (tapahtumat/tarkkaile! (tapahtumat/tarkkailija) tapahtuma tyyppi)))

(defn tapahtuman-kuuntelija!
  [tapahtuma f]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (ifn? f)]
   :post [(fn? f)]}
  (tapahtumat/kuuntele! (tapahtumat/tarkkailija) tapahtuma f))

(defn julkaise-tapahtuma
  [tapahtuma data]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (not (nil? data))]
   :post [(boolean? %)]}
  (tapahtumat/julkaise! (tapahtumat/tarkkailija) tapahtuma data host-nimi))

(defn tarkkaile [lopeta-tarkkailu-kanava timeout-ms f]
  (async/go
    (loop [[lopetetaan? _] (async/alts! [lopeta-tarkkailu-kanava]
                                        :default false)]
      (when-not lopetetaan?
        (f)
        (async/<! (async/timeout timeout-ms))
        (recur (async/alts! [lopeta-tarkkailu-kanava]
                            :default false))))))

(defn tarkkaile-tapahtumaa [tapahtuma tyyppi f & args]
  (let [kuuntelija (tapahtuman-julkaisia! tapahtuma tyyppi)]
    (when kuuntelija
      (async/go
        (loop [arvo (async/<! kuuntelija)]
          (apply f arvo args)
          (recur (async/<! kuuntelija)))))
    kuuntelija))

(defn lopeta-tapahtuman-kuuntelu [kuuntelija]
  (async/close! kuuntelija))

(defn tapahtuma-julkaisija
  "Palauttaa funktion, jolle annettava data julkaistaan aina tässä määritetylle eventille."
  [tapahtuma]
  (fn [data]
    (when *log-error*
      (log/error (str "Event: " tapahtuma " sai dataksi: " data)))
    (julkaise-tapahtuma tapahtuma data)))

(defn tapahtuma-datan-spec
  "Tarkoitettu wrapperiksi event-julkaisija:lle. Julkaistavaksi tarkoitettu data ensin validoidaan tälle annettua spekkiä vasten.
   Jos validointi epäonnistuu, julkaistavaksi dataksi laitetaan {::validointi-epaonnistui [ feilannu data ]}, lisäksi virhe logitetaan."
  [tapahtuma-julkaisija spec]
  (fn [data]
    (if (s/valid? spec data)
      (tapahtuma-julkaisija data)
      (binding [*log-error* true]
        (tapahtuma-julkaisija {::validointi-epaonnistui data})))))
