(ns harja.palvelin.integraatiot.api.tieluvat
  "Tielupien hallinta API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]

            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]))

(defn kirjaa-tielupa [liitteiden-hallinta db parametrit data kayttaja]
  (validointi/tarkista-onko-liikenneviraston-jarjestelma db kayttaja)
  (println "-->>> parametrit " parametrit)
  (println "-->>> data " data))

(defrecord Tieluvat []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-tielupa
      (POST "/api/tieluvat" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-tielupa
                         request
                         json-skeemat/tieluvan-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-tielupa liitteiden-hallinta db parametrit data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-tielupa)
    this))
