(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [thread timeout chan <!! >!!] :as async]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [clj-time.coerce :as tc]
            [harja.fmt :as fmt]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.palvelin.komponentit.sonja :as sonja-ns]
            [harja.pvm :as pvm])
  (:import (java.net InetAddress)))

(defonce ^{:doc "Pidetään kaikkien seurattavien komponenttien tila täällä"}
         komponenttien-tila
         (atom {}))

(defn olion-tila-aktiivinen? [tila]
  (= tila "ACTIVE"))

(defn jono-ok? [jonon-tiedot]
  (let [{:keys [tuottaja vastaanottaja]} (first (vals jonon-tiedot))
        tuottajan-tila-ok? (when tuottaja
                             (olion-tila-aktiivinen? (:tuottajan-tila tuottaja)))
        vastaanottajan-tila-ok? (when vastaanottaja
                                  (olion-tila-aktiivinen? (:vastaanottajan-tila vastaanottaja)))]
    (every? #(not (false? %))
            [tuottajan-tila-ok? vastaanottajan-tila-ok?])))

(defn istunto-ok? [{:keys [jonot istunnon-tila]}]
  (and (olion-tila-aktiivinen? istunnon-tila)
       (not (empty? jonot))
       (every? jono-ok?
               jonot)))

(defn sonjayhteys-ok?
  [{:keys [istunnot yhteyden-tila]}]
  (and (olion-tila-aktiivinen? yhteyden-tila)
       (not (empty? istunnot))
       (every? istunto-ok?
               istunnot)))

(defn sonjayhteydet-kannasta-ok? [tilat]
  (and (not (empty? tilat))
       (every? (fn [{:keys [tila paivitetty]}]
                 (and (sonjayhteys-ok? (:olioiden-tilat tila))
                      (pvm/ennen? (tc/to-local-date-time (pvm/sekunttia-sitten 20))
                                  (tc/to-local-date-time paivitetty))))
               tilat)))

(defn- aloita-sonjan-tarkkailu! [db sonja lopeta-tarkkailu-kanava]
  (thread
    (loop [[lopetetaan? _] (async/alts!! [lopeta-tarkkailu-kanava]
                                         :default false)]
      (when-not lopetetaan?
        (try
          (let [jms-tila (:vastaus (<!! (sonja-ns/kasky sonja {:jms-tilanne nil})))]
            (swap! komponenttien-tila
                   (fn [kt]
                     (-> kt
                         (assoc :sonja jms-tila)
                         (assoc-in [:sonja :kaikki-ok?] (sonjayhteys-ok? (:olioiden-tilat jms-tila))))))
            (q/tallenna-sonjan-tila<! db {:tila (cheshire/encode jms-tila)
                                          :palvelin (fmt/leikkaa-merkkijono 512
                                                                            (.toString (InetAddress/getLocalHost)))
                                          :osa-alue "sonja"}))
          (catch Throwable t
            (swap! komponenttien-tila
                   (fn [kt]
                     (assoc kt :sonja {:kaikki-ok? false})))
            (log/error (str "Jms tilan lukemisessa virhe: " (.getMessage t) "\nStackTrace: " (.printStackTrace t)))))
        (<!! (timeout 10000))
        (recur (async/alts!! [lopeta-tarkkailu-kanava]
                             :default false))))))

(defn- lopeta-sonjan-tarkkailu! [lopeta-tarkkailu-kanava]
  (>!! lopeta-tarkkailu-kanava true))

(defrecord KomponentinTila [sonja-lopeta-tarkkailu-kanava]
  component/Lifecycle
  (start [{:keys [db db-replica sonja]}]
    (aloita-sonjan-tarkkailu! db sonja sonja-lopeta-tarkkailu-kanava)
    (aloita-db-tarkkailu! db)
    (aloita-db-replican-tarrkailu! db db-replica))
  (stop [this]
    (lopeta-sonjan-tarkkailu! sonja-lopeta-tarkkailu-kanava)
    (lopeta-db-tarkkailu!)
    (lopeta-db-replican-tarkkailu!)
    (tyhjenna-cache!)))

(defn komponentin-tila []
  (->KomponentinTila (chan)))
