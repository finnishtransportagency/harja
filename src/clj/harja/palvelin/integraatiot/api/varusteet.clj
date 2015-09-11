(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.testi :as testi]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat]
            [harja.tyokalut.json_validointi :as json]))


(defn hae-tietolaji [tierekisteri {:keys [tunniste muutospaivamaara]} kayttaja]
  (log/debug "Haetaan tietolajin: " tunniste " kuvaus muutospäivämäärällä: " muutospaivamaara " käyttäjälle: " kayttaja)
  ;; todo: tarkista, että käyttäjä on olemassa
  (let [vastausdata (tierekisteri/hae-tietolajit tierekisteri tunniste muutospaivamaara)
        ominaisuudet (get-in vastausdata [:tietolaji :ominaisuudet])
        _ (log/debug "ennen munklausta" vastausdata)
        muunnettu-vastausdata (dissoc (assoc-in vastausdata [:tietolaji :ominaisuudet]
                                                (map (fn [o]
                                                       {:ominaisuus o})
                                                     ominaisuudet)) :onnistunut)]
    (log/debug "+++++++muunnettu-vastausdata: " muunnettu-vastausdata)
    muunnettu-vastausdata))

(defrecord Varusteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :hae-tietolaji
      (GET "/api/varusteet/tietolaji" request
        (kasittele-kutsu db integraatioloki :hae-tietolaji request nil skeemat/+tietolajien-haku+
                         (fn [parametrit data kayttaja db]
                           (log/debug "parametrit" parametrit)
                           (hae-tietolaji tierekisteri parametrit kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-tietolaji)
    this))