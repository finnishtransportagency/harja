(ns harja.palvelin.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.api.tyokalut.validointi :as validointi]

            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.tarkastukset :as tarkastukset]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [try+ throw+]]))


(defn kirjaa-tiestotarkastus [db kayttaja {id :id} tarkastus]
  (let [urakka-id (Long/parseLong id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (log/info "TARKASTUS TULOSSA: " tarkastus)))

(defrecord Tarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-reitti
     http :api-lisaa-tiestotarkastus
     (POST "/api/urakat/:id/tarkastus/tiestotarkastus" request
           (kasittele-kutsu db :api-lisaa-tiestotarkastus request
                            skeemat/+tiestotarkastuksen-kirjaus+ skeemat/+kirjausvastaus+ ;; FIXME: oikea vastausskeema
                            (fn [parametrit data kayttaja]
                              (kirjaa-tiestotarkastus db kayttaja parametrit data)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut :api-lisaa-tiestotarkastus)))
