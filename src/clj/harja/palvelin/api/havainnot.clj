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
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.roolit :as roolit])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(defn parsi-aika [paivamaara]
  (konversio/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara)))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki toteumat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-havainto [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus kohde paivamaara]} data
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (when (havainnot/onko-olemassa-ulkoisella-idlla? db (:id tunniste))
      (havainnot/poista-havainto-ulkoisella-idlla! db (:id tunniste)))
    (let [havainto (havainnot/luo-havainto<!
                     db
                     urakka-id
                     (parsi-aika paivamaara)
                     "urakoitsija"
                     kohde
                     true
                     (:id kirjaaja)
                     kuvaus
                     (:x koordinaatit)
                     (:y koordinaatit)
                     (:numero tie)
                     (:aosa tie)
                     (:losa tie)
                     (:aet tie)
                     (:let tie)
                     (:id tunniste))
          havainnon-id (:id havainto)]
      havainnon-id)))

(defn tallenna-kommentit [db havainto-id kirjaaja kommentit]
  (doseq [kommentin-data kommentit]
    (let [kommentti (kommentit/luo-kommentti<! db "urakoitsija" (:kommentti kommentin-data) nil (:id kirjaaja))
          kommentti-id (:id kommentti)]
      (havainnot/liita-kommentti<! db havainto-id kommentti-id))))

(defn tallenna-liitteet [db liitteiden-hallinta urakan-id havainto-id kirjaaja liitteet]
  (doseq [liitteen-data liitteet]
    (when (:sisalto (:liite liitteen-data))
      (let [liite (:liite liitteen-data)
            tyyppi (:tyyppi liite)
            tiedostonimi (:nimi liite)
            data (dekoodaa-base64 (:sisalto liite))
            koko (alength data)
            liite-id (:id (liitteet/luo-liite liitteiden-hallinta (:id kirjaaja) urakan-id tiedostonimi tyyppi koko data))]
        (havainnot/liita-havainto<! db havainto-id liite-id)))))

(defn tallenna [liitteiden-hallinta db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [havainto-id (tallenna-havainto transaktio urakka-id kirjaaja data)
          kommentit (:kommentit data)
          liitteet (:liitteet data)]
      (tallenna-kommentit transaktio havainto-id kirjaaja kommentit)
      (tallenna-liitteet transaktio liitteiden-hallinta urakka-id havainto-id kirjaaja liitteet))))

(defn kirjaa-havainto [liitteiden-hallinta db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi havainto urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna liitteiden-hallinta db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Havainnot []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta :as this}]
    (julkaise-reitti
      http :api-lisaa-havainto
      (POST "/api/urakat/:id/havainto" request
        (kasittele-kutsu db :api-lisaa-havainto request skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja] (kirjaa-havainto liitteiden-hallinta db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-lisaa-havainto)
    this))
