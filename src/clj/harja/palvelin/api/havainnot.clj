(ns harja.palvelin.api.havainnot
  "Havaintojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.api.skeemat :as skeemat]))



(defn kirjaa-havainto [db {urakan-id :id} data]
  (log/debug "Kirjataan uusi havainto urakalle id:" urakan-id)

  ;; fixme: validoi että vastaanotettua dataa voidaan käyttää
  ;; fixme: lapioi data kyselyyn ja tallenna
  ;; fixme: tee response

  (let [vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
    vastauksen-data))

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
