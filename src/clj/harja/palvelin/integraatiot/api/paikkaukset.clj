(ns harja.palvelin.integraatiot.api.paikkaukset
  "Tielupien hallinta API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kirjaa-paikkaus [liitteiden-hallinta db parametrit data kayttaja]
  (println "--->>> " data)
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"}))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-paikkaustoteuma
      (POST "/api/urakat/:id/paikkaus/toteuma" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-paikkaustoteuma
                         request
                         json-skeemat/paikkaustoteuman-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-paikkaus liitteiden-hallinta db parametrit data kayttaja))))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-paikkaus)
    this))
