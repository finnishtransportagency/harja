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

;; TODO Temppivastaus, katsotaan saadaanko lisätiedot mukaan
(defn- tee-onnistunut-vastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Varustetoteuma kirjattu onnistuneesti."}))

;; FIXME Nyt kun varustetoteumia voi kirjata kerralla monta,
;; pitäisi kai kerätä kaikkien tierekisterin pyyntöjen vastaukset yhteen jotta voidaan näyttää
;; vastauksessa?
#_(defn- tee-onnistunut-vastaus [{:keys [lisatietoja uusi-id]}]
    (tee-kirjausvastauksen-body {:ilmoitukset (str "Varustetoteuma kirjattu onnistuneesti." (when lisatietoja lisatietoja))
                                 :id (when uusi-id uusi-id)}))

(defn- lisaa-varuste-tierekisteriin [tierekisteri db kirjaaja otsikko {:keys [varustetoteuma]} toimenpide]
  (log/debug "Lisätään varuste tierekisteriin")
  (let [livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
        varustetoteuma (assoc-in varustetoteuma [:varuste :tunniste] livitunniste)
        valitettava-data (tierekisteri-sanomat/luo-varusteen-lisayssanoma
                           otsikko
                           kirjaaja
                           varustetoteuma
                           toimenpide)]
    (let [vastaus (tierekisteri/lisaa-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      (assoc (assoc vastaus :lisatietoja (str " Uuden varusteen livitunniste on: " livitunniste)) :uusi-id livitunniste))))

(defn- paivita-varuste-tierekisteriin [tierekisteri kirjaaja otsikko {:keys [varustetoteuma]} toimenpide]
  (log/debug "Päivitetään varuste tierekisteriin")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-paivityssanoma
                           otsikko
                           kirjaaja
                           varustetoteuma
                           toimenpide)]
    (let [vastaus (tierekisteri/paivita-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn- poista-varuste-tierekisterista [tierekisteri kirjaaja otsikko {:keys [varustetoteuma]} toimenpide]
  (log/debug "Poistetaan varuste tierekisteristä")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-poistosanoma
                           otsikko
                           kirjaaja
                           varustetoteuma
                           toimenpide)]
    (let [vastaus (tierekisteri/poista-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn- laheta-varustetoteuman-toimenpiteet-tierekisteriin
    "Päivittää varustetoteumassa tehdyt toimenpiteet Tierekisteriin.
    On mahdollista, että muutoksen välittäminen Tierekisteriin epäonnistuu.
    Tässä tapauksessa halutaan, että muutos jää kuitenkin Harjaan ja Harjan integraatiolokeihin, jotta
    nähdään, että toteumaa on yritetty kirjata."
    [tierekisteri db kirjaaja otsikko varustetoteuma]
    (when tierekisteri
      (doseq [toimenpide (get-in varustetoteuma [:varustetoteuma :toimenpiteet])]
        (let [toimenpide-tyyppi (first (keys toimenpide))
              toimenpiteen-tiedot (toimenpide-tyyppi toimenpide)]

          (condp = toimenpide-tyyppi
            :varusteen-lisays
            (lisaa-varuste-tierekisteriin tierekisteri db kirjaaja otsikko varustetoteuma toimenpiteen-tiedot)

            :varusteen-poisto
            (poista-varuste-tierekisterista tierekisteri kirjaaja otsikko varustetoteuma toimenpiteen-tiedot)

            :varusteen-paivitys
            (paivita-varuste-tierekisteriin tierekisteri kirjaaja otsikko varustetoteuma toimenpiteen-tiedot)

            :varusteen-tarkastus
            (paivita-varuste-tierekisteriin tierekisteri kirjaaja otsikko varustetoteuma toimenpiteen-tiedot))))))

(defn- poista-toteuman-varustetiedot [db toteuma-id]
  (log/debug "Poistetaan toteuman " toteuma-id " vanhat varustetiedot")
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

(defn- luo-uusi-varustetoteuma [db kirjaaja toteuma-id varustetoteuma tietolaji
                                tunniste toimenpide tie toimenpiteen-arvot-tekstina]
  (:id (toteumat/luo-varustetoteuma<!
         db
         tunniste
         toteuma-id
         toimenpide
         tietolaji
         toimenpiteen-arvot-tekstina
         nil ;; FIXME karttapvm puuttuu
         ;; FIXME Tartteeko varustetoteuma omaa alkanut/paattynyt aikaa, näkee suoraan toteumasta?
         (aika-string->java-sql-date (get-in varustetoteuma [:varustetoteuma :toteuma :alkanut]))
         (aika-string->java-sql-date (get-in varustetoteuma [:varustetoteuma :toteuma :paattynyt]))
         nil ; FIXME Piiri puuttuu?
         nil ; FIXME Kuntoluokitus puuttuu?
         nil ; FIXME tierekisteriurakkakoodi puuttuu?
         (:id kirjaaja)
         (:numero tie)
         (:aosa tie)
         (:aet tie)
         (:losa tie)
         (:let tie)
         (:puoli tie)
         (:ajr tie))))

(defn- tallenna-varusteen-lisays [db kirjaaja varustetoteuma tietolajin-arvot-string
                                  toimenpide toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: lisätty varaste")
  ; FIXME Tallennetaanko myös lisääjä johonkin?
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                           nil
                           "lisatty"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           tietolajin-arvot-string))

(defn- tallenna-varusteen-paivitys [db kirjaaja varustetoteuma tietolajin-arvot-string
                                    toimenpide toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: päivitetty varaste")
  ;; FIXME Tallennetaanko myös päivittäjä johonkin?
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                           (get-in toimenpide [:varuste :tunniste])
                           "paivitetty"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           tietolajin-arvot-string))

(defn- tallenna-varusteen-poisto [db kirjaaja varustetoteuma toimenpide toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: poistettu varuste")
  ;; FIXME Tallennetaanko myös poistaja johonkin?
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           (:tietolajitunniste toimenpide)
                           (:tunniste toimenpide)
                           "poistettu"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           nil))

(defn- tallenna-varusteen-tarkastus [db kirjaaja varustetoteuma toimenpide tietolajin-arvot-string
                                     toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: tarkastettu varaste")
  ;; FIXME Tallennetaanko myös tarkastaja johonkin?
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                           (get-in toimenpide [:varuste :tunniste])
                           "tarkastus"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           tietolajin-arvot-string))

(defn- tallenna-varustetoteuman-toimenpiteet
  "Luo jokaisesta varustetoteuman toimenpiteestä varustetoteuman"
  [db tierekisteri toteuma-id kirjaaja varustetoteuma]
  (poista-toteuman-varustetiedot db toteuma-id)
  (log/debug "Tallennetaan toteuman varustetietodot")
  (doseq [toimenpide (get-in varustetoteuma [:varustetoteuma :toimenpiteet])]
    (let [toimenpide-tyyppi (first (keys toimenpide))
          toimenpiteen-tiedot (toimenpide-tyyppi toimenpide)
          tietolajin-arvot (get-in varustetoteuma [:varuste :tietue :tietolaji :arvot])
          tietolajin-arvot-string (when tietolajin-arvot
                                    (let [tietolaji (get-in varustetoteuma [:varuste :tietue :tietolaji :tunniste])
                                          tietolajin-kuvaus (tierekisteri/hae-tietolajit
                                                              tierekisteri
                                                              tietolaji
                                                              nil)]
                                      (validoi-tietolajin-arvot
                                        tietolaji
                                        tietolajin-arvot
                                        tietolajin-kuvaus)
                                      (muunna-tietolajin-arvot-stringiksi
                                        tietolajin-kuvaus
                                        tietolajin-arvot)))]

      (condp = toimenpide-tyyppi
        :varusteen-lisays
        (tallenna-varusteen-lisays db kirjaaja varustetoteuma tietolajin-arvot-string
                                   toimenpiteen-tiedot toteuma-id)

        :varusteen-poisto
        (tallenna-varusteen-poisto db kirjaaja varustetoteuma
                                   toimenpiteen-tiedot toteuma-id)

        :varusteen-paivitys
        (tallenna-varusteen-paivitys db kirjaaja varustetoteuma tietolajin-arvot-string
                                     toimenpiteen-tiedot toteuma-id)

        :varusteen-tarkastus
        (tallenna-varusteen-tarkastus db kirjaaja varustetoteuma toimenpiteen-tiedot
                                      tietolajin-arvot-string toteuma-id)))))

(defn- tallenna-toteumat [db tierekisteri urakka-id kirjaaja varustetoteumat]
  (jdbc/with-db-transaction [db db]
    (doseq [varustetoteuma varustetoteumat]
      (log/debug "Tallennetaan toteuman perustiedot")
      (let [toteuma (assoc
                      (get-in varustetoteuma [:varustetoteuma :toteuma])
                      :reitti nil)
            toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma)]
        (log/debug "Toteuman perustiedot tallennettu, toteuma-id: " (pr-str toteuma-id))

        (log/debug "Tallennetaan toteuman tehtävät")
        (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id)

        ;; FIXME Sijainti oli ennen varustetoteumassa x/y koordinatti, tallennettin reittipisteenä. Entä nyt?

        (tallenna-varustetoteuman-toimenpiteet db
                                               tierekisteri
                                               toteuma-id
                                               kirjaaja
                                               varustetoteuma)))))

(defn- laheta-kirjaus-tierekisteriin [db tierekisteri kirjaaja otsikko varustetoteumat]
  (doseq [varustetoteuma varustetoteumat]
    (let [vastaus (laheta-varustetoteuman-toimenpiteet-tierekisteriin tierekisteri db kirjaaja otsikko varustetoteuma)]
        (log/debug "Varustetoteuman kirjaus tierekisteriin suoritettu"))))

(defn- validoi-tehtavat [db varustetoteumat]
  (doseq [varustetoteuma varustetoteumat]
    (toteuman-validointi/tarkista-tehtavat db (get-in varustetoteuma [:varustetoteuma :toteuma :tehtavat]))))

(defn kirjaa-toteuma
  "Varustetoteuman kirjauksessa kirjataan yksi tai useampi toteuma.
   Jokainen toteuma voi sisältää useita toimenpiteitä (varusteen lisäys, poisto, päivitys, tarkastus)"
  [tierekisteri db {id :id} {:keys [otsikko varustetoteumat] :as payload} kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi varustetoteuma urakalle id:" urakka-id
               " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (validoi-tehtavat db varustetoteumat)
    (tallenna-toteumat db tierekisteri urakka-id kirjaaja varustetoteumat)
    ;; FIXME Korjaa tierekisteriin lähetyssanomat
    #_(laheta-kirjaus-tierekisteriin db tierekisteri kirjaaja otsikko varustetoteumat)
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
