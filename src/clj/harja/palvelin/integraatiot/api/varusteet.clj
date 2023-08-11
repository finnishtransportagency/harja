(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut."
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-reitti julkaise-palvelu poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat :as tierekisteri-sanomat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as validointi]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietolaji-tunnisteella
  "Hakee tietolajin Tierekisteristä tunnisteen perusteella."
  [tierekisteri tunniste]
  (let [vastausdata (tierekisteri/hae-tietolaji tierekisteri tunniste nil)
        ominaisuudet (get-in vastausdata [:tietolaji :ominaisuudet])]
    (tierekisteri-sanomat/muunna-tietolajin-hakuvastaus vastausdata ominaisuudet)))

(defn hae-kaikki-tietolajit [tierekisteri]
  "Hakee kaikkien tietolajien kuvaukset Tierekisteristä tunnisteen perusteella."
  (let [vastausdata (tierekisteri/hae-kaikki-tietolajit tierekisteri nil)]
    (tierekisteri-sanomat/muunna-tietolajien-hakuvastaus vastausdata)))

(defn hae-tietolajit [tierekisteri parametrit kayttaja]
  (oikeudet/ei-oikeustarkistusta!)
  (let [tunniste (get parametrit "tunniste")]
    (if (not (str/blank? tunniste))
      (do
        (log/debug "Haetaan tietolajin: " tunniste " kuvaus käyttäjälle: " kayttaja)
        (hae-tietolaji-tunnisteella tierekisteri tunniste))
      (do
        (log/debug "Haetaan kaikkien tietolajien kuvaukset käyttäjälle: " kayttaja)
        (hae-kaikki-tietolajit tierekisteri)))))

(defrecord Varusteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :hae-tietolaji
      (GET "/api/varusteet/tietolaji" request
        (kasittele-kutsu-async db integraatioloki :hae-tietolaji request nil json-skeemat/tietolajien-haku
                               (fn [parametrit _ kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (hae-tietolajit tierekisteri parametrit kayttaja)))))

    (julkaise-palvelu http :hae-tietolajin-kuvaus
                      (fn [user tietolaji]
                        (let [tietolajit (hae-tietolajit tierekisteri {"tunniste" tietolaji} user)]
                          (:tietolaji (first (:tietolajit tietolajit))))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tietolaji
                     :hae-tietolajin-kuvaus)
    this))
