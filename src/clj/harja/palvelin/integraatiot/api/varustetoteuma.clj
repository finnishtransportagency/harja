(ns harja.palvelin.integraatiot.api.varustetoteuma
  "Varustetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Varustetoteuma kirjattu onnistuneesti"}]
    vastauksen-data))

(defn lisaa-varuste-tierekisteriin [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Lisätään varuste tierekisteriin")
  (let [valitettava-data {:lisaaja {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                                    (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                                    (:kayttajanimi kirjaaja))
                                    :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                                    :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                                    :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
                          :tietue  {:tunniste    (get-in varustetoteuma [:varuste :tunniste])
                                    :alkupvm     (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))
                                    :loppupvm    (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :paattynyt]))
                                    :karttapvm   (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :karttapvm]))
                                    :piiri       (get-in varustetoteuma [:varuste :piiri])
                                    :kuntoluokka (get-in varustetoteuma [:varuste :kuntoluokitus])
                                    :urakka      (get-in varustetoteuma [:varuste :tierekisteriurakkakoodi])
                                    :sijainti    {:tie {:numero  (get-in varustetoteuma [:varuste :sijainti :tie :numero])
                                                        :aet     (get-in varustetoteuma [:varuste :sijainti :tie :aet])
                                                        :aosa    (get-in varustetoteuma [:varuste :sijainti :tie :aosa])
                                                        :let     (get-in varustetoteuma [:varuste :sijainti :tie :let])
                                                        :losa    (get-in varustetoteuma [:varuste :sijainti :tie :losa])
                                                        :ajr     (get-in varustetoteuma [:varuste :sijainti :tie :ajr])
                                                        :puoli   (get-in varustetoteuma [:varuste :sijainti :tie :puoli])
                                                        :alkupvm (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :sijainti :tie :alkupvm]))
                                                        :loppupvm (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :sijainti :tie :loppupvm]))}}
                                    :tietolaji   {:tietolajitunniste (get-in varustetoteuma [:varuste :tietolaji])
                                                  :arvot             (get-in varustetoteuma [:varuste :arvot])}}

                          :lisatty (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))}]
    (let [vastaus (tierekisteri/lisaa-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn paivita-varuste-tierekisteriin [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Päivitetään varuste tierekisteriin")
  (let [valitettava-data {:paivittaja {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                                       (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                                       (:kayttajanimi kirjaaja))
                                       :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                                       :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                                       :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
                          :tietue     {:tunniste    (get-in varustetoteuma [:varuste :tunniste])
                                       :alkupvm     (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))
                                       :loppupvm    (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :paattynyt]))
                                       :karttapvm   (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :karttapvm]))
                                       :piiri       (get-in varustetoteuma [:varuste :piiri])
                                       :kuntoluokka (get-in varustetoteuma [:varuste :kuntoluokitus])
                                       :urakka      (get-in varustetoteuma [:varuste :tierekisteriurakkakoodi])
                                       :sijainti    {:tie {:numero  (get-in varustetoteuma [:varuste :sijainti :tie :numero])
                                                           :aet     (get-in varustetoteuma [:varuste :sijainti :tie :aet])
                                                           :aosa    (get-in varustetoteuma [:varuste :sijainti :tie :aosa])
                                                           :let     (get-in varustetoteuma [:varuste :sijainti :tie :let])
                                                           :losa    (get-in varustetoteuma [:varuste :sijainti :tie :losa])
                                                           :ajr     (get-in varustetoteuma [:varuste :sijainti :tie :ajr])
                                                           :puoli   (get-in varustetoteuma [:varuste :sijainti :tie :puoli])
                                                           :alkupvm (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :sijainti :tie :alkupvm]))
                                                           :loppupvm (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:varuste :sijainti :tie :loppupvm]))}}
                                       :tietolaji   {:tietolajitunniste (get-in varustetoteuma [:varuste :tietolaji])
                                                     :arvot             (get-in varustetoteuma [:varuste :arvot])}}

                          :paivitetty (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))}]
    (let [vastaus (tierekisteri/paivita-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn poista-varuste-tierekisterista [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Poistetaan varuste tierekisteristä")
  (let [valitettava-data {:poistaja          {:henkilo      (if (and (:etunimi kirjaaja) (:sukunimi kirjaaja))
                                                              (str (:etunimi kirjaaja) " " (:sukunimi kirjaaja))
                                                              (:kayttajanimi kirjaaja))
                                              :jarjestelma  (get-in otsikko [:lahettaja :jarjestelma])
                                              :organisaatio (get-in otsikko [:lahettaja :organisaatio :nimi])
                                              :yTunnus      (get-in otsikko [:lahettaja :organisaatio :ytunnus])}
                          :tunniste          (get-in varustetoteuma [:varuste :tunniste])
                          :tietolajitunniste (get-in varustetoteuma [:varuste :tietolaji])
                          :poistettu         (xml/json-date-time->xml-xs-date (get-in varustetoteuma [:toteuma :alkanut]))}]
    (let [vastaus (tierekisteri/poista-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn paivita-muutos-tierekisteriin
  "Päivittää varustetoteuman Tierekisteriin. On mahdollista, että muutoksen välittäminen Tierekisteriin epäonnistuu.
  Tässä tapauksessa halutaan, että muutos jää kuitenkin Harjaan ja Harjan integraatiolokeihin, jotta
  nähdään, että toteumaa on yritetty kirjata."
  [tierekisteri kirjaaja data]
  (case (get-in data [:varustetoteuma :varuste :toimenpide])
    "lisatty" (lisaa-varuste-tierekisteriin tierekisteri kirjaaja data)
    "paivitetty" (paivita-varuste-tierekisteriin tierekisteri kirjaaja data)
    "poistettu" (poista-varuste-tierekisterista tierekisteri kirjaaja data)))

(defn poista-toteuman-varustetiedot [db toteuma-id]
  (log/debug "Poistetaan toteuman vanhat varustetiedot (jos löytyy) " toteuma-id)
  (toteumat/poista-toteuman-varustetiedot!
    db
    toteuma-id))

(defn tallenna-varuste [db kirjaaja {:keys [tunniste tietolaji toimenpide arvot karttapvm sijainti
                                            kuntoluokitus piiri tierekisteriurakkakoodi ajorata alkupvm loppupvm]} toteuma-id]
  (toteumat/luo-varustetoteuma<!
    db
    tunniste
    toteuma-id
    toimenpide
    tietolaji
    arvot
    (pvm-string->java-sql-date karttapvm)
    (get-in sijainti [:tie :numero])
    (get-in sijainti [:tie :aosa])
    (get-in sijainti [:tie :losa])
    (get-in sijainti [:tie :let])
    (get-in sijainti [:tie :aet])
    (get-in sijainti [:tie :puoli])
    (get-in sijainti [:tie :ajorata])
    alkupvm
    loppupvm
    piiri
    kuntoluokitus
    tierekisteriurakkakoodi
    (:id kirjaaja)))

(defn tallenna-toteuma [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
                            (let [toteuma (get-in data [:varustetoteuma :toteuma])
                                  toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma transaktio urakka-id kirjaaja toteuma)
                                  varustetiedot (get-in data [:varustetoteuma :varuste])]
                              (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
                              (log/debug "Aloitetaan sijainnin tallennus")
                              (api-toteuma/tallenna-sijainti transaktio (get-in data [:varustetoteuma :sijainti]) toteuma-id)
                              (log/debug "Aloitetaan toteuman tehtävien tallennus")
                              (api-toteuma/tallenna-tehtavat transaktio kirjaaja toteuma toteuma-id)
                              (log/debug "Aloitetaan toteuman varustetietojen tallentaminen")
                              (poista-toteuman-varustetiedot transaktio toteuma-id)
                              (tallenna-varuste transaktio kirjaaja varustetiedot toteuma-id))))

(defn kirjaa-toteuma [tierekisteri db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjaaja: " (pr-str kirjaaja))
    (log/debug "Kirjataan uusi varustetoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-toteuma db urakka-id kirjaaja data)
    (paivita-muutos-tierekisteriin tierekisteri kirjaaja data)
    (log/debug "Tietojen päivitys tierekisteriin suoritettu")
    (tee-onnistunut-vastaus)))

(defrecord Varustetoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :lisaa-varustetoteuma
      (POST "/api/urakat/:id/toteumat/varuste" request
        (kasittele-kutsu db
                         integraatioloki
                         :lisaa-varustetoteuma
                         request
                         skeemat/+varustetoteuman-kirjaus+
                         skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja db] (kirjaa-toteuma tierekisteri db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-varustetoteuma)
    this))
