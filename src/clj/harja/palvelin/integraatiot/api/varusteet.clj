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
  (let [tunniste (get parametrit "tunniste")
        muutospaivamaara (get parametrit "muutospaivamaara")]
    (log/debug "Haetaan tietolajin: " tunniste " kuvaus muutospäivämäärällä: " muutospaivamaara " käyttäjälle: " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietolajit tierekisteri tunniste muutospaivamaara)
          ominaisuudet (get-in vastausdata [:tietolaji :ominaisuudet])
          muunnettu-vastausdata (dissoc (assoc-in vastausdata [:tietolaji :ominaisuudet]
                                                  (map (fn [o]
                                                         {:ominaisuus o})
                                                       ominaisuudet)) :onnistunut)]
      muunnettu-vastausdata)))

(defn hae-tietue [tierekisteri parametrit kayttaja]
  (let [tunniste (get parametrit "tunniste")
        tietolajitunniste (get parametrit "tietolajitunniste")]
    (log/debug "Haetaan tietue tunnisteella " tunniste " tietolajista " tietolajitunniste " kayttajalle " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietue tierekisteri tunniste tietolajitunniste)
          muunnettu-vastausdata (-> vastausdata
                                    (dissoc :onnistunut)
                                    (update-in [:tietue] dissoc :kuntoluokka :urakka :piiri)
                                    (update-in [:tietue :sijainti] dissoc :koordinaatit :linkki)
                                    (update-in [:tietue :sijainti :tie] dissoc :puoli :alkupvm :ajr)
                                    (clojure.set/rename-keys {:tietue :varuste}))]

      ;; Jos tietuetunnisteella ei löydy tietuetta, palauttaa tierekisteripalvelu XML:n jossa tietue on nil
      ;; Tässä tapauksessa me palautamme tyhjän kartan.
      (if (:tietue vastausdata)
        muunnettu-vastausdata
        {}))))

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

    (julkaise-reitti
      http :hae-tietue
      (GET "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :hae-tietue request nil skeemat/+varusteen-haku-vastaus+
                         (fn [parametrit data kayttaja db]
                           (log/debug "parametrit" parametrit)
                           (hae-tietue tierekisteri parametrit kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tietolaji
                     :hae-tietue)
    this))