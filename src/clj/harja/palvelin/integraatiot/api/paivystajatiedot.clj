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
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.fmt :as fmt]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.sanomat.paivystajatiedot :as paivystajatiedot-sanoma]
            [harja.utils :as utils]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn paivita-tai-luo-uusi-paivystys [db urakka-id {:keys [alku loppu varahenkilo vastuuhenkilo]} paivystaja-id]
  (if (yhteyshenkilot/onko-olemassa-paivystys-jossa-yhteyshenkilona-id? db paivystaja-id)
    (do
      (log/debug "Päivitetään päivystäjään liittyvän päivystyksen tiedot.")
      (yhteyshenkilot/paivita-paivystys-yhteyshenkilon-idlla<! db
                                                               (pvm-string->java-sql-date alku)
                                                               (pvm-string->java-sql-date loppu)
                                                               varahenkilo
                                                               vastuuhenkilo
                                                               paivystaja-id))
    (do
      (log/debug "Päivystäjällä ei ole päivystystä. Luodaan uusi päivystys.")
      (yhteyshenkilot/luo-paivystys<! db
                                      (pvm-string->java-sql-date alku)
                                      (pvm-string->java-sql-date loppu)
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
  (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
  (let [paivystajatiedot (yhteyshenkilot/hae-urakan-paivystajat db
                                                                urakka-id
                                                                (not (nil? alkaen))
                                                                (konv/sql-timestamp alkaen)
                                                                (not (nil? paattyen))
                                                                (konv/sql-timestamp paattyen))
        vastaus (paivystajatiedot-sanoma/muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot)]
    vastaus))

(defn hae-paivystajatiedot-puhelinnumerolla [db parametrit kayttaja]
  (log/debug "Haetaan päivystäjätiedot puhelinnumerolla parametreillä: " parametrit)
  (parametrivalidointi/tarkista-parametrit parametrit {:puhelinnumero "Puhelinnumero puuttuu"})
  (roolit/vaadi-rooli kayttaja roolit/liikennepaivystaja)
  (let [{puhelinnumero :puhelinnumero alkaen :alkaen paattyen :paattyen} parametrit
        alkaen (pvm-string->java-sql-date alkaen)
        paattyen (pvm-string->java-sql-date paattyen)
        kaikki-paivystajatiedot (yhteyshenkilot/hae-kaikki-paivystajat db
                                                                       (not (nil? alkaen))
                                                                       (konv/sql-timestamp alkaen)
                                                                       (not (nil? paattyen))
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
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:x            "Koordinaatti X puuttuu"
     :y            "Koordinaatti Y puuttuu"
     :urakkatyyppi "Urakkatyyppi puuttuu"})
  (let
    [{urakkatyyppi :urakkatyyppi alkaen :alkaen paattyen :paattyen x :x y :y} parametrit
     x (Double. x)
     y (Double. y)
     alkaen (pvm-string->java-sql-date alkaen)
     paattyen (pvm-string->java-sql-date paattyen)
     urakka-id (urakat/hae-urakka-id-sijainnilla db urakkatyyppi {:x x :y y})]
    (if urakka-id
      (do
        (log/debug "Sijainnilla löytyi urakka id: " (pr-str urakka-id))
        (hae-paivystajatiedot-urakan-idlla db urakka-id kayttaja alkaen paattyen))
      (throw+ {:type    virheet/+urakkaa-ei-loydy+
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
                      (hae-paivystajatiedot-sijainnilla db (utils/muuta-mapin-avaimet-keywordeiksi parametrit) kayttaja-id))}
   {:palvelu        :hae-paivystajatiedot-puhelinnumerolla
    :polku          "/api/paivystajatiedot/haku/puhelinnumerolla"
    :tyyppi         :GET
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-paivystajatiedot-puhelinnumerolla db (utils/muuta-mapin-avaimet-keywordeiksi parametrit) kayttaja-id))}
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
