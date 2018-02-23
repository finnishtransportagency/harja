(ns harja.palvelin.integraatiot.api.paikkaukset
  "Paikkausten ja niiden kustannusten hallinta API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.sanomat.paikkaussanoma :as paikkaussanoma]
            [harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma :as paikkaustoteumasanoma]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.domain.paikkaus :as paikkaus]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tallenna-paikkaus [db urakka-id kayttaja-id {paikkaukset :paikkaukset}]
  (let [paikkaukset (map #(paikkaussanoma/api->domain urakka-id (:paikkaus %)) paikkaukset)]
    (doseq [paikkaus paikkaukset]
      (paikkaus-q/tallenna-paikkaus db kayttaja-id paikkaus))))

(defn tallenna-paikkaustoteuma [db urakka-id kayttaja-id {paikkauskustannukset :paikkauskustannukset}]
  (let [toteumat (map #(paikkaustoteumasanoma/api->domain urakka-id (:paikkauskustannus %)) paikkauskustannukset)]
    (doseq [[ulkoinen-id toteumat] (group-by ::paikkaus/ulkoinen-id (apply concat toteumat))]
      (paikkaus-q/poista-paikkaustoteumat db kayttaja-id ulkoinen-id)
      (doseq [toteuma toteumat]
        (paikkaus-q/tallenna-paikkaustoteuma db kayttaja-id toteuma)))))

(defn kirjaa-paikkaus [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan uusia paikkauksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkaus data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tallenna-paikkaus db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"}))

(defn kirjaa-paikkaustoteuma [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan uusia paikkauskustannuksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkaus data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tallenna-paikkaustoteuma db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkauskustannukset kirjattu onnistuneesti"}))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-paikkaus
      (POST "/api/urakat/:id/paikkaus" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-paikkaus
                         request
                         json-skeemat/paikkausten-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-paikkaus db parametrit data kayttaja))))
      true)
    (julkaise-reitti
      http :kirjaa-paikkaustoteuma
      (POST "/api/urakat/:id/paikkaus/kustannus" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-paikkaustoteuma
                         request
                         json-skeemat/paikkauskustannusten-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-paikkaustoteuma db parametrit data kayttaja))))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-paikkaus)
    this))
