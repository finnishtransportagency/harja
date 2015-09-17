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

;; Muokkaa tietuetta siten, että se vastaa json skemaa
;; Esimerkiksi koordinaatteja ja linkkejä ei ole toistaiseksi tarkoituskaan laittaa eteenpäin,
;; vaan ne ovat 'future prooffausta'. Näiden poistaminen payloadista on kasattu tänne, jotta JOS joskus halutaankin
;; palauttaa esim koordinaatit, ei tarvi kuin poistaa niiden dissoccaaminen täältä.
;; Tietueille ja tietueelle tehdään myös muita samankaltaisia operaatiota, esim :tietue -> :varuste uudelleennimeäminen,
;; mutta näitä operaatioita ei tehdä täällä em. syystä.
(def puhdista-tietue-xf
  #(-> %
       (update-in [:tietue] dissoc :kuntoluokka :urakka :piiri)
       (update-in [:tietue :sijainti] dissoc :koordinaatit :linkki)
       (update-in [:tietue :sijainti :tie] dissoc :puoli :alkupvm :ajr)))

(defn hae-tietue [tierekisteri parametrit kayttaja]
  (let [tunniste (get parametrit "tunniste")
        tietolajitunniste (get parametrit "tietolajitunniste")]
    (log/debug "Haetaan tietue tunnisteella " tunniste " tietolajista " tietolajitunniste " kayttajalle " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietue tierekisteri tunniste tietolajitunniste)
          muunnettu-vastausdata (-> vastausdata
                                    (dissoc :onnistunut)
                                    (puhdista-tietue-xf)
                                    (clojure.set/rename-keys {:tietue :varuste}))]

      ;; Jos tietuetunnisteella ei löydy tietuetta, palauttaa tierekisteripalvelu XML:n jossa tietue on nil
      ;; Tässä tapauksessa me palautamme tyhjän kartan.
      (if (:tietue vastausdata)
        muunnettu-vastausdata
        {}))))

(defn hae-tietueet [tierekisteri parametrit kayttaja]
  (let [tr (into {} (filter val {:numero  (get parametrit "numero")
                                 :aet     (get parametrit "aet")
                                 :aosa    (get parametrit "aosa")
                                 :let     (get parametrit "let")
                                 :losa    (get parametrit "losa")
                                 :ajr     (get parametrit "ajr")
                                 :puoli   (get parametrit "puoli")
                                 :alkupvm (get parametrit "alkupvm")}))
        tietolajitunniste (get parametrit "tietolajitunniste")
        muutospvm (get parametrit "muutospaivamaara")]
    (log/debug "Haetaan tietueet tietolajista " tietolajitunniste " muutospäivämäärällä " muutospvm
               ", käyttäjälle " kayttaja " tr osoitteesta: " (pr-str tr))
    (let [vastausdata (tierekisteri/hae-tietueet tierekisteri tr tietolajitunniste muutospvm)
          muunnettu-vastausdata (-> vastausdata
                                    (dissoc :onnistunut)
                                    (update-in [:tietueet] #(map puhdista-tietue-xf %))
                                    (update-in [:tietueet] #(into [] (remove nil? (remove empty? %))))
                                    (update-in [:tietueet] (fn [tietue]
                                                             (map #(clojure.set/rename-keys % {:tietue :varuste}) tietue)))
                                    (clojure.set/rename-keys {:tietueet :varusteet}))]

      ;; Jos tietueita ei löydy, on muunnetussa vastausdatassa tyhjä vektori avaimella tietueet
      ;; Tässä tapauksessa palautamme tyhjän kartan
      (if (> (count (:varusteet muunnettu-vastausdata)) 1)
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

    (julkaise-reitti
      http :hae-tietueet
      (GET "/api/varusteet/varusteet" request
        (kasittele-kutsu db integraatioloki :hae-tietueet request nil skeemat/+varusteiden-haku-vastaus+
                         (fn [parametrit data kayttaja db]
                           (log/debug "parametrit" parametrit)
                           (hae-tietueet tierekisteri parametrit kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tietolaji
                     :hae-tietue
                     :hae-tietueet)
    this))