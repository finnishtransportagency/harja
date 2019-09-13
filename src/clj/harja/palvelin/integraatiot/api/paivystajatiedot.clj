(ns harja.palvelin.integraatiot.api.paivystajatiedot
  "Päivystäjätietojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.fmt :as fmt]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.sanomat.paivystajatiedot :as paivystajatiedot-sanoma]
            [harja.palvelin.integraatiot.api.tyokalut.apurit :as apurit]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.domain.puhelinnumero :as puhelinnumero]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn- parsi-paivamaara [paivamaara]
  (when paivamaara
    (try
      (konv/sql-timestamp (pvm-string->java-sql-date paivamaara))
      (catch Exception e
        (log/error e (format "Poikkeus parsittaessa päivämäärää: %s." paivamaara))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi virheet/+virheellinen-paivamaara+
           :viesti (format "Päivämäärää: %s ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD." paivamaara)})))))

(defn- parsi-aikaparametrit
  "Tarkistaa, että alku ja loppu on annettu ja ettei loppu ole ennen alkua.
   Jos alkua ja loppua ei ole annettu, palauttaa aikavälin nykyhetkestä pitkälle tulevaisuuteen."
  [alkaen paattyen]
  (when (and alkaen (not paattyen))
    (virheet/heita-puutteelliset-parametrit-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Päivämäärävälillä ei voi hakea ilman loppupäivämäärää")}))

  (when (and paattyen (not alkaen))
    (virheet/heita-puutteelliset-parametrit-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Päivämäärävälillä ei voi hakea ilman alkupäivämäärää")}))

  (if (and (not alkaen) (not paattyen))
    [(pvm/nyt) (c/to-date (t/plus (t/now) (t/years 500)))]

    (let [alkaen (parsi-paivamaara alkaen)
          paattyen (parsi-paivamaara paattyen)]
      (if (and alkaen paattyen (.after alkaen paattyen))
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi virheet/+virheellinen-paivamaara+
           :viesti (format "Alkupäivämäärä: %s on päättymispäivämäärän: %s jälkeen." alkaen paattyen)})
        [alkaen paattyen]))))

(defn- suodata-puhelinnumerolla [puhelinnumero paivystajatiedot]
  (into [] (filter (fn [paivystys]
                     (or (= (fmt/trimmaa-puhelinnumero (:tyopuhelin paivystys))
                            (fmt/trimmaa-puhelinnumero puhelinnumero))
                         (= (fmt/trimmaa-puhelinnumero (:matkapuhelin paivystys))
                            (fmt/trimmaa-puhelinnumero puhelinnumero))))
                   paivystajatiedot)))

(defn- tarkista-sijaintihaun-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:x "Koordinaatti X puuttuu"
     :y "Koordinaatti Y puuttuu"
     :urakkatyyppi "Urakkatyyppi puuttuu"})
  (when (not (some #(= % (:urakkatyyppi parametrit))
                   ["hoito" "paallystys" "paikkaus" "tiemerkinta" "valaistus" "siltakorjaus" "tekniset-laitteet"]))

    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Tuntematon urakkatyyppi: %s" (:urakkatyyppi parametrit))})))

(defn- paivita-tai-luo-uusi-paivystys [db urakka-id {:keys [alku loppu varahenkilo vastuuhenkilo id]} paivystaja-id kirjaaja]
  (if (yhteyshenkilot-q/onko-olemassa-paivystys-ulkoisella-idlla? db id (:id kirjaaja))
    (do
      (log/debug "Päivitetään päivystys.")
      (yhteyshenkilot-q/paivita-paivystys-ulkoisella-idlla<!
        db
        (aika-string->java-sql-date alku)
        (aika-string->java-sql-date loppu)
        varahenkilo
        vastuuhenkilo
        paivystaja-id
        id
        (:id kirjaaja)))
    (do
      (log/debug "Luodaan uusi päivystys.")
      (yhteyshenkilot-q/luo-paivystys<!
        db
        (aika-string->java-sql-date alku)
        (aika-string->java-sql-date loppu)
        urakka-id
        paivystaja-id
        varahenkilo
        vastuuhenkilo
        id
        (:id kirjaaja)))))

(defn- paivita-tai-luo-uusi-paivystaja [db {:keys [id etunimi sukunimi email matkapuhelin tyopuhelin liviTunnus]} urakoitsija-id]
  (let [matkapuhelin (puhelinnumero/kanonisoi matkapuhelin)
        tyopuhelin (puhelinnumero/kanonisoi tyopuhelin)]
    (if (yhteyshenkilot-q/onko-olemassa-yhteyshenkilo-ulkoisella-idlla? db (str id))
      (do
        (log/debug "Päivitetään päivystäjän tiedot ulkoisella id:llä " id)
        (:id (yhteyshenkilot-q/paivita-yhteyshenkilo-ulkoisella-idlla<! db etunimi sukunimi tyopuhelin matkapuhelin email
                                                                        (str id))))
      (do
        (log/debug "Päivystäjää ei löytynyt ulkoisella id:llä. Lisätään uusi päivystäjä")
        (:id (yhteyshenkilot-q/luo-yhteyshenkilo<! db etunimi sukunimi tyopuhelin matkapuhelin email
                                                   urakoitsija-id nil liviTunnus (str id)))))))

(defn- tallenna-paivystajatiedot [db urakka-id kirjaaja data]
  (log/debug "Aloitetaan päivystäjätietojen kirjaus")
  (jdbc/with-db-transaction [db db]
    (let [urakoitsija (:urakoitsija (first (urakat-q/hae-urakan-urakoitsija db urakka-id)))]
      (doseq [paivystys (:paivystykset data)]
        (let [paivystaja-id (paivita-tai-luo-uusi-paivystaja db (get-in paivystys [:paivystys :paivystaja]) urakoitsija)]
          (paivita-tai-luo-uusi-paivystys db urakka-id (:paivystys paivystys) paivystaja-id kirjaaja))))))

(defn- kirjaa-paivystajatiedot
  "Kirjaus toimii tällä hetkellä niin, että ulkoisen id:n omaavat päivystäjät päivitetään ja
  ilman ulkoista id:tä olevat lisätään Harjaan. Harjan käyttöliittymästä lisätyillä päivystäjillä
  ei ole ulkoista id:tä, joten ne ovat Harjan itse ylläpitämiä."
  [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan päivystäjätiedot urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-paivystajatiedot db urakka-id kirjaaja data)
    (paivystajatiedot-sanoma/tee-onnistunut-kirjaus-vastaus)))

(defn- hae-paivystajatiedot-urakan-idlla [db urakka-id kayttaja pvm-vali]
  (log/debug (format "Haetaan päivystäjätiedot urakan id:llä: %s, alkaen: %s, päättyen: %s." urakka-id
                     (pr-str (first pvm-vali)) (pr-str (second pvm-vali))))
  (validointi/tarkista-urakka db urakka-id)
  (validointi/tarkista-oikeudet-urakan-paivystajatietoihin db urakka-id kayttaja)
  (let [paivystajatiedot (map (fn [paivystajatiedot]
                                (if (= (:urakka_tyyppi paivystajatiedot) "teiden-hoito")
                                  (assoc paivystajatiedot :urakka_tyyppi "hoito")
                                  paivystajatiedot))
                              (yhteyshenkilot-q/hae-urakan-paivystajat db urakka-id (first pvm-vali) (second pvm-vali)))
        vastaus (paivystajatiedot-sanoma/muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot)]
    vastaus))

(defn- hae-paivystajatiedot-puhelinnumerolla [db parametrit kayttaja]
  (log/debug "Haetaan päivystäjätiedot puhelinnumerolla parametreillä: " parametrit)
  (parametrivalidointi/tarkista-parametrit parametrit {:puhelinnumero "Puhelinnumero puuttuu"})
  (validointi/tarkista-rooli kayttaja roolit/liikennepaivystaja)
  (let [{puhelinnumero :puhelinnumero alkaen :alkaen paattyen :paattyen} parametrit
        pvm-vali (parsi-aikaparametrit alkaen paattyen)
        kaikki-paivystajatiedot (yhteyshenkilot-q/hae-kaikki-paivystajat db (first pvm-vali) (second pvm-vali))
        paivystajatiedot-puhelinnumerolla (suodata-puhelinnumerolla puhelinnumero kaikki-paivystajatiedot)
        vastaus (paivystajatiedot-sanoma/muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot-puhelinnumerolla)]
    vastaus))

(defn- poista-paivystykset [db data parametrit kayttaja]
  (let [{:keys [paivystysidt]} data
        urakka-id (Integer/parseInt (:id parametrit))]
    (validointi/tarkista-urakka db urakka-id)
    (validointi/tarkista-oikeudet-urakan-paivystajatietoihin db urakka-id kayttaja)
    (log/debug "Poistettavat ulkoiset idt: " (pr-str paivystysidt) " urakka " (:id parametrit))
    (yhteyshenkilot-q/poista-urakan-paivystykset! db urakka-id (:id kayttaja) paivystysidt)
    (paivystajatiedot-sanoma/tee-onnistunut-poisto-vastaus)))

(defn- hae-paivystajatiedot-sijainnilla [db parametrit kayttaja]
  (log/debug "Haetaan päivystäjätiedot sijainnilla parametreillä: " parametrit)
  (tarkista-sijaintihaun-parametrit parametrit)
  (let [{urakkatyyppi :urakkatyyppi alkaen :alkaen paattyen :paattyen x :x y :y} parametrit
        x (Double/parseDouble x)
        y (Double/parseDouble y)
        pvm-vali (parsi-aikaparametrit alkaen paattyen)
        urakka-idt (urakat/hae-urakka-idt-sijainnilla db urakkatyyppi {:x x :y y})]
    (if (empty? urakka-idt)
      (virheet/heita-ei-hakutuloksia-apikutsulle-poikkeus
        {:koodi virheet/+urakkaa-ei-loydy+
         :viesti "Annetulla sijainnilla ei löydy aktiivista urakkaa."})
      (do
        (log/debug "Sijainnilla löytyi urakka id: " (pr-str urakka-idt))
        (reduce (partial merge-with concat)
                (map #(hae-paivystajatiedot-urakan-idlla db % kayttaja pvm-vali) urakka-idt))))))

(def palvelut
  [{:palvelu :hae-paivystajatiedot-urakka-idlla
    :polku "/api/urakat/:id/paivystajatiedot"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/paivystajatietojen-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja-id db]
                    (let [urakka-id (Integer/parseInt (:id parametrit))
                          pvm-vali (parsi-aikaparametrit (get parametrit "alkaen")
                                                         (get parametrit "paattyen"))]
                      (hae-paivystajatiedot-urakan-idlla db urakka-id kayttaja-id pvm-vali)))}
   {:palvelu :hae-paivystajatiedot-sijainnilla
    :polku "/api/paivystajatiedot/haku/sijainnilla"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/paivystajatietojen-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja-id db]
                    (hae-paivystajatiedot-sijainnilla db
                                                      (apurit/muuta-mapin-avaimet-keywordeiksi parametrit)
                                                      kayttaja-id))}
   {:palvelu :hae-paivystajatiedot-puhelinnumerolla
    :polku "/api/paivystajatiedot/haku/puhelinnumerolla"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/paivystajatietojen-haku-vastaus
    :kasittely-fn (fn [parametrit _ kayttaja-id db]
                    (hae-paivystajatiedot-puhelinnumerolla db
                                                           (apurit/muuta-mapin-avaimet-keywordeiksi parametrit)
                                                           kayttaja-id))}
   {:palvelu :lisaa-paivystajatiedot
    :polku "/api/urakat/:id/paivystajatiedot"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paivystajatietojen-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-paivystajatiedot db parametrit data kayttaja))}
   {:palvelu :poista-paivystajatiedot
    :polku "/api/urakat/:id/paivystajatiedot"
    :tyyppi :DELETE
    :kutsu-skeema json-skeemat/paivystyksen-poisto
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja-id db]
                    (poista-paivystykset db data parametrit kayttaja-id))}])

(defrecord Paivystajatiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)

    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))
