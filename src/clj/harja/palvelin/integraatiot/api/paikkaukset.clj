(ns harja.palvelin.integraatiot.api.paikkaukset
  "Tielupien hallinta API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]
            [harja.domain.paikkaus :as paikkaus]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.tieverkko :as tieverkko-q]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-sijainti [db sijainti]
  (let [parametrit {:tie (::paikkaus/tie sijainti)
                    :aosa (::paikkaus/aosa sijainti)
                    :aet (::paikkaus/aet sijainti)
                    :losa (::paikkaus/losa sijainti)
                    :loppuet (::paikkaus/let sijainti)}
        geometria (if (and (:losa parametrit) (:loppuet parametrit))
                    (tieverkko-q/tierekisteriosoite-viivaksi db parametrit)
                    (tieverkko-q/tierekisteriosoite-pisteeksi db parametrit))]
    (assoc sijainti ::paikkaus/geometria geometria)))


(defn kirjaa-paikkaus [liitteiden-hallinta db data kayttaja]
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"}))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-paikkaus
      (POST "/api/urakat/:id/paikkaus" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-paikkaus
                         request
                         json-skeemat/paikkaustoteuman-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [_ data kayttaja db]
                           (kirjaa-paikkaus liitteiden-hallinta db data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-paikkaus)
    this))
