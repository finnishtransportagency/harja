(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE PUT]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [try+ throw+]]))



(defn hae-tietolaji [tierekisteri parametrit kayttaja]
  (let [tunniste (get parametrit "tunniste")
        muutospaivamaara (get parametrit "muutospaivamaara")]
    (log/debug "Haetaan tietolajin: " tunniste " kuvaus muutospäivämäärällä: " muutospaivamaara " käyttäjälle: " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietolajit tierekisteri tunniste muutospaivamaara)
          ominaisuudet (get-in vastausdata [:tietolaji :arvot])
          muunnettu-vastausdata (dissoc (assoc-in vastausdata [:tietolaji :arvot]
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
      ;; Tässä tapauksessa me palautamme tyhjän kartan. Samalla tunnisteella voi myös virheellisesti löytyä
      ;; useampi tietue, jolloin palautamme virheen.
      (cond
        (:tietueet vastausdata)
        (throw+ {:type    :tierekisteri-kutsu-epaonnistui
                 :virheet [{:viesti (str "Varusteen haku epäonnistui, koska tunniste " tunniste " palautti virheellisesti "
                                         (count (:tietueet vastausdata)) " tietuetta.")
                            :koodi  :tunniste-palautti-monta-tietuetta}]})
        (:tietue vastausdata) muunnettu-vastausdata
        :else {}))))

(defn lisaa-tietue [tierekisteri data kayttaja]
  (log/debug "Lisätään tietue käyttäjän " kayttaja " pyynnöstä.")
  (let [lisattava-tietue (-> data
                             (assoc-in [:lisaaja :henkilo] (str (get-in data [:lisaaja :henkilo :etunimi])
                                                                " "
                                                                (get-in data [:lisaaja :henkilo :sukunimi])))
                             (assoc-in [:lisaaja :jarjestelma] (get-in data [:otsikko :lahettaja :jarjestelma]))
                             (assoc-in [:lisaaja :yTunnus] (get-in data [:otsikko :lahettaja :organisaatio :ytunnus]))
                             (assoc-in [:tietue :alkupvm] (xml/json-date-time->xml-xs-date (get-in data [:tietue :alkupvm])))
                             (assoc-in [:tietue :loppupvm] (xml/json-date-time->xml-xs-date (get-in data [:tietue :loppupvm])))
                             (assoc-in [:tietue :karttapvm] (xml/json-date-time->xml-xs-date (get-in data [:tietue :karttapvm])))
                             (assoc-in [:tietue :sijainti :tie :alkupvm] (xml/json-date-time->xml-xs-date (get-in data [:tietue :sijainti :tie :alkupvm])))
                             (assoc :lisatty (xml/json-date-time->xml-xs-date (:lisatty data)))
                             (dissoc :otsikko))]
    (tierekisteri/lisaa-tietue tierekisteri lisattava-tietue)))

(defn paivita-tietue [tierekisteri data kayttaja]
  (log/debug "Päivitetään tietue käyttäjän " kayttaja " pyynnöstä.")
  (let [paivitettava-tietue (-> data
                                (assoc-in [:paivittaja :henkilo] (str (get-in data [:paivittaja :henkilo :etunimi])
                                                                      " "
                                                                      (get-in data [:paivittaja :henkilo :sukunimi])))
                                (assoc-in [:paivittaja :jarjestelma] (get-in data [:otsikko :lahettaja :jarjestelma]))
                                (assoc-in [:paivittaja :yTunnus] (get-in data [:otsikko :lahettaja :organisaatio :ytunnus]))
                                (assoc-in [:tietue :alkupvm] (xml/json-date-time->xml-xs-date (get-in data [:tietue :alkupvm])))
                                (assoc-in [:tietue :loppupvm] (xml/json-date-time->xml-xs-date(get-in data [:tietue :loppupvm])))
                                (assoc-in [:tietue :karttapvm] (xml/json-date-time->xml-xs-date(get-in data [:tietue :karttapvm])))
                                (assoc-in [:tietue :sijainti :tie :alkupvm] (xml/json-date-time->xml-xs-date(get-in data [:tietue :sijainti :tie :alkupvm])))
                                (assoc :paivitetty (xml/json-date-time->xml-xs-date (:paivitetty data)))
                                (dissoc :otsikko))]
    (tierekisteri/paivita-tietue tierekisteri paivitettava-tietue)))

(defn poista-tietue [tierekisteri data kayttaja]
  (log/debug "Poistetaan tietue käyttäjän " kayttaja " pyynnöstä.")
  (let [poistettava-tietue {:poistaja          {:henkilo      (str (get-in data [:poistaja :henkilo :etunimi])
                                                                   " "
                                                                   (get-in data [:poistaja :henkilo :sukunimi]))
                                                :jarjestelma  (get-in data [:otsikko :lahettaja :jarjestelma])
                                                :organisaatio (get-in data [:otsikko :lahettaja :organisaatio :nimi])
                                                :yTunnus      (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])}
                            :tunniste          (:tunniste data)
                            :tietolajitunniste (:tietolajitunniste data)
                            :poistettu         (xml/json-date-time->xml-xs-date (:poistettu data))}]
    (tierekisteri/poista-tietue tierekisteri poistettava-tietue)))

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
                         (fn [parametrit _ kayttaja _]
                           (hae-tietolaji tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietue
      (GET "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :hae-tietue request nil skeemat/+varusteen-haku-vastaus+
                         (fn [parametrit _ kayttaja _]
                           (hae-tietue tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :lisaa-tietue
      (POST "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :lisaa-tietue request skeemat/+varusteen-lisays+ nil
                         (fn [_ data kayttaja _]
                           (lisaa-tietue tierekisteri data kayttaja)))))

    (julkaise-reitti
      http :paivita-tietue
      (PUT "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :paivita-tietue request skeemat/+varusteen-paivitys+ nil
                         (fn [parametrit data kayttaja db]
                           (paivita-tietue tierekisteri data kayttaja)))))

    (julkaise-reitti
      http :poista-tietue
      (DELETE "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :poista-tietue request skeemat/+varusteen-poisto+ nil
                         (fn [_ data kayttaja _]
                           (poista-tietue tierekisteri data kayttaja)))))

    (julkaise-reitti
      http :hae-tietueet
      (GET "/api/varusteet/varusteet" request
        (kasittele-kutsu db integraatioloki :hae-tietueet request nil skeemat/+varusteiden-haku-vastaus+
                         (fn [_ data kayttaja _]
                           (hae-tietueet tierekisteri data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tietolaji
                     :hae-tietue
                     :lisaa-tietue
                     :hae-tietueet
                     :poista-tietue)
    this))