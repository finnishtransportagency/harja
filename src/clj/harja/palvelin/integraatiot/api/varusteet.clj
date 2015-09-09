(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [taoensso.timbre :as log]))


(defn hae-tietolaji [tierekisteri parametrit kayttaja]
  (println "++++++PARAMETRIT" parametrit)
  (log/debug "Haetaan tietolajin: " nil " kuvaus muutospäivämäärällä: " nil " käyttäjälle: " kayttaja)
  ;; todo: tarkista, että käyttäjä on olemassa
  (let [vastausdata
        (tierekisteri/hae-tietolajit tierekisteri "tl506" nil)]))

(defrecord Varusteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :hae-tietolaji
      (GET "/api/varusteet/tietolaji" request
        (kasittele-kutsu db integraatioloki :hae-tietolaji request nil skeemat/+tietolajien-haku+
                         (fn [parametrit data kayttaja db] (hae-tietolaji tierekisteri parametrit kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-tietolaji)
    this))