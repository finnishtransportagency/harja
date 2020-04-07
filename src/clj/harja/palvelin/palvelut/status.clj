(ns harja.palvelin.palvelut.status
  (:require [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [harja.palvelin.palvelut.jarjestelman-tila :as jarjestelman-tila]
            [cheshire.core :refer [encode]]
            [harja.kyselyt.status :as q]
            [clojure.core.async :as async :refer [<! go-loop]]))

(defn tietokannan-tila [db]
  {:viimeisin-toteuman-luontiaika (q/tarkista-kantayhteys db)})

(defn replikoinnin-tila [db-replica]
  {:replikoinnin-viive-sekunteina (q/hae-replikoinnin-viive db-replica)})

(defn sonja-yhteyden-tila [db]
  (let [yhteys-ok? (first (async/alts!! [(go-loop [status-ok? (jarjestelman-tila/kaikki-yhteydet-ok? (jarjestelman-tila/hae-sonjan-tila db))]
                                           (if status-ok?
                                             status-ok?
                                             (do (<! (async/timeout 1000))
                                                 (recur (jarjestelman-tila/kaikki-yhteydet-ok? (jarjestelman-tila/hae-sonjan-tila db))))))
                                         (async/timeout 5000)]))]
    {:sonja-yhteys-ok? yhteys-ok?}))

(defrecord Status [status]
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           db-replica :db-replica
           :as this}]
    (http-palvelin/julkaise-reitti
     http :status
     (GET "/status" _
          (let [{:keys [status viesti]} @status]
            {:status status
             :headers {"Content-Type" "application/json; charset=UTF-8"}
             :body (encode
                    (merge {:viesti viesti}
                           (when (= 200 status)
                             (merge
                              (tietokannan-tila db)
                              (replikoinnin-tila db-replica)
                              (sonja-yhteyden-tila db)))))})))
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

(defn luo-status []
  (->Status (atom {:status 503 :viesti "Harja käynnistyy"})))

(defn aseta-status! [status koodi viesti]
  (swap! (:status status) assoc :status koodi :viesti viesti))
