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

(defn aseta-status! [komponentti status-komponentti koodi viesti]
  (swap! (:status status-komponentti) update komponentti assoc :status koodi :viesti viesti))

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
      (aseta-status! :db status-komponentti 503 (str "Ei saatu yhteyttä kantaan " timeout-s " sekunnin kuluessa.")))
    {:yhteys-master-kantaan-ok? yhteys-ok?}))

(defn replikoinnin-tila! [status-komponentti db-replica]
  (let [replikoinnin-viive (q/hae-replikoinnin-viive db-replica)
        replikoinnin-tila-ok? (not (and replikoinnin-viive (> replikoinnin-viive 100)))]
    (when-not replikoinnin-tila-ok?
      (aseta-status! :db-replica status-komponentti 503 (str "Replikoinnin viive on liian suuri: " replikoinnin-viive)))
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
      (aseta-status! :sonja status-komponentti 503 (str "Ei saatu yhteyttä Sonjaan " (/ timeout-ms 1000) " sekunnin kuluessa.")))
    {:sonja-yhteys-ok? yhteys-ok?}))

(defn status-ja-viesti [status-komponentti testattavat-komponentit]
  (let [tilanne @(:status status-komponentti)
        tilanteen-koonti (reduce-kv (fn [{edellinen-status :status
                                          edelliset-viestit :viesti :as koottu} k {:keys [status viesti]}]
                                      (if (contains? testattavat-komponentit k)
                                        (assoc koottu
                                               :status (if (= edellinen-status status 200)
                                                         edellinen-status
                                                         (max status edellinen-status))
                                               :viesti (if viesti
                                                         (conj edelliset-viestit viesti)
                                                         edelliset-viestit))
                                        koottu))
                                    {:status 200 :viesti []}
                                    tilanne)]
    (update tilanteen-koonti
            :viesti
            (fn [viestit]
              (apply str (interpose "\n" viestit))))))

(defrecord Status [status kehitysmoodi?]
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           db-replica :db-replica
           :as this}]
    (http-palvelin/julkaise-reitti
     http :status
     (GET "/status" _
          (let [testit (merge
                         (tietokannan-tila! this db)
                         (replikoinnin-tila! this db-replica)
                         (sonja-yhteyden-tila! this db kehitysmoodi?))
                {:keys [status viesti]} (status-ja-viesti this #{:db :db-replica :sonja :harja})]
            {:status status
             :headers {"Content-Type" "application/json; charset=UTF-8"}
             :body (encode
                    (merge {:viesti viesti}
                           testit))})))
    (http-palvelin/julkaise-reitti
      http :app-status
      (GET "/app_status" _
        (let [{:keys [status viesti]} (status-ja-viesti this #{:harja})]
          {:status status
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (encode
                   {:viesti viesti})})))
    #_(http-palvelin/julkaise-reitti
      http :db-status
      (GET "/db_status" _
        (let [testit (merge
                       (tietokannan-tila! this db)
                       (replikoinnin-tila! this db-replica))
              {:keys [status viesti]} (status-ja-viesti this #{:db :db-replica :harja})]
          {:status status
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (encode
                   (merge {:viesti viesti}
                          testit))})))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :status)
    this))

(defn luo-status [kehitysmoodi?]
  (->Status (atom {:harja {:status 503 :viesti "Harja käynnistyy"}}) kehitysmoodi?))
