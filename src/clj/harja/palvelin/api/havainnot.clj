(ns harja.palvelin.api.havainnot
  "Havaintojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.api.skeemat :as skeemat]
            [harja.kyselyt.urakat :as qu]
            [harja.kyselyt.havainnot :as qh]
            [clojure.java.jdbc :as jdbc])
  (:import
    (javax.ws.rs BadRequestException)))

(defn tee-virhevastaus []
  )

(defn tee-onnistunut-vastaus []
  )

(defn tallenna-havainto [db urakka-id data]
  (log/debug "Data on:" data)
  (log/debug "Päivämäärä on " (:paivamaara data))

  (jdbc/with-db-transaction [transaktio db]
                            (qh/luo-havainto<! db urakka-id nil nil nil true nil))
  true)

(defn kirjaa-havainto [db {id :id} data]
  (let [urakka-id (read-string id)]
    (log/debug "Kirjataan uusi havainto urakalle id:" urakka-id)
    (if (qu/onko-olemassa? db urakka-id)
      (let [kirjaus-onnistunut? (tallenna-havainto db urakka-id data)]
        (if kirjaus-onnistunut?
          (tee-onnistunut-vastaus)
          (tee-virhevastaus)))
      (do
        (log/warn "Urakkaa id:llä " urakka-id " ei löydy.")
        (throw (BadRequestException. (str "Tuntematon urakka. Urakkaa id:llä " urakka-id " ei löydy.")))))

    (let [vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
      vastauksen-data)))

(defrecord Havainnot []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-reitti
      http :api-lisaa-havainto
      (POST "/api/urakat/:id/havainto" request
        (kasittele-kutsu :api-lisaa-havainto request skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+
                         (fn [parametit data] (kirjaa-havainto db parametit data)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-lisaa-havainto)
    this))
