(ns harja.palvelin.api.havainnot
  "Havaintojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.yleinen :refer [virhe vastaus kutsu monitoroi-kasittely]]
            [harja.palvelin.api.skeemat :as skeemat]))

(defn kirjaa-havainto [db kutsu]
  (let [urakan-id (:id (:params kutsu))
        vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
    (log/debug "Kirjataan uusi havainto urakalle id:" urakan-id)
    (vastaus 200 skeemat/+onnistunut-kirjaus+ vastauksen-data)))

(defrecord Havainnot []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-reitti
      http :api-lisaa-havainto
      (POST "/api/urakat/:id/havainto" request
        (monitoroi-kasittely :api-lisaa-havainto request
                             #(kirjaa-havainto db request))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-lisaa-havainto)
    this))
