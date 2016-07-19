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
            [harja.palvelin.integraatiot.api.validointi.toteumat :as toteuman-validointi]
            [harja.domain.tierekisterin-tietolajin-kuvauksen-kasittely :as tr-tietolaji]
            [clj-time.core :as t]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
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

;; FIXME Rikki
#_(defn- paivita-muutos-tierekisteriin
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

(defn validoi-tietolajin-arvot
  "Tarkistaa, että API:n kautta tulleet tietolajin arvot on annettu oikein.
   Jos arvoissa on ongelma, heittää poikkeuksen. Jos arvot ovat ok, palauttaa nil."
  [tietolaji arvot tietolajin-kuvaus]
  (let [arvot (clojure.walk/stringify-keys arvot)
        kenttien-kuvaukset (sort-by :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))]
    (doseq [kentan-kuvaus kenttien-kuvaukset]
      (tr-tietolaji/validoi-arvo (clojure.walk/stringify-keys (get arvot (:kenttatunniste kentan-kuvaus)))
                                 kentan-kuvaus
                                 tietolaji))))

(defn- muunna-tietolajin-arvot-stringiksi [tietolajin-kuvaus arvot-map]
  (tr-tietolaji/tietolajin-arvot-map->string
    (clojure.walk/stringify-keys arvot-map)
    tietolajin-kuvaus))

(defn- tallenna-varusteen-lisays [db kirjaaja tierekisteri varustetoteuma toimenpiteen-tiedot toteuma-id]
  ;; FIXME Sijainti oli ennen varustetoteumassa x/y koordinatti, entä nyt? päätelläänkö toimenpiteen tieosoitteesta?
  ;; FIXME Tallennetaanko myös lisääjä johonkin?
  (let [tietojalin-kuvaus (tierekisteri/hae-tietolajit
                            tierekisteri
                            (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
                            nil)
        tietolaji [:varuste :tietue :tietolaji :tunniste]
        tietolajin-arvot [:varuste :tietue :tietolaji :arvot]]
    (validoi-tietolajin-arvot
      tietolaji
      tietolajin-arvot
      tietojalin-kuvaus)
    (:id (toteumat/luo-varustetoteuma<!
           db
           "" ;; FIXME Varustetoteuman tunniste, tätäkö ei enää tule?
           toteuma-id
           "lisatty"
           (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
           (muunna-tietolajin-arvot-stringiksi
             tietojalin-kuvaus
             tietolajin-arvot)
           nil ;; FIXME karttapvm puuttuu
           ;; FIXME Tartteeko varustetoteuma omaa alkanut/paattynyt aikaa, näkee suoraan toteumasta?
           (get-in varustetoteuma [:varustetoteuma :toteuma :alkanut])
           (get-in varustetoteuma [:varustetoteuma :toteuma :paattynyt])
           nil ; FIXME Piiri puuttuu?
           nil ; FIXME Kuntoluokitus puuttuu?
           nil ; FIXME tierekisteriurakkakoodi puuttuu?
           (:id kirjaaja)
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :numero])
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :aosa])
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :aet])
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :losa])
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :let])
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :puoli])
           (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :ajr])))))

(defn- tallenna-varusteen-paivitys []
  (log/debug "Tallennetaan varustetoteuman toimenpide: päivitetty varaste")
  ;; TODO Lisää / päivitä toteumaan reittipiste
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varusteen-poisto []
  (log/debug "Tallennetaan varustetoteuman toimenpide: poistettu varaste")
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varusteen-tarkastus []
  (log/debug "Tallennetaan varustetoteuman toimenpide: tarkastettu varaste")
  ;; TODO Lisää / päivitä toteumaan reittipiste
  ;; TODO
  #_(tallenna-toimenpide db kirjaaja varustetiedot toteuma-id))

(defn- tallenna-varustetoteuma
  "Luo jokaisesta varustetoteuman toimenpiteestä oman toteuman
   Toteuman tiedot ovat pääosin samat jokaisella toimenpiteellä, mutta mm.
   sijainti on eri."
  [db tierekisteri urakka-id kirjaaja varustetoteuma]
  (doseq [toimenpide (get-in varustetoteuma [:varustetoteuma :toimenpiteet])]
    (log/debug "Tallennetaan toteuman perustiedot")
    (let [toteuma (assoc
                    (get-in varustetoteuma [:varustetoteuma :toteuma])
                    :reitti nil)
          toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma)]

      (log/debug "Tallennetaan toteuman tehtävät")
      (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id)

      (log/debug "Tallennetaan toteuman varustetietodot")
      (poista-toteuman-varustetiedot db toteuma-id)
      (let [toimenpide-tyyppi (first (keys toimenpide))
            toimenpiteen-tiedot (toimenpide-tyyppi toimenpide)]
        (condp = toimenpide-tyyppi

          :varusteen-lisays
          (tallenna-varusteen-lisays db kirjaaja tierekisteri
                                     varustetoteuma toimenpiteen-tiedot toteuma-id)

          :varusteen-poisto
          (tallenna-varusteen-poisto)

          :varusteen-paivitys
          (tallenna-varusteen-paivitys)

          :varusteen-tarkastus
          (tallenna-varusteen-tarkastus))))))

(defn- tallenna-toteumat [db tierekisteri urakka-id kirjaaja varustetoteumat]
  (jdbc/with-db-transaction [db db]
    (doseq [varustetoteuma varustetoteumat]
      (tallenna-varustetoteuma db
                               tierekisteri
                               urakka-id
                               kirjaaja
                               varustetoteuma))))

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
    (tallenna-toteumat db tierekisteri urakka-id kirjaaja varustetoteumat)
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
