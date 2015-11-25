(ns harja.palvelin.integraatiot.api.ilmoitukset
  "Ilmoitusten haku ja ilmoitustoimenpiteiden kirjaus"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-havainnolle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))


(defn kaynnista-ilmoitusten-haku [db parametrit data kayttaja]
  )

(defrecord Havainnot []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :hae-ilmoitukset
      (POST "/api/urakat/:id/ilmoitukset" request
        (kasittele-kutsu db integraatioloki :hae-ilmoitukset request xml-skeemat/+havainnon-kirjaus+ xml-skeemat/+kirjausvastaus+
                         (fn [parametrit data kayttaja db] (kaynnista-ilmoitusten-haku db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-ilmoitukset)
    this))
