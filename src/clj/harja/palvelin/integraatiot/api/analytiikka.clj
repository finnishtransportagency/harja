(ns harja.palvelin.integraatiot.api.analytiikka
  "Analytiikkaportaalille endpointit"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [clojure.data.json :as json]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-get-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn palauta-toteumat [db parametrit kayttaja]
  (json/read-str (->
                   "resources/api/examples/analytiikka-reittitoteumat-response.json"
                   slurp)))

(defrecord Analytiikka []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :analytiikka-toteumat
      (GET "/api/analytiikka/toteumat/:alkuaika/:loppuaika" request
        (kasittele-get-kutsu db integraatioloki
          :analytiikka-hae-toteumat request
          json-skeemat/analytiikkaportaali-toteuma-vastaus
          (fn [parametrit kayttaja db]
            (palauta-toteumat db parametrit kayttaja))
          true)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :analytiikka-toteumat)
    this))
