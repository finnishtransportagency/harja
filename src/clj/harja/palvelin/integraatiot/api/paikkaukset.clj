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
            [harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma :as paikkaustoteumasanoma]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tallenna-paikkaustoteuma [db urakka-id kayttaja-id toteumat]
  (let [paikkaustoteumat (map #(paikkaustoteumasanoma/api->domain urakka-id (:paikkaustoteuma %))
                              (:paikkaustoteumat toteumat))]
    (doseq [paikkaustoteuma paikkaustoteumat]
      (paikkaus-q/tallenna-paikkaus db kayttaja-id paikkaustoteuma))))

(defn kirjaa-paikkaustoteumat [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan uusia paikkaustoteumia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkaustoteumat data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tallenna-paikkaustoteuma db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"}))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
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
                           (kirjaa-paikkaustoteumat db parametrit data kayttaja))))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-paikkaus)
    this))
