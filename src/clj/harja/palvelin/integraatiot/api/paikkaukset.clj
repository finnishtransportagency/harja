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
            [harja.palvelin.integraatiot.yha.yha-paikkauskomponentti :as yha-paikkauskomponentti]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.domain.paikkaus :as paikkaus]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- poista-paikkaustoteumat
  "Merkitsee poistetuksi paikkaustoteumat eli paikkauskustannukset."
  [db urakka-id kayttaja-id paikkaustoteumat]
    (paikkaus-q/paivita-paikkaustoteumat-poistetuksi db kayttaja-id urakka-id paikkaustoteumat))

(defn- poista-paikkaukset
  "Merkitsee poistetuksi paikkaukset."
  [db urakka-id kayttaja-id paikkaukset]
    (paikkaus-q/paivita-paikkaukset-poistetuksi db kayttaja-id urakka-id paikkaukset))

(defn- poista-paikkauskohteet
  "Merkitsee poistetuksi paikkauskohteet sekä niistä riippuvaiset paikkaukset ja paikkaustoteumat eli paikkauskustannukset."
  [db urakka-id kayttaja-id paikkauskohteet]
  (paikkaus-q/paivita-paikkauskohteet-poistetuksi db kayttaja-id urakka-id paikkauskohteet))

(defn poista-paikkaustiedot [db yhap {id :id} data kayttaja]
  (log/debug (format "Poistetaan paikkaustietoja urakasta: %s käyttäjän: %s toimesta"
                     id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)
        kohde-idt (:poistettavat-paikkauskohteet data)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction [tx db]
      (poista-paikkauskohteet tx urakka-id kayttaja-id kohde-idt)
      (poista-paikkaukset tx urakka-id kayttaja-id (:poistettavat-paikkaukset data))
      (poista-paikkaustoteumat tx urakka-id kayttaja-id (:poistettavat-paikkauskustannukset data)))
    (doseq [kohde-id kohde-idt]
      (try+
        (when-let [harja-id (paikkaus-q/hae-paikkauskohteen-harja-id db {:ulkoinen-id kohde-id})]
          (yha-paikkauskomponentti/poista-paikkauskohde yhap urakka-id harja-id))
        (catch Exception e
          (log/error "Poista paikkauskohde YHA:sta epäonnistui, tiedot: " (pr-str e))))))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkauskohteet ja -kustannukset poistettu onnistuneesti"}))


(defn tallenna-paikkaus [db urakka-id kayttaja-id {paikkaukset :paikkaukset}]
  (let [paikkaukset (map #(paikkaussanoma/api->domain urakka-id (:paikkaus %)) paikkaukset)]
    (doseq [paikkaus paikkaukset]
      (paikkaus-q/tallenna-paikkaus db urakka-id kayttaja-id paikkaus))))

(defn tallenna-paikkaustoteuma
  "Tallentaa paikkauskustannuksiin liittyvät tiedot. Poistaa sitä ennen kannasta."
  [db urakka-id kayttaja-id {paikkauskustannukset :paikkauskustannukset}]
  (let [toteumat (map #(paikkaustoteumasanoma/api->domain urakka-id (:paikkauskustannus %)) paikkauskustannukset)]
    (doseq [[ulkoinen-id toteumat] (group-by ::paikkaus/ulkoinen-id (apply concat toteumat))]
      (paikkaus-q/poista-paikkaustoteuma db kayttaja-id urakka-id ulkoinen-id)
      (doseq [toteuma toteumat]
        (paikkaus-q/tallenna-paikkaustoteuma db urakka-id kayttaja-id toteuma)))))

(defn kirjaa-paikkaus [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan paikkauksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkaukset data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tallenna-paikkaus db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"}))

(defn kirjaa-paikkaustoteuma [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan paikkauskustannuksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkauskustannukset data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tallenna-paikkaustoteuma db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkauskustannukset kirjattu onnistuneesti"}))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db
           integraatioloki :integraatioloki
           yha-paikkaus :yha-paikkauskomponentti
           :as this}]
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
    (julkaise-reitti
      http :poista-paikkaustiedot
      (DELETE "/api/urakat/:id/paikkaus" request
        (kasittele-kutsu db
                         integraatioloki
                         :poista-paikkaustiedot
                         request
                         json-skeemat/paikkausten-poisto-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (poista-paikkaustiedot db yha-paikkaus parametrit data kayttaja))))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-paikkaus
                     :kirjaa-paikkaustoteuma
                     :poista-paikkaustiedot)
    this))
