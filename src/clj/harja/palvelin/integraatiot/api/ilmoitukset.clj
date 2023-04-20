(ns harja.palvelin.integraatiot.api.ilmoitukset
  "Tieliikennelmoitusten haku ja ilmoitustoimenpiteiden kirjaus"
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [with-channel on-close send!]]
            [clojure.spec.alpha :as s]
            [clj-time.coerce :as c]
            [compojure.core :refer [PUT GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer
             [kasittele-kutsu kasittele-kevyesti-get-kutsu lokita-kutsu lokita-vastaus tee-vastaus aja-virhekasittelyn-kanssa hae-kayttaja tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date sql-timestamp-str->utc-timestr]]
            [harja.kyselyt.tieliikenneilmoitukset :as tieliikenneilmoitukset-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.sanomat.ilmoitus-sanomat :as sanomat]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
            [harja.pvm :as pvm])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-ilmoituksen-id [db ilmoitusid]
  (if-let [id (:id (first (tieliikenneilmoitukset-kyselyt/hae-id-ilmoitus-idlla db ilmoitusid)))]
    id
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi :tuntematon-ilmoitus
       :viesti (format "Ilmoitus id:llä %s. ei löydy ilmoitusta." ilmoitusid)})))

(defn tee-onnistunut-ilmoitustoimenpidevastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Ilmoitustoimenpide kirjattu onnistuneesti"}))

(defn luo-ilmoitustoimenpide
  [db id ilmoitusid ilmoitustoimenpide ilmoittaja kasittelija kuittaustyyppi aiheutti-toimenpiteita vapaateksti suunta kanava]

  (when (not (nil? aiheutti-toimenpiteita))
    (tieliikenneilmoitukset-kyselyt/ilmoitus-aiheutti-toimenpiteita! db aiheutti-toimenpiteita id))

  (:id (tieliikenneilmoitukset-kyselyt/luo-ilmoitustoimenpide<!
         db
         {:ilmoitus                         id
          :ilmoitusid                       ilmoitusid
          :kuitattu                         (aika-string->java-sql-date (:aika ilmoitustoimenpide))
          :vakiofraasi                      nil
          :vapaateksti                      vapaateksti
          :kuittaustyyppi                   kuittaustyyppi
          :tila (when (= "valitys" kuittaustyyppi) "lahetetty")
          :suunta                           suunta
          :kanava                           kanava
          :kuittaaja_henkilo_etunimi        (get-in ilmoittaja [:henkilo :etunimi])
          :kuittaaja_henkilo_sukunimi       (get-in ilmoittaja [:henkilo :sukunimi])
          :kuittaaja_henkilo_matkapuhelin   (get-in ilmoittaja [:henkilo :matkapuhelin])
          :kuittaaja_henkilo_tyopuhelin     (get-in ilmoittaja [:henkilo :tyopuhelin])
          :kuittaaja_henkilo_sahkoposti     (get-in ilmoittaja [:henkilo :sahkoposti])
          :kuittaaja_organisaatio_nimi      (get-in ilmoittaja [:organisaatio :nimi])
          :kuittaaja_organisaatio_ytunnus   (get-in ilmoittaja [:organisaatio :ytunnus])
          :kasittelija_henkilo_etunimi      (get-in kasittelija [:henkilo :etunimi])
          :kasittelija_henkilo_sukunimi     (get-in kasittelija [:henkilo :sukunimi])
          :kasittelija_henkilo_matkapuhelin (get-in kasittelija [:henkilo :matkapuhelin])
          :kasittelija_henkilo_tyopuhelin   (get-in kasittelija [:henkilo :tyopuhelin])
          :kasittelija_henkilo_sahkoposti   (get-in kasittelija [:henkilo :sahkoposti])
          :kasittelija_organisaatio_nimi    (get-in kasittelija [:organisaatio :nimi])
          :kasittelija_organisaatio_ytunnus (get-in kasittelija [:organisaatio :ytunnus])})))

(defn kirjaa-ilmoitustoimenpide [db tloik parametrit
                                 {{:keys [tyyppi
                                          vapaateksti
                                          ilmoittaja
                                          kasittelija
                                          aiheutti-toimenpiteita]
                                   :as ilmoitustoimenpide}
                                  :ilmoitustoimenpide}]
  (let [ilmoitusid (Integer/parseInt (:id parametrit))
        id (hae-ilmoituksen-id db ilmoitusid)
        _ (log/debug (format "Kirjataan toimenpide ilmoitukselle, jonka id on: %s ja ilmoitusid on: %s" id ilmoitusid))]

    (when (and (= tyyppi "aloitus") (not (tieliikenneilmoitukset-kyselyt/ilmoitukselle-olemassa-vastaanottokuittaus? db ilmoitusid)))
      (let [aloitus-kuittaus-id (luo-ilmoitustoimenpide db id ilmoitusid ilmoitustoimenpide ilmoittaja kasittelija
                                                        "vastaanotto" false "Vastaanotettu" "sisaan"
                                                        "ulkoinen_jarjestelma")]
        (tloik/laheta-ilmoitustoimenpide tloik aloitus-kuittaus-id)))

    (let [kuittaus-id (luo-ilmoitustoimenpide db id ilmoitusid ilmoitustoimenpide ilmoittaja kasittelija tyyppi
                                              aiheutti-toimenpiteita vapaateksti "sisaan" "ulkoinen_jarjestelma")]
      (tloik/laheta-ilmoitustoimenpide tloik kuittaus-id))

    (tee-onnistunut-ilmoitustoimenpidevastaus)))

(defn ilmoituslahettaja [integraatioloki kanava tapahtuma-id sulje-lahetyksen-jalkeen?]
  (fn [ilmoitukset]
    (send!
     kanava
     (aja-virhekasittelyn-kanssa
      "laheta-ilmoitus"
      nil
      nil
      nil
      (fn []
        (let [data {:ilmoitukset (mapv (fn [ilmoitus]
                                         (sanomat/rakenna-ilmoitus
                                          (konversio/alaviiva->rakenne ilmoitus)))
                                       ilmoitukset)}
              vastaus (tee-vastaus json-skeemat/ilmoitusten-haku data)]
          (lokita-vastaus integraatioloki :hae-ilmoitukset vastaus tapahtuma-id)
          (doseq [ilmoitus ilmoitukset]
            (notifikaatiot/ilmoita-lahetetysta-ilmoituksesta (:ilmoitusid ilmoitus) :api))
          vastaus)))
     sulje-lahetyksen-jalkeen?)))

(defn pura-ilmoitusten-kuuntelun-kutsuparametrit [request]
  (let [{urakka-id :id
         muuttunut-jalkeen "muuttunutJalkeen"
         odota-uusia "odotaUusia"
         sulje-vastauksen-jalkeen "suljeVastauksenJalkeen"} (:params request)
        odota-uusia? (if odota-uusia
                       (Boolean/valueOf odota-uusia)
                       false)]
    {:urakka-id (when urakka-id (Integer/parseInt urakka-id))
     :muuttunut-jalkeen (when muuttunut-jalkeen (parametrit/pvm-aika muuttunut-jalkeen))
     :odota-uusia? odota-uusia?
     :sulje-vastauksen-jalkeen? (if sulje-vastauksen-jalkeen
                                  (and odota-uusia? (not (Boolean/valueOf sulje-vastauksen-jalkeen)))
                                  true)}))

(defn kaynnista-ilmoitusten-kuuntelu [db integraatioloki request]
  (let [parametrit (pura-ilmoitusten-kuuntelun-kutsuparametrit request)
        headerit (:headers request)]
    (aja-virhekasittelyn-kanssa
     "hae-ilmoitukset"
     parametrit
     headerit
     nil
     (fn []
       (let [{urakka-id :urakka-id
              muuttunut-jalkeen :muuttunut-jalkeen
              odota-uusia? :odota-uusia?
              sulje-vastauksen-jalkeen? :sulje-vastauksen-jalkeen?} parametrit
             tapahtuma-id (lokita-kutsu integraatioloki :hae-ilmoitukset request nil)
             kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))]
         (log/debug (format "Käynnistetään ilmoitusten kuuntelu urakalle id: %s. Muutosaika: %s." urakka-id muuttunut-jalkeen))
         (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
         (with-channel request kanava
                       (let [laheta-ilmoitukset (ilmoituslahettaja integraatioloki kanava tapahtuma-id sulje-vastauksen-jalkeen?)
                             odottavat-ilmoitukset (and muuttunut-jalkeen (tieliikenneilmoitukset-kyselyt/hae-muuttuneet-ilmoitukset db urakka-id muuttunut-jalkeen))]

                         ;; Jos löytyi vanhoja ilmoituksia tai ei pidä jäädä odottamaan uusia ilmoituksia, palautetaan response välittömästi
                         (when (or (not odota-uusia?) (not (empty? odottavat-ilmoitukset)))
                           (laheta-ilmoitukset odottavat-ilmoitukset))

                         (when odota-uusia?
                           (let [kuuntelun-lopetus-fn (notifikaatiot/kuuntele-urakan-ilmoituksia
                                                        urakka-id
                                                        (fn [{ilmoitus-id :payload :as foo}]
                                                          (laheta-ilmoitukset (tieliikenneilmoitukset-kyselyt/hae-ilmoitukset-ilmoitusidlla db [ilmoitus-id]))))]
                             (on-close kanava
                                       (fn [_]
                                         (log/debug (format "Suljetaan urakan id: %s ilmoitusten kuuntelu." urakka-id))
                                         (when kuuntelun-lopetus-fn
                                           (kuuntelun-lopetus-fn)))))))))))))

;; Sallitaan ajalle kaksi eri formaattia
(def pvm-aika-muoto1 "yyyy-MM-dd'T'HH:mm:ssX")
(def pvm-aika-muoto2 "yyyy-MM-dd'T'HH:mm:ss.SSSX")
(defn valid-aikamuoto? [string]
  (try
    (if (< (count string) 25)
      (inst? (.parse (SimpleDateFormat. pvm-aika-muoto1) string))
      (inst? (.parse (SimpleDateFormat. pvm-aika-muoto2) string)))
    (catch Exception e
      false)))
(s/def ::alkuaika #(and (string? %) (> (count %) 20) (valid-aikamuoto? %)))
(s/def ::loppuaika #(and (string? %) (> (count %) 20) (valid-aikamuoto? %)))

(defn- tarkista-ilmoitus-haun-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:ytunnus "Y-tunnus puuttuu"
     :alkuaika "Alkuaika puuttuu"})
  (when (not (s/valid? ::alkuaika (:alkuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Alkuaika väärässä muodossa: %s
       Anna muodossa: yyyy-MM-dd'T'HH:mm:ssX tai yyyy-MM-dd'T'HH:mm:ss.SSSX tai yyyy-MM-dd'T'HH:mm:ss.SSSZ
       esim: 2005-01-01T03:00:00+03 tai 2005-01-01T03:00:00.123+03 tai 2005-01-01T00:00:00.123Z" (:alkuaika parametrit))}))
  (when (and (not (nil? (:loppuaika parametrit))) (not (s/valid? ::loppuaika (:loppuaika parametrit))))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Loppuaika väärässä muodossa: %s
       Anna muodossa: yyyy-MM-dd'T'HH:mm:ssX tai yyyy-MM-dd'T'HH:mm:ss.SSSX tai yyyy-MM-dd'T'HH:mm:ss.SSSZ
       esim: 2005-01-02T03:00:00+03 tai 2005-01-01T03:00:00.123+03 tai 2005-01-01T00:00:00.123Z" (:loppuaika parametrit))})))

(def db-kuittaus->avaimet
  {:f1 :kuitattu
   :f2 :kuittaustyyppi
   :f3 :vakiofraasi
   :f4 :vapaateksti
   :f5 :kuittaaja_henkilo_etunimi
   :f6 :kuittaaja_henkilo_sukunimi,
   :f7 :kuittaaja_organisaatio_nimi,
   :f8 :kuittaaja_organisaatio_ytunnus
   :f9 :kanava})

(defn hae-ilmoitukset-ytunnuksella
  "Haetaan ilmoitukset y-tunnuksella ja valitetty-harjaan ajan perusteella. Lisätään alueurakkanumero, jotta urakka
  on mahdollista eritellä."
  [db {:keys [ytunnus alkuaika loppuaika] :as parametrit} kayttaja]
  (log/info "Hae ilmoitukset ytunnuksella :: parametrit:" parametrit)
  (tarkista-ilmoitus-haun-parametrit parametrit)
  (validointi/tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja)
  (let [;; Ilmoitukset "valitettu-urakkaan" Timestamp tallennetaan UTC ajassa. Muokataan siitä syystä myös loppuaika ja alkuaika utc aikaan
        alkuaika (pvm/utc-str-aika->sql-timestamp alkuaika)
        loppuaika (if loppuaika
                    (pvm/utc-str-aika->sql-timestamp loppuaika)
                    (c/to-sql-time (pvm/ajan-muokkaus (pvm/joda-timeksi (pvm/nyt)) true 1 :tunti)))
        ilmoitukset (tieliikenneilmoitukset-kyselyt/hae-ilmoitukset-ytunnuksella
                      db
                      {:ytunnus ytunnus
                       :alkuaika alkuaika
                       :loppuaika loppuaika})
        ilmoitukset
        (->> ilmoitukset
          (map #(update % :kuittaukset konversio/jsonb->clojuremap))
          (map #(update % :kuittaukset
                   (fn [rivit]
                     (let [tulos (keep
                                   (fn [r]
                                     ;; Haussa käytetään left joinia, joten on mahdollista, että löytyy nil id
                                     (when (not (nil? (:f1 r)))
                                       (let [r (-> r (clojure.set/rename-keys db-kuittaus->avaimet))
                                             r (assoc r :kuitattu (sql-timestamp-str->utc-timestr (:kuitattu r)))]
                                         r)))
                                   rivit)]
                       tulos)))))
        vastaus {:ilmoitukset
                 (map (fn [ilmoitus]
                         (sanomat/rakenna-ilmoitus
                           (konversio/alaviiva->rakenne ilmoitus)))
                   ilmoitukset)}]
    vastaus))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tloik :tloik :as this}]
    (julkaise-reitti
      http :hae-ilmoitukset
      (GET "/api/urakat/:id/ilmoitukset" request
        (kaynnista-ilmoitusten-kuuntelu db integraatioloki request)))

    ;; Loppuaika ei ole pakollinen parametri, joten tehdään kaksi end pointtia saman asian käsittelyyn
    (julkaise-reitti
      http :hae-ilmoitukset-ytunnuksella
      (GET "/api/ilmoitukset/:ytunnus/:alkuaika/:loppuaika" request
        (kasittele-kevyesti-get-kutsu db integraatioloki :hae-ilmoitukset-ytunnuksella request
          (fn [parametrit kayttaja db]
            (hae-ilmoitukset-ytunnuksella db parametrit kayttaja))
          false)))
    (julkaise-reitti
      http :hae-ilmoitukset-ytunnuksella
      (GET "/api/ilmoitukset/:ytunnus/:alkuaika" request
        (kasittele-kevyesti-get-kutsu db integraatioloki :hae-ilmoitukset-ytunnuksella request
          (fn [parametrit kayttaja db]
            (hae-ilmoitukset-ytunnuksella db parametrit kayttaja))
          false)))


    ;; Tässä rajapinnassa on virheellisesti kauttaviiva. Rajapinta duplikoitu ilman kauttaviivaa,
    ;; ja tämä jätetty varmuuden vuoksi tähän.
    ;; TODO: Jos näet tämän kesäkuun 2023 jälkeen, tarkista graylokista, näkyykö alla olevaa varoitusta. Jos ei, tämä voidaan poistaa
    (julkaise-reitti
      http :kirjaa-ilmoitustoimenpide
      (PUT "/api/ilmoitukset/:id/" request
           (kasittele-kutsu db integraatioloki :kirjaa-ilmoitustoimenpide request json-skeemat/ilmoitustoimenpiteen-kirjaaminen json-skeemat/kirjausvastaus
                         (fn [parametrit data _ db]
                           (log/warn ":kirjaa-ilmoitustoimenpide kutsuttu kauttaviivalla osoitteen lopussa!")
                           (kirjaa-ilmoitustoimenpide db tloik parametrit data)))))

    (julkaise-reitti
      http :kirjaa-ilmoitustoimenpide
      (PUT "/api/ilmoitukset/:id" request
        (kasittele-kutsu db integraatioloki :kirjaa-ilmoitustoimenpide request json-skeemat/ilmoitustoimenpiteen-kirjaaminen json-skeemat/kirjausvastaus
          (fn [parametrit data _ db] (kirjaa-ilmoitustoimenpide db tloik parametrit data)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-ilmoitukset)
    (poista-palvelut http :kirjaa-ilmoitustoimenpide)
    (poista-palvelut http :hae-ilmoitukset-ytunnuksella)
    this))
