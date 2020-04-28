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

(defn ms->s
  "Muuttaa millisekunnit sekunneiksi"
  [ms]
  {:pre [(integer? ms)]
   :post [(integer? %)]}
  (let [arvo (/ ms 1000)]
    (if (integer? arvo)
      arvo
      (Math/round arvo))))

(declare kasittele-status!)

(defn dbn-tila-ok?
  ([db] (dbn-tila-ok? db nil))
  ([db timeout-ms]
   {:pre [(instance? harja.palvelin.komponentit.tietokanta.Tietokanta db)
          (or (nil? timeout-ms) (integer? timeout-ms))]
    :post [(boolean? %)]}
   (let [timeout-ms (or timeout-ms 20000)
         timeout-s (ms->s timeout-ms)]
     (with-open [c (.getConnection (:datasource db))
                 stmt (jdbc/prepare-statement c
                                              "SELECT 1;"
                                              {:timeout timeout-s
                                               :result-type :forward-only
                                               :concurrency :read-only})
                 rs (.executeQuery stmt)]
       (if (.next rs)
         (= 1 (.getObject rs 1))
         false)))))

(defn replikoinnin-tila-ok?
  ([db-replica] (replikoinnin-tila-ok? db-replica nil))
  ([db-replica timeout-ms]
   {:pre [(instance? harja.palvelin.komponentit.tietokanta.Tietokanta db-replica)
          (or (nil? timeout-ms) (integer? timeout-ms))]
    :post [(boolean? %)]}
   (let [timeout-ms (or timeout-ms 100000)
         timeout-s (ms->s timeout-ms)
         replikoinnin-viive (q/hae-replikoinnin-viive db-replica)]
     (not (and replikoinnin-viive (> replikoinnin-viive timeout-s))))))

(defn sonja-yhteyden-tila-ok?
  ([db kehitysmoodi?] (sonja-yhteyden-tila-ok? db kehitysmoodi? nil))
  ([db kehitysmoodi? timeout-ms]
   {:pre [(instance? harja.palvelin.komponentit.tietokanta.Tietokanta db)
          (boolean? kehitysmoodi?)
          (or (nil? timeout-ms) (integer? timeout-ms))]
    :post [(boolean? %)]}
   (let [timeout-ms (or timeout-ms 10000)]
     (boolean
       (first (async/alts!! [(go-loop [status-ok? (jarjestelman-tila/kaikki-yhteydet-ok? (jarjestelman-tila/hae-sonjan-tila db kehitysmoodi?))]
                               (if status-ok?
                                 status-ok?
                                 (do (<! (async/timeout 1000))
                                     (recur (jarjestelman-tila/kaikki-yhteydet-ok? (jarjestelman-tila/hae-sonjan-tila db kehitysmoodi?))))))
                             (async/timeout timeout-ms)]))))))

(defn tietokannan-tila! [status-komponentti db]
  (let [timeout-ms 20000
        yhteys-ok? (dbn-tila-ok? db timeout-ms)]
    (kasittele-status! status-komponentti yhteys-ok? :db (str "Ei saatu yhteyttä kantaan " (ms->s timeout-ms) " sekunnin kuluessa."))
    {:yhteys-master-kantaan-ok? yhteys-ok?}))

(defn replikoinnin-tila! [status-komponentti db-replica]
  (let [timeout-ms 100000
        replikoinnin-tila-ok? (replikoinnin-tila-ok? db-replica timeout-ms)]
    (kasittele-status! status-komponentti replikoinnin-tila-ok? :db-replica (str "Replikoinnin viive on suurempi kuin " (ms->s timeout-ms) " sekunttia"))
    {:replikoinnin-tila-ok? replikoinnin-tila-ok?}))

(defn sonja-yhteyden-tila! [status-komponentti db kehitysmoodi?]
  (let [timeout-ms 10000
        yhteys-ok? (sonja-yhteyden-tila-ok? db kehitysmoodi? timeout-ms)]
    (kasittele-status! status-komponentti yhteys-ok? :sonja (str "Ei saatu yhteyttä Sonjaan " (ms->s timeout-ms) " sekunnin kuluessa."))
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

(defn kasittele-status!
  "Asettaa annetun komponenttiavaimen statuksen ja sen virheviestin. Tätä statusta voi sitten kysellä API:n kautta"
  [status-komponentti yhteys-ok? komponenttiavain virheviesti]
  {:pre [(instance? Status status-komponentti)
         (boolean? yhteys-ok?)
         (keyword? komponenttiavain)
         (string? virheviesti)]}
  (cond
    (not yhteys-ok?)
    (aseta-status! komponenttiavain status-komponentti 503 virheviesti)
    (and yhteys-ok?
         (not= (get-in @(:status status-komponentti) [komponenttiavain :status])
               200))
    (aseta-status! komponenttiavain status-komponentti 200 "")))