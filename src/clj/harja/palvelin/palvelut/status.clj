(ns harja.palvelin.palvelut.status
  (:require [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [harja.palvelin.palvelut.jarjestelman-tila :as jarjestelman-tila]
            [cheshire.core :refer [encode]]
            [harja.kyselyt.status :as q]
            [clojure.core.async :as async :refer [<! go-loop]]
            [clojure.java.jdbc :as jdbc])
  (:import (com.mchange.v2.c3p0 C3P0ProxyConnection)))

(defn aseta-status! [status koodi viesti]
  (swap! (:status status) assoc :status koodi :viesti viesti))

(defn aseta-kaynnistynyt! [status kaynnistynyt?]
  (swap! (:status status) assoc :kaynnistynyt? kaynnistynyt?))

(defn tietokannan-tila! [status-komponentti db]
  (let [timeout-s 15
        yhteys-ok? (with-open [c (.getConnection (:datasource db))
                               stmt (jdbc/prepare-statement c
                                                            "SELECT 1;"
                                                            {:timeout timeout-s
                                                             :result-type :forward-only
                                                             :concurrency :read-only})
                               rs (.executeQuery stmt)]
                     (if (.next rs)
                       (= 1 (.getObject rs 1))
                       false))]
    (when-not yhteys-ok?
      (aseta-status! status-komponentti 503 (str "Ei saatu yhteyttä kantaan " timeout-s " sekunnin kuluessa.")))
    {:yhteys-master-kantaan-ok? yhteys-ok?}))

(defn replikoinnin-tila! [status-komponentti db-replica]
  (let [replikoinnin-viive (q/hae-replikoinnin-viive db-replica)
        replikoinnin-tila-ok? (not (and replikoinnin-viive (> replikoinnin-viive 100)))]
    (when-not replikoinnin-tila-ok?
      (aseta-status! status-komponentti 503 (str "Replikoinnin viive on liian suuri: " replikoinnin-viive)))
    {:replikoinnin-tila-ok? replikoinnin-tila-ok?}))

(defn sonja-yhteyden-tila! [status-komponentti db kehitysmoodi?]
  (let [timeout-ms 5000
        yhteys-ok? (first (async/alts!! [(go-loop [status-ok? (jarjestelman-tila/kaikki-yhteydet-ok? (jarjestelman-tila/hae-sonjan-tila db kehitysmoodi?))]
                                           (if status-ok?
                                             status-ok?
                                             (do (<! (async/timeout 1000))
                                                 (recur (jarjestelman-tila/kaikki-yhteydet-ok? (jarjestelman-tila/hae-sonjan-tila db kehitysmoodi?))))))
                                         (async/timeout timeout-ms)]))]
    (when-not yhteys-ok?
      (aseta-status! status-komponentti 503 (str "Ei saatu yhteyttä Sonjaan " (/ timeout-ms 1000) " sekunnin kuluessa.")))
    {:sonja-yhteys-ok? yhteys-ok?}))

(defrecord Status [status kehitysmoodi?]
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           db-replica :db-replica
           :as this}]
    (http-palvelin/julkaise-reitti
     http :status
     (GET "/status" _
          (let [testit (when (get @status :kaynnistynyt?)
                         (merge
                           (tietokannan-tila! this db)
                           (replikoinnin-tila! this db-replica)
                           (sonja-yhteyden-tila! this db kehitysmoodi?)))
                tila-palautunut-ok? (and (not= 200 (:status @status))
                                         (every? true? (vals testit)))
                _ (when tila-palautunut-ok?
                    (aseta-status! this 200 "Harja käynnistetty"))
                {:keys [status viesti]} @status]
            {:status status
             :headers {"Content-Type" "application/json; charset=UTF-8"}
             :body (encode
                    (merge {:viesti viesti}
                           testit))})))
    (http-palvelin/julkaise-reitti
      http :app-status
      (GET "/app_status" _
        (let [{:keys [status viesti] :as body} @status]
          {:status status
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (encode
                   {:viesti viesti})})))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :status)
    this))

(defn luo-status [kehitysmoodi?]
  (->Status (atom {:status 503 :viesti "Harja käynnistyy" :kaynnistynyt? false}) kehitysmoodi?))
