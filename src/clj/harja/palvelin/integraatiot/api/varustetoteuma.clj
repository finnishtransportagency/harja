(ns harja.palvelin.integraatiot.api.varustetoteuma
  "Varustetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat :as tierekisteri-sanomat]
            [harja.kyselyt.livitunnisteet :as livitunnisteet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus [{:keys [lisatietoja uusi-id]}]
  (let [vastauksen-data
        {:ilmoitukset (str "Varustetoteuma kirjattu onnistuneesti." (when lisatietoja lisatietoja))}]
    (if uusi-id
      (assoc vastauksen-data :id uusi-id)
      vastauksen-data)))

(defn lisaa-varuste-tierekisteriin [tierekisteri db kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Lisätään varuste tierekisteriin")
  (let [livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
        varustetoteuma (assoc-in varustetoteuma [:varuste :tunniste] livitunniste)
        valitettava-data (tierekisteri-sanomat/luo-varusteen-lisayssanoma otsikko kirjaaja varustetoteuma)]
    (let [vastaus (tierekisteri/lisaa-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      (assoc (assoc vastaus :lisatietoja (str " Uuden varusteen livitunniste on: " livitunniste)) :uusi-id livitunniste))))

(defn paivita-varuste-tierekisteriin [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Päivitetään varuste tierekisteriin")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-paivityssanoma otsikko kirjaaja varustetoteuma)]
    (let [vastaus (tierekisteri/paivita-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn poista-varuste-tierekisterista [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Poistetaan varuste tierekisteristä")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-poistosanoma otsikko kirjaaja varustetoteuma)]
    (let [vastaus (tierekisteri/poista-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn paivita-muutos-tierekisteriin
  "Päivittää varustetoteuman Tierekisteriin. On mahdollista, että muutoksen välittäminen Tierekisteriin epäonnistuu.
  Tässä tapauksessa halutaan, että muutos jää kuitenkin Harjaan ja Harjan integraatiolokeihin, jotta
  nähdään, että toteumaa on yritetty kirjata."
  [tierekisteri db kirjaaja data]
  (when tierekisteri
    (case (get-in data [:varustetoteuma :varuste :toimenpide])
      "lisatty" (lisaa-varuste-tierekisteriin tierekisteri db kirjaaja data)
      "paivitetty" (paivita-varuste-tierekisteriin tierekisteri kirjaaja data)
      "poistettu" (poista-varuste-tierekisterista tierekisteri kirjaaja data))))

(defn poista-toteuman-varustetiedot [db toteuma-id]
  (log/debug "Poistetaan toteuman vanhat varustetiedot (jos löytyy) " toteuma-id)
  (toteumat/poista-toteuman-varustetiedot!
    db
    toteuma-id))

(defn tallenna-varuste [db kirjaaja {:keys [tunniste tietolaji toimenpide arvot karttapvm sijainti
                                            kuntoluokitus piiri tierekisteriurakkakoodi alkupvm loppupvm]} toteuma-id]
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
    (let [toteuma (assoc (get-in data [:varustetoteuma :toteuma]) :reitti nil)
          toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma transaktio urakka-id kirjaaja toteuma)
          varustetiedot (get-in data [:varustetoteuma :varuste])
          sijainti (get-in data [:varustetoteuma :sijainti])
          aika (pvm-string->java-sql-date (get-in data [:varustetoteuma :toteuma :alkanut]))]
      (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
      (log/debug "Aloitetaan sijainnin tallennus")
      (api-toteuma/tallenna-sijainti transaktio sijainti aika toteuma-id)
      (log/debug "Aloitetaan toteuman tehtävien tallennus")
      (api-toteuma/tallenna-tehtavat transaktio kirjaaja toteuma toteuma-id)
      (log/debug "Aloitetaan toteuman varustetietojen tallentaminen")
      (poista-toteuman-varustetiedot transaktio toteuma-id)
      (tallenna-varuste transaktio kirjaaja varustetiedot toteuma-id))))

(defn kirjaa-toteuma [tierekisteri db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi varustetoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-toteuma db urakka-id kirjaaja data)

    (let [vastaus (paivita-muutos-tierekisteriin tierekisteri db kirjaaja data)]
      (log/debug "Tietojen päivitys tierekisteriin suoritettu")
      (tee-onnistunut-vastaus vastaus))))

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
                         json-skeemat/+varustetoteuman-kirjaus+
                         json-skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja db] (kirjaa-toteuma tierekisteri db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-varustetoteuma)
    this))
