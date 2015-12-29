(ns harja.palvelin.integraatiot.api.paivystajatiedot
  "Päivystäjätietojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.fmt :as fmt]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.sanomat.paivystajatiedot :as paivystajatiedot-sanoma]
            [harja.palvelin.integraatiot.api.tyokalut.apurit :as apurit]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn parsi-paivamaara [paivamaara]
  (try
    (pvm-string->java-sql-date paivamaara)
    (catch Exception e
      (throw+
        {:type    virheet/+viallinen-kutsu+
         :virheet [{:koodi  virheet/+virheellinen-paivamaara+
                    :viesti (format "Päivämäärää: %s ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD." paivamaara)}]}))))

(defn tarkista-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:x            "Koordinaatti X puuttuu"
     :y            "Koordinaatti Y puuttuu"
     :urakkatyyppi "Urakkatyyppi puuttuu"})
  (when (not (some #(= % (:urakkatyyppi parametrit))
                   ["hoito" "paallystys" "paikkaus" "tiemerkinta" "valaistus" "siltakorjaus"]))

    (throw+ {:type    virheet/+viallinen-kutsu+
             :virheet [{:koodi  virheet/+puutteelliset-parametrit+
                        :viesti (format "Tuntematon urakkatyyppi: %s" (:urakkatyyppi parametrit))}]})))

(defn paivita-tai-luo-uusi-paivystys [db urakka-id {:keys [alku loppu varahenkilo vastuuhenkilo]} paivystaja-id]
  (if (yhteyshenkilot/onko-olemassa-paivystys-jossa-yhteyshenkilona-id? db paivystaja-id)
    (do
      (log/debug "Päivitetään päivystäjään liittyvän päivystyksen tiedot.")
      (yhteyshenkilot/paivita-paivystys-yhteyshenkilon-idlla<! db
                                                               (aika-string->java-sql-date alku)
                                                               (aika-string->java-sql-date loppu)
                                                               varahenkilo
                                                               vastuuhenkilo
                                                               paivystaja-id))
    (do
      (log/debug "Päivystäjällä ei ole päivystystä. Luodaan uusi päivystys.")
      (yhteyshenkilot/luo-paivystys<! db
                                      (aika-string->java-sql-date alku)
                                      (aika-string->java-sql-date loppu)
                                      urakka-id
                                      paivystaja-id
                                      varahenkilo
                                      vastuuhenkilo))))

(defn paivita-tai-luo-uusi-paivystaja [db {:keys [id etunimi sukunimi email matkapuhelin tyopuhelin liviTunnus]}]
  (if (yhteyshenkilot/onko-olemassa-yhteyshenkilo-ulkoisella-idlla? db (str id))
    (do
      (log/debug "Päivitetään päivystäjän tiedot ulkoisella id:llä " id)
      (:id (yhteyshenkilot/paivita-yhteyshenkilo-ulkoisella-idlla<! db etunimi sukunimi tyopuhelin matkapuhelin email nil (str id))))
    (do
      (log/debug "Päivystäjää ei löytynyt ulkoisella id:llä. Lisätään uusi päivystäjä")
      (:id (yhteyshenkilot/luo-yhteyshenkilo<! db etunimi sukunimi tyopuhelin matkapuhelin email nil nil liviTunnus (str id))))))

(defn tallenna-paivystajatiedot [db urakka-id data]
  (log/debug "Aloitetaan päivystäjätietojen kirjaus")
  (jdbc/with-db-transaction [transaktio db]
    (doseq [paivystys (:paivystykset data)]
      (let [paivystaja-id (paivita-tai-luo-uusi-paivystaja db (get-in paivystys [:paivystys :paivystaja]))]
        (paivita-tai-luo-uusi-paivystys db urakka-id (:paivystys paivystys) paivystaja-id)))))

(defn kirjaa-paivystajatiedot
  "Kirjaus toimii tällä hetkellä niin, että ulkoisen id:n omaavat päivystäjät päivitetään ja
  ilman ulkoista id:tä olevat lisätään Harjaan. Harjan käyttöliittymästä lisätyillä päivystäjillä
  ei ole ulkoista id:tä, joten ne ovat Harjan itse ylläpitämiä."
  [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan päivystäjätiedot urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-paivystajatiedot db urakka-id data)
    (paivystajatiedot-sanoma/tee-onnistunut-kirjaus-vastaus)))

(defn hae-paivystajatiedot-urakan-idlla [db urakka-id kayttaja alkaen paattyen]
  (log/debug "Haetaan päivystäjätiedot urakan id:llä: " urakka-id " alkaen " (pr-str alkaen) " päättyen " (pr-str paattyen))
  (validointi/tarkista-urakka db urakka-id)
  (validointi/tarkista-oikeudet-urakan-paivystajatietoihin db urakka-id kayttaja)
  (let [paivystajatiedot (yhteyshenkilot/hae-urakan-paivystajat db
                                                                urakka-id
                                                                (konv/sql-timestamp alkaen)
                                                                (konv/sql-timestamp paattyen))
        vastaus (paivystajatiedot-sanoma/muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot)]
    vastaus))

(defn hae-paivystajatiedot-puhelinnumerolla [db parametrit kayttaja]
  (log/debug "Haetaan päivystäjätiedot puhelinnumerolla parametreillä: " parametrit)
  (parametrivalidointi/tarkista-parametrit parametrit {:puhelinnumero "Puhelinnumero puuttuu"})
  (validointi/tarkista-rooli kayttaja roolit/liikennepaivystaja)
  (let [{puhelinnumero :puhelinnumero alkaen :alkaen paattyen :paattyen} parametrit
        alkaen (parsi-paivamaara alkaen)
        paattyen (parsi-paivamaara paattyen)
        kaikki-paivystajatiedot (yhteyshenkilot/hae-kaikki-paivystajat db
                                                                       (konv/sql-timestamp alkaen)
                                                                       (konv/sql-timestamp paattyen))
        paivystajatiedot-puhelinnumerolla (into [] (filter (fn [paivystys]
                                                             ; Ei voida helposti filtteröidä kantatasolla, koska puhelinnumeron
                                                             ; kirjoitusasu voi vaihdella.
                                                             (or (= (fmt/trimmaa-puhelinnumero (:tyopuhelin paivystys))
                                                                    (fmt/trimmaa-puhelinnumero puhelinnumero))
                                                                 (= (fmt/trimmaa-puhelinnumero (:matkapuhelin paivystys))
                                                                    (fmt/trimmaa-puhelinnumero puhelinnumero))))
                                                           kaikki-paivystajatiedot))
        vastaus (paivystajatiedot-sanoma/muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot-puhelinnumerolla)]
    vastaus))

(defn hae-paivystajatiedot-sijainnilla [db parametrit kayttaja]
  (log/debug "Haetaan päivystäjätiedot sijainnilla parametreillä: " parametrit)
  (tarkista-parametrit parametrit)
  (let [{urakkatyyppi :urakkatyyppi alkaen :alkaen paattyen :paattyen x :x y :y} parametrit
        x (Double/parseDouble x)
        y (Double/parseDouble y)
        alkaen (parsi-paivamaara alkaen)
        paattyen (parsi-paivamaara paattyen)
        urakka-idt (urakat/hae-urakka-idt-sijainnilla db urakkatyyppi {:x x :y y})]
    (if-not (empty? urakka-idt)
      (do
        (log/debug "Sijainnilla löytyi urakka id: " (pr-str urakka-idt))
        (reduce (partial merge-with concat)
                (map #(hae-paivystajatiedot-urakan-idlla db % kayttaja alkaen paattyen) urakka-idt)))
      (throw+ {:type    virheet/+viallinen-kutsu+
               :virheet [{:koodi  virheet/+virheellinen-sijainti+
                          :viesti "Annetulla sijainnilla ei löydy aktiivista urakkaa."}]}))))

(def palvelutyypit
  [{:palvelu        :hae-paivystajatiedot-urakka-idlla
    :polku          "/api/urakat/:id/paivystajatiedot"
    :tyyppi         :GET
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (let [urakka-id (Integer/parseInt (:id parametrit))]
                        (hae-paivystajatiedot-urakan-idlla db urakka-id kayttaja-id nil nil)))}
   {:palvelu        :hae-paivystajatiedot-sijainnilla
    :polku          "/api/paivystajatiedot/haku/sijainnilla"
    :tyyppi         :GET
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-paivystajatiedot-sijainnilla db (apurit/muuta-mapin-avaimet-keywordeiksi parametrit) kayttaja-id))}
   {:palvelu        :hae-paivystajatiedot-puhelinnumerolla
    :polku          "/api/paivystajatiedot/haku/puhelinnumerolla"
    :tyyppi         :GET
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-paivystajatiedot-puhelinnumerolla db (apurit/muuta-mapin-avaimet-keywordeiksi parametrit) kayttaja-id))}
   {:palvelu        :lisaa-paivystajatiedot
    :polku          "/api/urakat/:id/paivystajatiedot"
    :tyyppi         :POST
    :kutsu-skeema   json-skeemat/+paivystajatietojen-kirjaus+
    :vastaus-skeema json-skeemat/+kirjausvastaus+
    :kasittely-fn   (fn [parametrit data kayttaja db]
                      (kirjaa-paivystajatiedot db parametrit data kayttaja))}])

(defrecord Paivystajatiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelutyypit :when (= tyyppi :GET)]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

    (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelutyypit :when (= tyyppi :POST)]
      (julkaise-reitti
        http palvelu
        (POST polku request
          (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-paivystajatiedot)
    this))
