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
            [harja.kyselyt.havainnot :as havainnot]
            [harja.kyselyt.kommentit :as kommentit]
            [clojure.java.jdbc :as jdbc])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(defn parsi-aika [paivamaara]
  (konversio/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara)))

(defn tee-virhevastaus []
  ;; todo: toteuta
  )

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-havainto [db urakka-id data]

  ;; todo: tarkista annettu tunnus, jos olemassa, päivitä
  ;; todo: selvitä tarviiko kirjaajaa tallentaa
  ;; fixme: sijaintipisteen tallennus ei toimi jostain syystä
  ;; todo: päättele luoja / päivittäjä

  (let [{:keys [sijainti kuvaus kirjaaja otsikko kohde liitteet paivamaara]} data
        tie (:tie sijainti)
        koordinaatit (:koodinaatit sijainti)
        havainto (havainnot/luo-havainto<! db
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
                                           (:let tie))]
    (:id havainto)))

(defn tallenna-kommentit [db havainto-id kommentit]
  (doseq [kommentti kommentit]
    ;; fixme: tallenna loput arvot
    (let [kommentti (kommentit/luo-kommentti<! db nil (:kommentti kommentti) nil nil)
          kommentti-id (:id kommentti)]
      (havainnot/liita-kommentti<! db havainto-id kommentti-id))))

(defn tallenna [db urakka-id data]
  (jdbc/with-db-transaction [transaktio db]
                            (let [havainto-id (tallenna-havainto transaktio urakka-id data)]
                              (tallenna-kommentit transaktio havainto-id (:kommentit data))
                              ;; todo: hanskaa liitteet
                              ))
  true)

(defn kirjaa-havainto [db {id :id} data]
  (let [urakka-id (read-string id)]
    (log/debug "Kirjataan uusi havainto urakalle id:" urakka-id)
    (validointi/tarkista-urakka db urakka-id)
    (let [kirjaus-onnistunut? (tallenna db urakka-id data)]
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
