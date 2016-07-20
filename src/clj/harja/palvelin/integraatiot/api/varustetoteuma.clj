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
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- tee-onnistunut-vastaus [vastaukset]
  ;; FIXME Jokainen toimenpide on kirjattu tierekisteriin erikseen ja myös vastaus saadaan jokaisesta
  ;; erikseen. Nyt vastaukset joinataan yhteen str/join:lla. Vastaukset ovat kirjattujen toimenpiteiden
  ;; mukaisessa järjestyksessä. Voi silti hankalaa ottaa selvää mikä vastaus liittyy mihinkin
  ;; kirjaukseen, etenkin jos toimenpiteitä kirjattiin monta.
  (tee-kirjausvastauksen-body
    {:ilmoitukset
     (str "Varustetoteuma kirjattu onnistuneesti: " (map #(str/join ", " (:lisatietoja %)) vastaukset))
     :idt (map #(str/join ", " (:uusi-id %)) vastaukset)}))

(defn validoi-tietolajin-arvot
  "Tarkistaa, että API:n kautta tulleet tietolajin arvot on annettu oikein.
   Jos arvoissa on ongelma, heittää poikkeuksen. Jos arvot ovat ok, palauttaa nil."
  [tietolaji arvot tietolajin-kuvaus]
  ;; FIXME Tarkista vielä, ettei ole ylimääräisiä kenttiä (tr-tietolaji-namespaceen validointifunktio joka ottaa tämänkin huomioon?)
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

(defn- validoi-ja-muunna-arvot-merkkijonoksi
  "Hakee tietolajin kuvauksen, validoi arvot sen pohjalta ja muuntaa arvot merkkijonoksi"
  [tierekisteri arvot tietolaji]
  (let [tietolajin-kuvaus (tierekisteri/hae-tietolajit
                            tierekisteri
                            tietolaji
                            nil)]
    (validoi-tietolajin-arvot
      tietolaji
      arvot
      tietolajin-kuvaus)
    (muunna-tietolajin-arvot-stringiksi
      tietolajin-kuvaus
      arvot)))

(defn- lisaa-varuste-tierekisteriin [tierekisteri db kirjaaja otsikko toimenpide arvot-string]
  (log/debug "Lisätään varuste tierekisteriin")
  (let [livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
        valitettava-data (tierekisteri-sanomat/luo-varusteen-lisayssanoma
                           otsikko
                           kirjaaja
                           livitunniste
                           toimenpide
                           arvot-string)]
    (let [vastaus (tierekisteri/lisaa-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      (assoc vastaus :lisatietoja (str " Uuden varusteen livitunniste on: " livitunniste)
                     :uusi-id livitunniste))))

(defn- paivita-varuste-tierekisteriin [tierekisteri kirjaaja otsikko toimenpide arvot-string]
  (log/debug "Päivitetään varuste tierekisteriin")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-paivityssanoma
                           otsikko
                           kirjaaja
                           toimenpide
                           arvot-string)]
    (let [vastaus (tierekisteri/paivita-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      vastaus)))

(defn- poista-varuste-tierekisterista [tierekisteri kirjaaja otsikko toimenpide]
  (log/debug "Poistetaan varuste tierekisteristä")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-poistosanoma
                           otsikko
                           kirjaaja
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
            toimenpiteen-tiedot (toimenpide-tyyppi toimenpide)
            tietolaji (get-in varustetoteuma [:varuste :tietue :tietolaji :tunniste])
            tietolajin-arvot (get-in varustetoteuma [:varuste :tietue :tietolaji :arvot])
            tietolajin-arvot-string (when tietolajin-arvot
                                      (validoi-ja-muunna-arvot-merkkijonoksi
                                        tierekisteri
                                        tietolajin-arvot
                                        tietolaji))]

        (condp = toimenpide-tyyppi
          :varusteen-lisays
          (lisaa-varuste-tierekisteriin tierekisteri db kirjaaja otsikko
                                        toimenpiteen-tiedot tietolajin-arvot-string)

          :varusteen-poisto
          (poista-varuste-tierekisterista tierekisteri kirjaaja otsikko
                                          toimenpiteen-tiedot)

          :varusteen-paivitys
          (paivita-varuste-tierekisteriin tierekisteri kirjaaja otsikko
                                          toimenpiteen-tiedot tietolajin-arvot-string)

          :varusteen-tarkastus
          (paivita-varuste-tierekisteriin tierekisteri kirjaaja otsikko
                                          toimenpiteen-tiedot tietolajin-arvot-string))))))

(defn- poista-toteuman-varustetiedot [db toteuma-id]
  (log/debug "Poistetaan toteuman " toteuma-id " vanhat varustetiedot")
  (toteumat/poista-toteuman-varustetiedot!
    db
    toteuma-id))

(defn- luo-uusi-varustetoteuma [db kirjaaja toteuma-id varustetoteuma toimenpiteen-tiedot tietolaji
                                tunniste tehty-toimenpide tie toimenpiteen-arvot-tekstina]
  (:id (toteumat/luo-varustetoteuma<!
         db
         {:tunniste tunniste
          :toteuma toteuma-id
          :toimenpide tehty-toimenpide
          :tietolaji tietolaji
          :arvot toimenpiteen-arvot-tekstina
          :karttapvm (get-in toimenpiteen-tiedot [:varuste :tietue :karttapvm])
          ;; FIXME Tartteeko varustetoteuma omaa alkanut/paattynyt aikaa, näkee suoraan toteumasta?
          :alkupvm (aika-string->java-sql-date (get-in varustetoteuma [:varustetoteuma :toteuma :alkanut]))
          :loppupvm (aika-string->java-sql-date (get-in varustetoteuma [:varustetoteuma :toteuma :paattynyt]))
          :piiri (get-in toimenpiteen-tiedot [:varuste :tietue :piiri])
          :kuntoluokka (get-in toimenpiteen-tiedot [:varuste :tietue :kuntoluokitus])
          :tierekisteriurakkakoodi (get-in toimenpiteen-tiedot [:varuste :tietue :tierekisteriurakkakoodi])
          :luoja (:id kirjaaja)
          :tr_numero (:numero tie)
          :tr_alkuosa (:aosa tie)
          :tr_alkuetaisyys (:aet tie)
          :tr_loppuosa (:losa tie)
          :tr_loppuetaisyys (:let tie)
          :tr_puoli (:puoli tie)
          :tr_ajorata (:ajr tie)})))

(defn- tallenna-varusteen-lisays [db kirjaaja varustetoteuma tietolajin-arvot-string
                                  toimenpide toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: lisätty varaste")
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           toimenpide
                           (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                           nil
                           "lisatty"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           tietolajin-arvot-string))

(defn- tallenna-varusteen-paivitys [db kirjaaja varustetoteuma tietolajin-arvot-string
                                    toimenpide toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: päivitetty varaste")
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           toimenpide
                           (get-in toimenpide [:varuste :tietue :tietolaji :tunniste])
                           (get-in toimenpide [:varuste :tunniste])
                           "paivitetty"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           tietolajin-arvot-string))

(defn- tallenna-varusteen-poisto [db kirjaaja varustetoteuma toimenpide toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: poistettu varuste")
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           toimenpide
                           (:tietolajitunniste toimenpide)
                           (:tunniste toimenpide)
                           "poistettu"
                           (get-in toimenpide [:varuste :tietue :sijainti :tie])
                           nil))

(defn- tallenna-varusteen-tarkastus [db kirjaaja varustetoteuma toimenpide tietolajin-arvot-string
                                     toteuma-id]
  (log/debug "Tallennetaan varustetoteuman toimenpide: tarkastettu varaste")
  (luo-uusi-varustetoteuma db
                           kirjaaja
                           toteuma-id
                           varustetoteuma
                           toimenpide
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
          tietolaji (get-in varustetoteuma [:varuste :tietue :tietolaji :tunniste])
          tietolajin-arvot (get-in varustetoteuma [:varuste :tietue :tietolaji :arvot])
          tietolajin-arvot-string (when tietolajin-arvot
                                    (validoi-ja-muunna-arvot-merkkijonoksi
                                      tierekisteri
                                      tietolajin-arvot
                                      tietolaji))]
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

(defn- laheta-kirjaus-tierekisteriin
  "Lähettää varustetoteumat tierekisteriin yksi kerrallaan.
   Palauttaa vectorissa tierekisterikomponentin antamat vastaukset."
  [db tierekisteri kirjaaja otsikko varustetoteumat]
  (mapv (fn [varustetoteuma]
          (let [vastaus (laheta-varustetoteuman-toimenpiteet-tierekisteriin
                          tierekisteri
                          db
                          kirjaaja
                          otsikko
                          varustetoteuma)]
            vastaus))
        varustetoteumat))

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
    (let [tierekisterin-vastaukset (laheta-kirjaus-tierekisteriin db tierekisteri kirjaaja otsikko varustetoteumat)]
      (tee-onnistunut-vastaus tierekisterin-vastaukset))))

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
