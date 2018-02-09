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
            [harja.kyselyt.api-tyojono :as tyojono-q]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol PaikkausAPI
  (kirjaa-paikkaustoteuma [this tyojono-id]))

(defn tallenna-paikkaustoteuma [db toteumat]
  (def uusimmat-paikkaustoteumat (:paikkaustoteumat toteumat))
  (let [urakka-id (:urakka-id toteumat)
        kayttaja-id (:luoja-id toteumat)
        paikkaustoteumat (map #(paikkaustoteumasanoma/api->domain urakka-id (:paikkaustoteuma %)) (:paikkaustoteumat toteumat))]
    (doseq [paikkaustoteuma paikkaustoteumat]
      (paikkaus-q/tallenna-paikkaustoteuma db kayttaja-id paikkaustoteuma))))

(defn kirjaa-paikkaustoteumat [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan uusia paikkaustoteumia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkaustoteumat data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        luoja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tyojono-q/lisaa-tyojonoon<! db {:tapahtuman_nimi "uusi-paikkaustoteuma"
                                     :sisalto (cheshire/encode (assoc data :urakka-id urakka-id :luoja-id luoja-id))}))
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
    this)

  PaikkausAPI
  (kirjaa-paikkaustoteuma [{db :db} tyojono-id]
    (tallenna-paikkaustoteuma db (walk/keywordize-keys (cheshire/decode (tyojono-q/hae-tapahtuman-sisalto db tyojono-id))))))
