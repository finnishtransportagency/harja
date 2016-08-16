(ns harja.palvelin.palvelut.status
  (:require [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [cheshire.core :refer [encode]]))

(defrecord Status [status]
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    (http-palvelin/julkaise-reitti
     http :status
     (GET "/status" _
          (let [{:keys [status] :as body} @status]
            {:status status
             :headers {"Content-Type" "application/json"}
             :body (encode (dissoc body :status))})))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :status)
    this))

(defn luo-status []
  (->Status (atom {:status 503 :viesti "Harja käynnistyy"})))

(defn aseta-status! [status koodi viesti]
  (swap! (:status status) assoc :status koodi :vietsi viesti))
