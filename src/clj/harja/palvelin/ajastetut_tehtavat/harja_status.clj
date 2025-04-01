(ns harja.palvelin.ajastetut-tehtavat.harja-status
  "Tekee ajastetun status tarkistuksen kaikille komponenteille, joita tarvitsee vahtia."
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.palvelin.integraatiot.jms :as jms-util]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.status :as status-kyselyt]
            [harja.kyselyt.jarjestelman-tila :as jarjestelman-tila-kyselyt])
  (:import (java.net InetAddress)))

(def palvelimen-nimi
  (fmt/leikkaa-merkkijono 512
    (.toString (InetAddress/getLocalHost))))

(defn tarkista-tietokanta [db]
  (let [tulos (status-kyselyt/hae-tietokannan-tila db)
        status (if tulos "ok" "nok")
        _ (status-kyselyt/aseta-komponentin-tila<! db {:palvelin palvelimen-nimi
                                                       :komponentti "db"
                                                       :status status
                                                       :lisatiedot nil})]))

(defn tarkista-replica [db kehitysmoodi?]
  (let [db-on-aurora? (try
                        (status-kyselyt/db-on-aurora db)
                        (catch Exception e
                          false))
        viive (if db-on-aurora?
                (status-kyselyt/hae-replikoinnin-viive-aurora db)
                (status-kyselyt/hae-replikoinnin-viive db))
        status (cond
                 (not (ominaisuus-kaytossa? :replica-db)) "ei-kaytossa"
                 (and (ominaisuus-kaytossa? :replica-db) (not (nil? viive)) (not kehitysmoodi?) (> 5 viive)) "ok"
                 (and (ominaisuus-kaytossa? :replica-db) (not (nil? viive)) (not kehitysmoodi?) (> 10 viive)) "hidas"
                 (and (ominaisuus-kaytossa? :replica-db) (not kehitysmoodi?)) "nok"
                 kehitysmoodi? "ei-kaytossa")
        replica {:palvelin palvelimen-nimi
                 :komponentti "replica"
                 :status status
                 :lisatiedot nil}
        _ (status-kyselyt/aseta-komponentin-tila<! db replica)]))

(defn tarkista-itmf [db kehitysmoodi?]
  (let [itmfn-tilat (jarjestelman-tila-kyselyt/itmfn-tila db kehitysmoodi?)
        _ (doseq [tila itmfn-tilat]
            (let [lisatiedot (str (.getValue (:tila tila)))
                  status (if (str/includes? lisatiedot "ACTIVE")
                           "ok" "nok")
                  itmf {:palvelin (:palvelin tila)
                        :komponentti "itmf"
                        :status status
                        :lisatiedot lisatiedot}
                  _ (status-kyselyt/aseta-komponentin-tila<! db itmf)]))]))

(defn tarkista-harja-status [db kehitysmoodi?]
  (try
    (let [_ (tarkista-tietokanta db)
          _ (tarkista-replica db kehitysmoodi?)
          _ (tarkista-itmf db kehitysmoodi?)])
    (catch Exception e
      (log/error e (format "Harjan statusta ei voitu tarkistaa: %s" e)))))

(defn tarkista-async-threadpool
  "Lokittaa periodisesti clojure async threadpoolin tilan, mutta ei aseta statusta tietokantaan"
  []
  ;; Käynnistä ajastettu tehtävä minuutin päästä ja sen jälkeen 5 minuutin välein
  (ajastettu-tehtava/ajasta-minuutin-valein 5 60
    (fn [_]
      (let [threadpool-size (Long/getLong "clojure.core.async.pool-size")
            ;; https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.State.html
            ;;
            ;; Logittaa relevantit ajossa olevat async-threadit:
            ;;
            ;; RUNNABLE
            ;;   A thread executing in the Java virtual machine is in this state.
            ;; BLOCKED
            ;;   A thread that is blocked waiting for a monitor lock is in this state.
            ;; WAITING
            ;;   A thread that is waiting indefinitely for another thread to perform a particular action is in this state.
            ;; 
            states [:WAITING :RUNNABLE :BLOCKED]
            thread-stack (mapv bean (keys (Thread/getAllStackTraces)))
            thread-maara (fn [tyyppi state]
                           (->> 
                             thread-stack
                             (filter #(and (str/includes? (:name %) tyyppi)
                                        (= (:state %) (Thread$State/valueOf (name state)))))
                             (count)))]

        (doseq [state states]
          (log/info (str
                      "async-dispatch / poolin koko (" state ") : "
                      (thread-maara "async-dispatch" state) " / " threadpool-size)))
        
        (doseq [state states]
          (log/info (str
                      "async-thread-macro count (" state ") : " (thread-maara "async-thread-macro" state))))

        ;; Tulosta async-threadien tila debuggausta varten
        #_(clojure.pprint/pprint
            (->>
              (keys (Thread/getAllStackTraces))
              (mapv bean)
              (filter #(str/includes? (:name %) "async-dispatch-"))
              (mapv :state)))))))

(defn tarkista-status [db kehitysmoodi?]
  (log/debug (format "Tarkistetaan harjan status."))
  ;; Tätä ei tarkoituksella ajeta lukon kanssa, koska halutaan, että kaikilta appista pyörittäviltä palvelimilta, tehdään sama toimenpide
  (ajastettu-tehtava/ajasta-minuutin-valein
    1 1 ;; alkaa pyöriä 1 min 1 sekunnin kuluttua käynnistyksestä - ja sen jälkeen minuutin välein
    (fn [_] (tarkista-harja-status db kehitysmoodi?))))

(defn- poista-statusviestit [db]
  (status-kyselyt/poista-statusviestit! db))

(defn- ajastus-poista-statusviestit [db]
  (log/info "Ajastetaan statusviestien siivous - ajetaan kerran vuorokaudessa - poistetaan viikon kaikki yli kaksi päivää vanhat")
  (ajastettu-tehtava/ajasta-paivittain [0 55 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "status_viestit"
        #(do
           (log/info "ajasta-paivittain :: status_viestit :: Alkaa " (pvm/nyt))
           (poista-statusviestit db)
           (log/info "ajasta-paivittain :: status_viestit :: Loppuu " (pvm/nyt)))))))

(defrecord HarjaStatus [kehitysmoodi]
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this
      :harja-status (tarkista-status db kehitysmoodi)
      :poista-turhat-statusviestit (ajastus-poista-statusviestit db)
      :tarkista-async-threadpool (tarkista-async-threadpool)))
  (stop [{harja-status :harja-status
          poista-turhat-statusviestit :poista-turhat-statusviestit
          tarkista-async-threadpool :tarkista-async-threadpool :as this}]
    (do
      (harja-status)
      (poista-turhat-statusviestit)
      (tarkista-async-threadpool))
    (dissoc this
      :harja-status
      :poista-turhat-statusviestit
      :tarkista-async-threadpool)))
