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
            [harja.kyselyt.toteumat :as toteumat-q]
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
  (tee-kirjausvastauksen-body
    {:ilmoitukset "Varustetoteuma kirjattu onnistuneesti."
     :uudet-idt (mapv :uusi-id vastaukset)}))

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
    (try
      (tr-tietolaji/validoi-tietolajin-arvot
       tietolaji
       (clojure.walk/stringify-keys arvot)
       tietolajin-kuvaus)
      (catch Exception e
        (throw+ {:type virheet/+viallinen-kutsu+
                 :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti (.getMessage e)}]})))
    (muunna-tietolajin-arvot-stringiksi
      tietolajin-kuvaus
      arvot)))

(defn- lisaa-varuste-tierekisteriin [tierekisteri kirjaaja otsikko toimenpide livitunniste arvot-string]
  (log/debug "Lisätään varuste livitunnisteella " livitunniste " tierekisteriin")
  (let [valitettava-data (tierekisteri-sanomat/luo-varusteen-lisayssanoma
                           otsikko
                           kirjaaja
                           livitunniste
                           toimenpide
                           arvot-string)]
    (let [vastaus (tierekisteri/lisaa-tietue tierekisteri valitettava-data)]
      (log/debug "Tierekisterin vastaus: " (pr-str vastaus))
      (assoc vastaus :uusi-id livitunniste))))

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
  [tierekisteri db kirjaaja toteuma-id otsikko varustetoteuma]
  ;; FIXME On mahdollista, joskin epätodennäköistä, että kirjaus lähtee tierekisteriin,
  ;; mutta kuittausta ei koskaan saada. Tällöin varuste saatetaan kirjata kahdesti jos
  ;; sama payload lähetetään Harjaan uudelleen.
  (when tierekisteri
    (doseq [toimenpide (get-in varustetoteuma [:varustetoteuma :toimenpiteet])]
      (let [toimenpide-tyyppi (first (keys toimenpide))
            toimenpiteen-tiedot (toimenpide-tyyppi toimenpide)
            tunniste (if (= toimenpide-tyyppi :varusteen-poisto)
                       (:tunniste toimenpiteen-tiedot)
                       (get-in toimenpiteen-tiedot [:varuste :tunniste]))
            tietolajin-arvot-string (:arvot-string toimenpide)]

        (log/debug "Valmistellaan toimenpiteen lähetys tierekisteriin, tyyppi: " (pr-str toimenpide-tyyppi))
        ;; On mahdollista, että sama toteuma ja toimenpide lähetetään Harjaan useaan kertaan. Tässä tilanteessa
        ;; tarkistetaan, onko toimenpide jo lähetetty tierekisteriin. Jos on, sitä ei lähetetä uudelleen."
        (let [lahetetty? (true? (first (toteumat-q/hae-varustetoteuman-lahetystiedot
                                         db
                                         {:id (:varustetoteuma-id toimenpide)})))]
          (if lahetetty?
            (log/debug "Toimenpide on jo lähetetty, ohitetaan.")

            (do
              (case toimenpide-tyyppi
                :varusteen-lisays
                (lisaa-varuste-tierekisteriin tierekisteri kirjaaja otsikko toimenpiteen-tiedot
                                              tunniste
                                              tietolajin-arvot-string)

                :varusteen-poisto
                (poista-varuste-tierekisterista tierekisteri kirjaaja otsikko
                                                toimenpiteen-tiedot)

                :varusteen-paivitys
                (paivita-varuste-tierekisteriin tierekisteri kirjaaja otsikko
                                                toimenpiteen-tiedot tietolajin-arvot-string)

                :varusteen-tarkastus
                (paivita-varuste-tierekisteriin tierekisteri kirjaaja otsikko
                                                toimenpiteen-tiedot tietolajin-arvot-string))
              (toteumat-q/merkitse-varustetoteuma-lahetetyksi<! db (:varustetoteuma-id toimenpide)))))))))

(defn- luo-uusi-varustetoteuma [db kirjaaja toteuma-id varustetoteuma toimenpiteen-tiedot tietolaji
                                tunniste tehty-toimenpide tie toimenpiteen-arvot-tekstina]
  (:id (toteumat-q/luo-varustetoteuma<!
         db
         {:tunniste tunniste
          :toteuma toteuma-id
          :toimenpide tehty-toimenpide
          :tietolaji tietolaji
          :arvot toimenpiteen-arvot-tekstina
          :karttapvm (get-in toimenpiteen-tiedot [:varuste :tietue :karttapvm])
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

(def toimenpide-tyyppi->toimenpide
  {:varusteen-lisays "lisatty"
   :varusteen-paivitys "paivitetty"
   :varusteen-poisto "poistettu"
   :varusteen-tarkastus "tarkastus"})

(defn- etsi-varustetoteuman-id
  "Etsii toimenpiteen varustetoteuman kannasta. Lisäys- poisto- ja tarkastusoperaatioissa
   voidaan yksilöidä helposti annetulla tunnisteella. Lisäysoperaatio yksilöidään sen muiden
   tietojen perusteella."
  [db toteuma-id tunniste tietolaji toimenpiteen-tiedot tehty-toimenpide]
  (:id (first (toteumat-q/hae-varustetoteuman-id
                db
                {:toteumaid toteuma-id
                 :tunniste tunniste
                 :tietolaji tietolaji
                 :toimenpide tehty-toimenpide
                 :tr_numero (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :numero])
                 :tr_aosa (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :aosa])
                 :tr_aet (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :aet])
                 :tr_losa (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :losa])
                 :tr_let (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :let])
                 :tr_ajorata (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :ajr])
                 :tr_puoli (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie :puoli])}))))

(defn- tallenna-varustetoteuman-toimenpiteet
  "Luo jokaisesta varustetoteuman toimenpiteestä varustetoteuman.
   Palauttaa päivitetyt varustetoteumat:
   - Kaikille toimenpiteille liitetään niiden varustetoteuman id kannassa.
   - Lisäystoimenpiteelle on lisäksi lisätty Harjan luoma tunniste"
  [db tierekisteri toteuma-id kirjaaja varustetoteuma]
  (log/debug "Tallennetaan toteuman toimenpiteet.")
  (mapv
    (fn [toimenpide]
      (let [toimenpide-tyyppi (first (keys toimenpide))
            _ (log/debug "Käsitellään toimenpide tyyppiä: " (pr-str toimenpide-tyyppi))
            toimenpiteen-tiedot (toimenpide-tyyppi toimenpide)
            tietolaji (if (not= toimenpide-tyyppi :varusteen-poisto)
                        (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
                        (:tietolajitunniste toimenpiteen-tiedot))
            tietolajin-arvot (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :arvot])
            tunniste (if (not= toimenpide-tyyppi :varusteen-poisto)
                       (get-in toimenpiteen-tiedot [:varuste :tunniste])
                       (:tunniste toimenpiteen-tiedot))
            tie (get-in toimenpiteen-tiedot [:varuste :tietue :sijainti :tie])
            tietolajin-arvot-string (when tietolajin-arvot
                                      (validoi-ja-muunna-arvot-merkkijonoksi
                                        tierekisteri
                                        tietolajin-arvot
                                        tietolaji))]

        ;; On mahdollista, että sama toteuma lähetetään useaan kertaan. Tässä tilanteessa
        ;; tarkistetaan, onko toimenpide jo tallennettu. Jos on, sitä ei tallenneta uudelleen."
        (if-let [varustetoteuma-id (etsi-varustetoteuman-id db
                                                            toteuma-id
                                                            tunniste
                                                            tietolaji
                                                            toimenpiteen-tiedot
                                                            (toimenpide-tyyppi->toimenpide toimenpide-tyyppi))]
          (do (log/debug "Toimenpide on jo tallennettu, ohitetaan.")
              (assoc toimenpide :varustetoteuma-id varustetoteuma-id))

          (case toimenpide-tyyppi
            :varusteen-lisays
            (let [uusi-livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
                  varustetoteuma-id (luo-uusi-varustetoteuma db
                                                             kirjaaja
                                                             toteuma-id
                                                             varustetoteuma
                                                             toimenpiteen-tiedot
                                                             tietolaji
                                                             uusi-livitunniste
                                                             "lisatty"
                                                             tie
                                                             tietolajin-arvot-string)]
              (-> toimenpide
                  (assoc :varustetoteuma-id varustetoteuma-id)
                  (assoc :arvot-string tietolajin-arvot-string)
                  (assoc-in [:varusteen-lisays :varuste :tunniste] uusi-livitunniste)))

            :varusteen-paivitys
            (let [varustetoteuma-id (luo-uusi-varustetoteuma db
                                                             kirjaaja
                                                             toteuma-id
                                                             varustetoteuma
                                                             toimenpiteen-tiedot
                                                             tietolaji
                                                             tunniste
                                                             "paivitetty"
                                                             tie
                                                             tietolajin-arvot-string)]
              (-> toimenpide
                  (assoc :arvot-string tietolajin-arvot-string)
                  (assoc :varustetoteuma-id varustetoteuma-id)))

            :varusteen-poisto
            (let [varustetoteuma-id (luo-uusi-varustetoteuma db
                                                             kirjaaja
                                                             toteuma-id
                                                             varustetoteuma
                                                             toimenpiteen-tiedot
                                                             tietolaji
                                                             tunniste
                                                             "poistettu"
                                                             nil
                                                             nil)]
              (assoc toimenpide :varustetoteuma-id varustetoteuma-id))

            :varusteen-tarkastus
            (let [varustetoteuma-id (luo-uusi-varustetoteuma db
                                                             kirjaaja
                                                             toteuma-id
                                                             varustetoteuma
                                                             toimenpiteen-tiedot
                                                             tietolaji
                                                             tunniste
                                                             "tarkastus"
                                                             tie
                                                             tietolajin-arvot-string)]
              (-> toimenpide
                  (assoc :arvot-string tietolajin-arvot-string)
                  (assoc :varustetoteuma-id varustetoteuma-id)))))))
    (get-in varustetoteuma [:varustetoteuma :toimenpiteet])))

(defn- tallenna-toteumat
  "Tallentaa varustetoteumat kantaan. Palauttaa päivitetyt varustetoteumat, joiden metadataan on liitetty
   toteuman kanta-id."
  [db tierekisteri urakka-id kirjaaja varustetoteumat]
  (jdbc/with-db-transaction [db db]
    (mapv (fn [varustetoteuma]
            (log/debug "Tallennetaan toteuman perustiedot")
            (let [toteuma (assoc
                            (get-in varustetoteuma [:varustetoteuma :toteuma])
                            :reitti nil)
                  toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma)]
              (log/debug "Toteuman perustiedot tallennettu, toteuma-id: " (pr-str toteuma-id))

              (log/debug "Tallennetaan toteuman tehtävät")
              (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id)

              ;; FIXME Sijainti oli ennen varustetoteumassa x/y koordinatti, tallennettin reittipisteenä.
              ;; Ota toimenpiteiden TR-osoitteet ja muodosta niistä geometriat
              ;; Mietittävä miten hanskataan koska frontissa oletetaan tällä hetkellä että sijainti on yksi piste

              (let [paivitetyt-toimenpiteet (tallenna-varustetoteuman-toimenpiteet
                                              db
                                              tierekisteri
                                              toteuma-id
                                              kirjaaja
                                              varustetoteuma)]
                (-> varustetoteuma
                    (assoc-in [:varustetoteuma :toimenpiteet] paivitetyt-toimenpiteet)
                    (with-meta {:toteuma-id toteuma-id})))))
          varustetoteumat)))

(defn- laheta-kirjaus-tierekisteriin
  "Lähettää varustetoteumat tierekisteriin yksi kerrallaan.
   Palauttaa vectorissa tierekisterikomponentin antamat vastaukset."
  [db tierekisteri kirjaaja otsikko varustetoteumat]
  (mapv (fn [varustetoteuma]
          (let [vastaus (laheta-varustetoteuman-toimenpiteet-tierekisteriin
                          tierekisteri
                          db
                          kirjaaja
                          (:toteuma-id (meta varustetoteuma))
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
    (let [varustetoteumat (tallenna-toteumat db tierekisteri urakka-id kirjaaja varustetoteumat)
          tierekisterin-vastaukset (laheta-kirjaus-tierekisteriin db tierekisteri kirjaaja otsikko varustetoteumat)]
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
