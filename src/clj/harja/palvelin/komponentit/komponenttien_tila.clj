(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [thread timeout chan <!! >!!] :as async]
            [clojure.java.jdbc :as jdbc]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [clj-time.coerce :as tc]
            [harja.fmt :as fmt]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.palvelin.komponentit.sonja :as sonja-ns]
            [harja.pvm :as pvm]
            [harja.tyokalut.muunnos :as muunnos])
  (:import (java.net InetAddress)))

(defonce ^{:doc "Pidetään kaikkien seurattavien komponenttien tila täällä"}
         komponenttien-tila
         (atom {}))

(defn- tarkkaile [lopeta-tarkkailu-kanava timeout-ms f]
  (thread
    (loop [[lopetetaan? _] (async/alts!! [lopeta-tarkkailu-kanava]
                                         :default false)]
      (when-not lopetetaan?
        (f)
        (<!! (timeout timeout-ms))
        (recur (async/alts!! [lopeta-tarkkailu-kanava]
                             :default false))))))

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
  (boolean
    (and (olion-tila-aktiivinen? yhteyden-tila)
         (not (empty? istunnot))
         (every? istunto-ok?
                 istunnot))))

(defn sonjayhteydet-kannasta-ok? [tilat]
  (and (not (empty? tilat))
       (every? (fn [{:keys [tila paivitetty]}]
                 (and (sonjayhteys-ok? (:olioiden-tilat tila))
                      (pvm/ennen? (tc/to-local-date-time (pvm/sekunttia-sitten 20))
                                  (tc/to-local-date-time paivitetty))))
               tilat)))

(defn- tallenna-sonjan-tila-cacheen [jms-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (-> kt
               (assoc :sonja jms-tila)
               (assoc-in [:sonja :kaikki-ok?] (sonjayhteys-ok? (:olioiden-tilat jms-tila)))))))

(defn- tallenna-sonjan-tila-kantaan [db jms-tila]
  (q/tallenna-sonjan-tila<! db {:tila (cheshire/encode jms-tila)
                                :palvelin (fmt/leikkaa-merkkijono 512
                                                                  (.toString (InetAddress/getLocalHost)))
                                :osa-alue "sonja"}))

(defn- aloita-sonjan-tarkkailu! [{:keys [paivitys-tiheys-ms]} db sonja lopeta-tarkkailu-kanava]
  (tarkkaile lopeta-tarkkailu-kanava
             paivitys-tiheys-ms
             (fn []
               (try
                 (let [jms-tila (:vastaus (<!! (sonja-ns/kasky sonja {:jms-tilanne nil})))]
                   (tallenna-sonjan-tila-cacheen jms-tila)
                   (tallenna-sonjan-tila-kantaan db jms-tila))
                 (catch Throwable t
                   (tallenna-sonjan-tila-cacheen nil)
                   (log/error (str "Jms tilan lukemisessa virhe: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))))))))

(defn- tallenna-dbn-tila-cacheen [kanta-ok?]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [:db :kaikki-ok?] kanta-ok?))))

(defn- aloita-db-tarkkailu! [{:keys [paivitys-tiheys-ms kyselyn-timeout-ms]} db lopeta-tarkkailu-kanava]
  (tarkkaile lopeta-tarkkailu-kanava
             paivitys-tiheys-ms
             (fn []
               (try (with-open [c (.getConnection (:datasource db))
                                stmt (jdbc/prepare-statement c
                                                             "SELECT 1;"
                                                             {:timeout (muunnos/ms->s kyselyn-timeout-ms)
                                                              :result-type :forward-only
                                                              :concurrency :read-only})
                                rs (.executeQuery stmt)]
                      (let [kanta-ok? (if (.next rs)
                                        (= 1 (.getObject rs 1))
                                        false)]
                        (tallenna-dbn-tila-cacheen kanta-ok?)))
                    (catch Throwable _
                      (tallenna-dbn-tila-cacheen false))))))

(defn- lopeta-sonjan-tarkkailu! [lopeta-tarkkailu-kanava]
  (>!! lopeta-tarkkailu-kanava true))

(defn- lopeta-db-tarkkailu! [lopeta-tarkkailu-kanava]
  (>!! lopeta-tarkkailu-kanava true))

(defn- tyhjenna-cache! []
  (reset! komponenttien-tila nil))

(defrecord KomponentinTila [asetukset sonja-lopeta-tarkkailu-kanava db-lopeta-tarkkailu-kanava]
  component/Lifecycle
  (start [{:keys [db db-replica sonja]}]
    (aloita-sonjan-tarkkailu! (:sonja asetukset) db sonja sonja-lopeta-tarkkailu-kanava)
    (aloita-db-tarkkailu! (:db asetukset) db db-lopeta-tarkkailu-kanava)
    (aloita-db-replican-tarrkailu! db db-replica))
  (stop [this]
    (lopeta-sonjan-tarkkailu! sonja-lopeta-tarkkailu-kanava)
    (lopeta-db-tarkkailu! db-lopeta-tarkkailu-kanava)
    (lopeta-db-replican-tarkkailu!)
    (tyhjenna-cache!)))

(defn komponentin-tila [asetukset]
  (->KomponentinTila asetukset (chan) (chan)))
