(ns harja.palvelin.integraatiot.api.urakat
  "Urakan yleistietojen API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.tyokalut.muunnos :as muunnos]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodit]
            [harja.kyselyt.materiaalit :as q-materiaalit]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.apurit :as apurit]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn hae-tehtavat [db urakka-id]
  (let [yksikkohintaiset-tehtavat (q-toimenpidekoodit/hae-apin-kautta-seurattavat-yksikkohintaiset-tehtavat db urakka-id)
        kokonaishintaiset-tehtavat (q-toimenpidekoodit/hae-apin-kautta-seurattavat-kokonaishintaiset-tehtavat db urakka-id)
        tee-tehtavat #(mapv (fn [data] {:tehtava {:id (:apitunnus data) :selite (:nimi data) :yksikko (:yksikko data)}}) %)]
    (merge
      {:yksikkohintaiset (tee-tehtavat yksikkohintaiset-tehtavat)}
      {:kokonaishintaiset (tee-tehtavat kokonaishintaiset-tehtavat)})))

(defn hae-urakan-sopimukset [db urakka-id]
  (let [sopimukset (q-urakat/hae-urakan-sopimukset db urakka-id)]
    (for [sopimus sopimukset]
      {:sopimus sopimus})))

(defn hae-materiaalit [db]
  (let [materiaalit (q-materiaalit/hae-kaikki-materiaalit db)]
    (for [materiaali materiaalit]
      {:materiaali {:nimi (:nimi materiaali) :yksikko (:yksikko materiaali)}})))

(defn- urakan-tiedot [urakka]
  (let [urakka (if (= "teiden-hoito"(:tyyppi urakka))
                 (assoc urakka :tyyppi "hoito")
                                urakka)]
              (-> urakka
                  (select-keys #{:id :nimi :tyyppi :alkupvm :loppupvm
                                 :takuu_loppupvm :alueurakkanumero :urakoitsija})
                  (assoc :vaylamuoto "tie"))))

(defn muodosta-vastaus-urakan-haulle [db id urakka]
  {:urakka
   {:tiedot      (urakan-tiedot urakka)
    :sopimukset  (hae-urakan-sopimukset db id)
    :materiaalit (hae-materiaalit db)
    :tehtavat    (hae-tehtavat db (:id urakka))}})

(defn muodosta-vastaus-urakoiden-haulle [urakat]
  {:urakat (mapv (fn [urakka] {:urakka {:tiedot (urakan-tiedot urakka)}}) urakat)})

(defn hae-urakka-idlla [db {:keys [id]} kayttaja]
  (log/debug "Haetaan urakka id:llä: " id)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [urakka (some->> urakka-id (q-urakat/hae-urakka db) first konv/alaviiva->rakenne)]
      (muodosta-vastaus-urakan-haulle db urakka-id urakka))))

(defn hae-kayttajan-urakat [db parametrit {:keys [kayttajanimi] :as kayttaja}]
  (log/debug (format "Haetaan käyttäjän: %s urakat" kayttaja))
  (let [urakkatyyppi (get parametrit "urakkatyyppi")]
    (validointi/tarkista-urakkatyyppi urakkatyyppi)
    (muodosta-vastaus-urakoiden-haulle
      (konv/vector-mappien-alaviiva->rakenne
        (q-urakat/hae-jarjestelmakayttajan-urakat db kayttajanimi urakkatyyppi)))))

(defn hae-urakka-ytunnuksella [db parametrit {:keys [kayttajanimi] :as kayttaja}]
  (parametrivalidointi/tarkista-parametrit parametrit {:ytunnus "Y-tunnus puuttuu"})
  (let [{ytunnus :ytunnus} parametrit]
    (log/debug "Haetaan urakat y-tunnuksella: " ytunnus)
    (validointi/tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja)
    (let [organisaation-urakat (q-urakat/hae-urakat-ytunnuksella db ytunnus)
          erillisoikeus-urakat (filter (fn [eu] (not-any? (fn [ou] (= (:id ou) (:id eu))) organisaation-urakat))
                                       (q-urakat/hae-urakat-joihin-jarjestelmalla-erillisoikeus db kayttajanimi))
          urakat (konv/vector-mappien-alaviiva->rakenne (into organisaation-urakat erillisoikeus-urakat))]
      (muodosta-vastaus-urakoiden-haulle urakat))))

(defn- hae-urakka-sijainnilla*
  "Hakee urakan urakkatyypin perusteella, tai pyrkii hakemaan tulokset kaikilla urakkatyypeillä mikäli urakkatyyppiä
  ei ole määritelty."
  [db {:keys [x y aloitustoleranssi maksimitolenranssi urakkatyyppi]}]
  ;; Aloitetaan pistehaku aloitustoleranssilla tai default 50 metrin toleranssilla.
  (loop [threshold (or aloitustoleranssi 50)
         k 1]
    ;; Jos ei löydy urakkaa maksimitolenranssilla tai on yritetty hakea kasvavalla toleranssilla 10 kertaa, niin
    ;; palautetaan tyhjä tulos.
    (if (and
          (< threshold (or maksimitolenranssi 500))
          (< k 10))
      (let [urakat (if urakkatyyppi
                     (q-urakat/hae-urakka-sijainnilla db {:x x :y y
                                                          :threshold threshold
                                                          :urakkatyyppi urakkatyyppi})

                     ;; Jos urakkatyyppiä ei ole määritelty, yritetään hakea kaikki urakkatyypit annetulla sijainnilla
                     (let [urakat (mapv (fn [urakkatyyppi]
                                          (let [res (q-urakat/hae-urakka-sijainnilla db {:x x :y y
                                                                                         :threshold threshold
                                                                                         :urakkatyyppi urakkatyyppi})]
                                            res))
                                    ;; hoito = hoito tai teiden-hoito
                                    ;; paallystys = paallystys tai paikkaus
                                    ["hoito" "valaistus" "paallystys" "tekniset-laitteet" "siltakorjaus"])]
                       (into [] (flatten urakat))))]
        (if (empty? urakat)
          ;; Jos ei löydy tuloksia, tuplataan hakutoleranssi ja yritään uudestaan
          (recur (* 2 threshold) (inc k))
          urakat))
      [])))

(defn hae-urakka-sijainnilla
  "Palauttaa ne voimassaolevat urakat, jotka osuvat annettuun koordinaattiin ja täyttävät mahdollisesti annetun urakkatyyppi-ehdon.
   Urakkatyyppi 'hoito' tarkoittaa sekä hoito että teiden-hoito-urakoita (eli siis vanhanmallisia alueurakoita ja MH-urakoita).
   Jos urakkatyyppiä ei anneta, palautetaan kaiken tyyppiset urakat."
  [db parametrit kayttaja]
  (parametrivalidointi/tarkista-parametrit parametrit {:x "X-koordinaatti puuttuu"
                                                       :y "Y-koordinaatti puuttuu"})

  (jdbc/with-db-transaction [db db]
    (let [{:keys [x y urakkatyyppi]} parametrit
          x-easting (try+
                      (muunnos/str->double x)
                      (catch NumberFormatException _
                             (throw+ {:type virheet/+viallinen-kutsu+
                                      :virheet [{:koodi virheet/+virheellinen-sijainti+
                                                 :viesti "Virheellinen X-koordinaatti"}]})))
          y-northing (try+
                       (muunnos/str->double y)
                       (catch NumberFormatException _
                         (throw+ {:type virheet/+viallinen-kutsu+
                                  :virheet [{:koodi virheet/+virheellinen-sijainti+
                                             :viesti "Virheellinen Y-koordinaatti"}]})))
          ;; Aloitetaan pistehaku 50 m aloitustoleranssilla
          ;; Mikäli osumia ei tule, hakutoleranssia kasvatetaan vähitellen maksimitoleranssia kohti
          aloitustoleranssi 50
          ;; Pistehaun toleranssia kasvatatetaan maksimitoleranssiin asti
          maksimitolenranssi 500]

      (validointi/tarkista-koordinaattien-jarjestys [x-easting y-northing])

      (log/debug (str "Haetaan urakat sijainnilla x: " x ", y:" y
                   (when urakkatyyppi (str " ja urakkatyypillä: " urakkatyyppi))))

      (when urakkatyyppi
        (validointi/tarkista-urakkatyyppi urakkatyyppi))

      (let [urakat (hae-urakka-sijainnilla* db {:x x-easting :y y-northing
                                                :aloitustoleranssi aloitustoleranssi
                                                :maksimitoleranssi maksimitolenranssi
                                                :urakkatyyppi urakkatyyppi})
            urakat (konv/vector-mappien-alaviiva->rakenne urakat)
            urakat-suodatettu (into []
                                (filter (fn [urakka]
                                          ;; Ota tuloksiin mukaan vain urakat, joihin käyttäjällä on oikeus
                                          ;; Validointi heittää slingshot-virheen, jos käyttäjällä ei ole oikeuksia.
                                          (try+
                                            (validointi/tarkista-kayttajan-oikeudet-urakkaan
                                              db (:id urakka) kayttaja)
                                            true
                                            (catch Object _
                                              false))))
                                urakat)]
        (muodosta-vastaus-urakoiden-haulle urakat-suodatettu)))))

(def hakutyypit
  [{:palvelu :hae-urakka
    :api-oikeus :luku
    :polku "/api/urakat/:id"
    :vastaus-skeema json-skeemat/urakan-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja-id db]
                    (hae-urakka-idlla db parametrit kayttaja-id))}
   {:palvelu :hae-kayttajan-urakat
    :api-oikeus :luku
    :polku "/api/urakat/haku/"
    :vastaus-skeema json-skeemat/urakoiden-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja db]
                    (hae-kayttajan-urakat db parametrit kayttaja))}
   {:palvelu :hae-urakka-sijainnilla
    ;; Kaikilla sijaintihakutyypeille on oma polku /haku/sijainnilla
    ;; Mahdollinen jousto tulevaisuudessa: Helppo lisätä uusia optioita esim. &crs=EPSG:4326&threshold=1000&hakutyyppi=piste,
    ;; eikä hakuparametrien järjestyksellä ole väliä.
    ;; HUOM: Tämä polku täytyy määritellä järjestyksessä ennen /haku/:y-tunnus polkua (alla), jotta kyselyä ei ohjata väärälle käsittelijälle.
    :api-oikeus :luku
    :polku "/api/urakat/haku/sijainnilla"
    :vastaus-skeema json-skeemat/urakoiden-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja-id db]
                    (hae-urakka-sijainnilla db
                      (apurit/muuta-mapin-avaimet-keywordeiksi parametrit)
                      kayttaja-id))}
   ;; TODO: Urakoiden hakua voisi yhtenäistää tulevaisuudessa.
   ;;      Olisiko y-tunnuksella haku oikeastaan /api/urakat/haku parametri &ytunnus=...?
   {:palvelu :hae-urakka-ytunnuksella
    :api-oikeus :luku
    :polku "/api/urakat/haku/:ytunnus"
    :vastaus-skeema json-skeemat/urakoiden-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja-id db]
                    (hae-urakka-ytunnuksella db parametrit kayttaja-id))}])

(defrecord Urakat []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku vastaus-skeema kasittely-fn api-oikeus]} hakutyypit]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request nil vastaus-skeema kasittely-fn api-oikeus))))
    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu hakutyypit))
    this))
