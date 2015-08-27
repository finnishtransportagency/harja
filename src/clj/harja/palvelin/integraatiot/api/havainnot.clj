(ns harja.palvelin.integraatiot.api.havainnot
  "Havaintojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.havainnot :as havainnot]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-havainnolle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki havainnot kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-havainto [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus kohde paivamaara]} data
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (if (havainnot/onko-olemassa-ulkoisella-idlla? db (:id tunniste) (:id kirjaaja))
      (:id (havainnot/paivita-havainto-ulkoisella-idlla<!
             db
             (pvm-string->java-sql-date paivamaara)
             kohde
             kuvaus
             (:x koordinaatit)
             (:y koordinaatit)
             (:numero tie)
             (:aosa tie)
             (:losa tie)
             (:aet tie)
             (:let tie)
             (:id kirjaaja)
             (:id tunniste)
             (:id kirjaaja)))
      (:id (havainnot/luo-havainto<!
             db
             urakka-id
             (pvm-string->java-sql-date paivamaara)
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
             (:id tunniste))))))

(defn tallenna-kommentit [db havainto-id kirjaaja kommentit]
  (doseq [kommentin-data kommentit]
    (let [kommentti (kommentit/luo-kommentti<! db "urakoitsija" (:kommentti kommentin-data) nil (:id kirjaaja))
          kommentti-id (:id kommentti)]
      (havainnot/liita-kommentti<! db havainto-id kommentti-id))))

(defn tallenna [liitteiden-hallinta db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [havainto-id (tallenna-havainto transaktio urakka-id kirjaaja data)
          kommentit (:kommentit data)
          liitteet (:liitteet data)]
      (tallenna-kommentit transaktio havainto-id kirjaaja kommentit)
      (tallenna-liitteet-havainnolle transaktio liitteiden-hallinta urakka-id havainto-id kirjaaja liitteet))))

(defn kirjaa-havainto [liitteiden-hallinta db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi havainto urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna liitteiden-hallinta db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Havainnot []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-havainto
      (POST "/api/urakat/:id/havainto" request
        (kasittele-kutsu db integraatioloki :lisaa-havainto request skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+
                         (fn [parametrit data kayttaja db] (kirjaa-havainto liitteiden-hallinta db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-havainto)
    this))
