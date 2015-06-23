(ns harja.palvelin.api.havainnot
  "Havaintojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.havainnot :as qh]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-virhevastaus []
  )

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-havainto [db urakka-id data]
  ;; fixme: nämä pois
  (log/debug "Data on:" data)

  (jdbc/with-db-transaction [transaktio db]
                            ;; fixme: täytyä oikeat arvot
                            (qh/luo-havainto<! db urakka-id nil nil nil true nil nil nil nil nil nil nil nil nil ))
  true)

(defn kirjaa-havainto [db {id :id} data]
  (let [urakka-id (read-string id)]
    (log/debug "Kirjataan uusi havainto urakalle id:" urakka-id)
    (validointi/tarkista-urakka db urakka-id)
    (let [kirjaus-onnistunut? (tallenna-havainto db urakka-id data)]
      (if kirjaus-onnistunut?
        (tee-onnistunut-vastaus)
        (tee-virhevastaus)))))

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
