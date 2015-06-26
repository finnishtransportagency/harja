(ns harja.palvelin.api.havainnot
  "Havaintojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.havainnot :as qh]
            [clojure.java.jdbc :as jdbc])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-virhevastaus []
  )

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn parsi-aika [paivamaara]
  (konversio/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara)))

(defn tallenna-havainto [db urakka-id data]
  (let [{:keys [sijainti kuvaus kommentit kirjaaja otsikko kohde liitteet paivamaara]} data
        tie (:tie sijainti)
        koordinaatit (:koodinaatit sijainti)]

    ;; todo: päättele luoja / päivittäjä
    ;; todo: hanskaa liitteet
    ;; todo: tallenna kommentit
    ;; todo: tarkista annettu tunnus, jos olemassa, päivitä

    (jdbc/with-db-transaction [transaktio db]
                              ;; fixme: täytyä oikeat arvot
                              (qh/luo-havainto<! db
                                                 urakka-id
                                                 (parsi-aika paivamaara)
                                                 "urakoitsija"
                                                 kohde
                                                 true
                                                 nil
                                                 kuvaus
                                                 (:x koordinaatit)
                                                 (:y koordinaatit)
                                                 (:numero tie)
                                                 (:aosa tie)
                                                 (:losa tie)
                                                 (:aet tie)
                                                 (:let tie)))
    true))

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
