(ns harja.palvelin.integraatiot.api.varustetoteuma
  "Varustetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat :as tierekisteri-sanomat]
            [harja.kyselyt.livitunnisteet :as livitunnisteet]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as toteuman-validointi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- tee-onnistunut-vastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Varustetoteuma kirjattu onnistuneesti."}))

;; FIXME Nyt kun varustetoteumia voi kirjata kerralla monta,
;; pitäisi kai kerätä kaikkien tierekisterin pyyntöjen vastaukset yhteen jotta voidaan näyttää
;; vastauksessa?
#_(defn- tee-onnistunut-vastaus [{:keys [lisatietoja uusi-id]}]
    (tee-kirjausvastauksen-body {:ilmoitukset (str "Varustetoteuma kirjattu onnistuneesti." (when lisatietoja lisatietoja))
                                 :id (when uusi-id uusi-id)}))

(defn- lisaa-varuste-tierekisteriin [tierekisteri db kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Lisätään varuste tierekisteriin")
  (let [livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
        varustetoteuma (assoc-in varustetoteuma [:varuste :tunniste] livitunniste)
        valitettava-data (tierekisteri-sanomat/luo-varusteen-lisayssanoma otsikko kirjaaja varustetoteuma)]
    (let [vastaus (tierekisteri/lisaa-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      (assoc (assoc vastaus :lisatietoja (str " Uuden varusteen livitunniste on: " livitunniste)) :uusi-id livitunniste))))

(defn- paivita-varuste-tierekisteriin [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Päivitetään varuste tierekisteriin")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-paivityssanoma otsikko kirjaaja varustetoteuma)]
    (let [vastaus (tierekisteri/paivita-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn- poista-varuste-tierekisterista [tierekisteri kirjaaja {:keys [otsikko varustetoteuma]}]
  (log/debug "Poistetaan varuste tierekisteristä")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-poistosanoma otsikko kirjaaja varustetoteuma)]
    (let [vastaus (tierekisteri/poista-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn- paivita-muutos-tierekisteriin
  "Päivittää varustetoteuman Tierekisteriin. On mahdollista, että muutoksen välittäminen Tierekisteriin epäonnistuu.
  Tässä tapauksessa halutaan, että muutos jää kuitenkin Harjaan ja Harjan integraatiolokeihin, jotta
  nähdään, että toteumaa on yritetty kirjata."
  [tierekisteri db kirjaaja data]
  (when tierekisteri
    (case (get-in data [:varustetoteuma :varuste :toimenpide])
      "lisatty" (lisaa-varuste-tierekisteriin tierekisteri db kirjaaja data)
      "paivitetty" (paivita-varuste-tierekisteriin tierekisteri kirjaaja data)
      "poistettu" (poista-varuste-tierekisterista tierekisteri kirjaaja data)
      "tarkastus" (paivita-varuste-tierekisteriin tierekisteri kirjaaja data))))

(defn- poista-toteuman-varustetiedot [db toteuma-id]
  (log/debug "Poistetaan toteuman vanhat varustetiedot (jos löytyy) " toteuma-id)
  (toteumat/poista-toteuman-varustetiedot!
    db
    toteuma-id))

(defn- tallenna-toimenpide [db kirjaaja {:keys [tunniste tietolaji toimenpide arvot karttapvm sijainti
                                             kuntoluokitus piiri tierekisteriurakkakoodi alkupvm loppupvm]} toteuma-id]
  (jdbc/with-db-transaction
    [db db]
    (let [tr (:tie sijainti)
          ;; jeesql 20 parametrin rajoituksen vuoksi luonti kahdessa erässä
          id (:id (toteumat/luo-varustetoteuma<!
                    db
                    tunniste
                    toteuma-id
                    toimenpide
                    tietolaji
                    arvot
                    (aika-string->java-sql-date karttapvm)
                    alkupvm
                    loppupvm
                    piiri
                    kuntoluokitus
                    tierekisteriurakkakoodi
                    (:id kirjaaja)))]
      (toteumat/paivita-varustetoteuman-tr-osoite! db
                                                   (:numero tr)
                                                   (:aosa tr) (:aet tr)
                                                   (:losa tr) (:let tr)
                                                   (:puoli tr)
                                                   (:ajr tr)
                                                   id))))

(defn- tallenna-varusteen-lisays []
  (log/debug "Tallennetaan varustetoteuman toimenpide: lisätty varaste")
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varusteen-paivitys []
  (log/debug "Tallennetaan varustetoteuman toimenpide: päivitetty varaste")
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varusteen-poisto []
  (log/debug "Tallennetaan varustetoteuman toimenpide: poistettu varaste")
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varusteen-tarkastus []
  (log/debug "Tallennetaan varustetoteuman toimenpide: tarkastettu varaste")
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varustetoteuman-toimenpiteet [db kirjaaja toimenpiteet toteuma-id]
  (doseq [toimenpide toimenpiteet]
    (when (:varusteen-lisays toimenpide)
      (tallenna-varusteen-lisays))
    (when (:varusteen-poisto toimenpide)
      (tallenna-varusteen-poisto))
    (when (:varusteen-paivitys toimenpide)
      (tallenna-varusteen-paivitys))
    (when (:varusteen-tarkastus toimenpide)
      (tallenna-varusteen-tarkastus))))

(defn- tallenna-toteuma [db urakka-id kirjaaja varustetoteuma]
  (let [toteuma (assoc (get-in varustetoteuma [:varustetoteuma :toteuma]) :reitti nil)
        ;varustetiedot (get-in varustetoteuma [:varustetoteuma :varuste])
        ;sijainti (get-in varustetoteuma [:varustetoteuma :sijainti])
        ;aika (aika-string->java-sql-date (get-in varustetoteuma [:varustetoteuma :toteuma :alkanut]))
        ]
    (log/debug "Tallennetaan toteuman perustiedot")
    (let [toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma)]
      (log/debug "Tallennetaan sijainti reittipisteenä")
      ; FIXME Sijainti on nykyään erikseen jokaisella toimenpiteellä.
      ;; Miten pitäisi tallentaa? Tallennetaanko jokaisesta toimenpiteestä oma toteuma omalla sijainnilla?
      #_(api-toteuma/tallenna-sijainti db sijainti aika toteuma-id)
      (log/debug "Tallennetaan toteuman tehtävät")
      (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id)
      (log/debug "Tallennetaan toteuman varustetietodot")
      (poista-toteuman-varustetiedot db toteuma-id)
      (tallenna-varustetoteuman-toimenpiteet db
                                             kirjaaja
                                             (get-in varustetoteuma [:varustetoteuma :toimenpiteet])
                                             toteuma-id))))

(defn- tallenna-toteumat [db urakka-id kirjaaja varustetoteumat]
  (jdbc/with-db-transaction [db db]
    (doseq [varustetoteuma varustetoteumat]
      (tallenna-toteuma db urakka-id kirjaaja varustetoteuma))))

(defn- laheta-kirjaus-tierekisteriin [db tierekisteri kirjaaja varustetoteumat]
  (doseq [varustetoteuma varustetoteumat]
    ;; FIXME Päivitä käyttämään uutta tietomallia
    #_(let [vastaus (paivita-muutos-tierekisteriin tierekisteri db kirjaaja varustetoteuma)]
        (log/debug "Varustetoteuman kirjaus tierekisteriin suoritettu"))))

(defn- validoi-tehtavat [db varustetoteumat]
  (doseq [varustetoteuma varustetoteumat]
    (toteuman-validointi/tarkista-tehtavat db (get-in varustetoteuma [:varustetoteuma :toteuma :tehtavat]))))

(defn kirjaa-toteuma
  "Varustetoteuman kirjauksessa kirjataan yksi tai useampi toteuma.
   Jokainen toteuma voi sisältää useita toimenpiteitä (varusteen lisäys, poisto, päivitys, tarkastus)"
  [tierekisteri db {id :id} {:keys [varustetoteumat] :as payload} kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi varustetoteuma urakalle id:" urakka-id
               " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (validoi-tehtavat db varustetoteumat)
    (tallenna-toteumat db urakka-id kirjaaja varustetoteumat)
    (laheta-kirjaus-tierekisteriin db tierekisteri kirjaaja varustetoteumat)
    (tee-onnistunut-vastaus)))

(defrecord Varustetoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :lisaa-varustetoteuma
      (POST "/api/urakat/:id/toteumat/varuste" request
        (kasittele-kutsu-async
          db
          integraatioloki
          :lisaa-varustetoteuma
          request
          json-skeemat/varustetoteuman-kirjaus
          json-skeemat/kirjausvastaus
          (fn [parametit data kayttaja db]
            (kirjaa-toteuma tierekisteri db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-varustetoteuma)
    this))
