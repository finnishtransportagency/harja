(ns harja.palvelin.palvelut.laadunseuranta
  "Laadunseuranta: Tarkastukset, Laatupoikkeamat ja Sanktiot

  Palvelu sisältää perus CRUD-operaatiot Laadunseurannan kokonaisuuksille: Tarkastuksille,
  Laatupoikkeamille, ja (suora)Sanktioille. Palvelimella piirrettävien asioiden karttakuvan piirtäminen
  ja tietojen hakeminen infopaneeliin löytyy täältä myös; kirjoittamisen hetkellä tarkastukset piirretään
  palvelimella.

  Vaikka kaikkia kolme laadunseurannan käsitettä tukevat luontia suoraan käyttöliittymästä,
  voivat ne olla hyvin tiivisti yhteydessä toisiinsa, joka näkyy näissä palveluissa.

  Tarkastuksen pohjalta voidaan luoda laatupoikkeama. Tällöin tarkastus ja laatupoikkeama ovat yhteydessä.

  Laatupoikkeamat, joille on annettu päätös, voivat sisältää 0-n sanktiota.

  Sanktioita voi luoda käyttöliittymästä myös suoraan, ilman laatupoikkeaman luontia - koodissa
  näitä kutsutaan suorasanktioiksi. On tärkeää huomata, että tietomallissa myös suorasanktioihin
  kuuluu laatupoikkeama. Näitä koneellisesti generoituja laatupoikkeamia ei kuitenkaan ole yleensä
  mielekästä näyttää käyttäjälle."

  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [go <! >! thread >!! timeout] :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat-q]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.sanktiot :as sanktiot]
            [harja.palvelin.palvelut.laadunseuranta.viestinta :as viestinta]
            [harja.palvelin.palvelut.laadunseuranta.yhteiset :as yhteiset]

            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.sanktiot :as sanktiot-domain]
            [harja.geo :as geo]

            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]

            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]
            [clojure.core.async :as async]))



(defn hae-urakan-laatupoikkeamat [db user {:keys [listaus urakka-id alku loppu]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [tietokannasta-nostetut
          ((case listaus
             :omat laatupoikkeamat-q/hae-omat-laatupoikkeamat
             :kaikki laatupoikkeamat-q/hae-kaikki-laatupoikkeamat
             :selvitys laatupoikkeamat-q/hae-selvitysta-odottavat-laatupoikkeamat
             :kasitellyt laatupoikkeamat-q/hae-kasitellyt-laatupoikkeamat)
            db
            {:urakka urakka-id
             :alku (konv/sql-timestamp alku)
             :loppu (konv/sql-timestamp loppu)
             :kayttaja (:id user)})
          uniikit (map (fn [[_ vektori]] (first vektori)) (group-by :id tietokannasta-nostetut))
          tulos (into [] yhteiset/laatupoikkeama-xf uniikit)]
      tulos)))

(defn hae-laatupoikkeaman-tiedot
  "Hakee yhden laatupoikkeaman kaiken tiedon muokkausnäkymää varten: laatupoikkeaman perustiedot, kommentit ja liitteet, päätös ja sanktiot.
   Ottaa urakka-id:n ja laatupoikkeama-id:n. Urakka id:tä käytetään oikeustarkistukseen, laatupoikkeaman tulee olla annetun urakan
   toimenpiteeseen kytketty."
  [db user urakka-id laatupoikkeama-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (let [laatupoikkeama (first (into []
                                    yhteiset/laatupoikkeama-xf
                                    (laatupoikkeamat-q/hae-laatupoikkeaman-tiedot db urakka-id laatupoikkeama-id)))]
    (when laatupoikkeama
      (assoc laatupoikkeama
        :kommentit (into []
                         (comp (map konv/alaviiva->rakenne)
                               (map #(assoc % :tekija (name (:tekija %))))
                               (map (fn [{:keys [liite] :as kommentti}]
                                      (if (:id liite)
                                        kommentti
                                        (dissoc kommentti :liite)))))
                         (laatupoikkeamat-q/hae-laatupoikkeaman-kommentit db laatupoikkeama-id))
        :sanktiot (into []
                        (comp (map #(konv/array->set % :tyyppi_laji keyword))
                              (map konv/alaviiva->rakenne)
                              (map #(konv/string->keyword % :laji :vakiofraasi))
                              (map #(assoc %
                                      :sakko? (sanktiot-domain/sakko? %)
                                      :summa (some-> % :summa double))))
                        (sanktiot/hae-laatupoikkeaman-sanktiot db laatupoikkeama-id))
        :liitteet (into [] (laatupoikkeamat-q/hae-laatupoikkeaman-liitteet db laatupoikkeama-id))))))

(defn hae-urakan-sanktiot
  "Hakee urakan sanktiot perintäpvm:n mukaan"
  [db user {:keys [urakka-id alku loppu]}]

  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (log/debug "Hae sanktiot (" urakka-id alku loppu ")")
  (into []
        (comp (geo/muunna-pg-tulokset :laatupoikkeama_sijainti)
              (map #(konv/string->keyword % :laatupoikkeama_paatos_kasittelytapa :vakiofraasi))
              (map konv/alaviiva->rakenne)
              (map #(konv/decimal->double % :summa))
              (map #(assoc % :laji (keyword (:laji %)))))
        (sanktiot/hae-urakan-sanktiot db urakka-id (konv/sql-timestamp alku) (konv/sql-timestamp loppu))))

(defn- vaadi-sanktiolaji-ja-sanktiotyyppi-yhteensopivat
  [db sanktiolaji sanktiotyypin-id]
  (let [mahdolliset-sanktiotyypit (into #{}
                                        (map :id (sanktiot/hae-sanktiotyyppi-sanktiolajilla
                                                   db {:sanktiolaji (name sanktiolaji)})))]
    (when-not (mahdolliset-sanktiotyypit sanktiotyypin-id)
      (throw (SecurityException. (str "Sanktiolaji" sanktiolaji " ei mahdollinen sanktiotyypille "
                                      sanktiotyypin-id))))))

(defn vaadi-sanktio-kuuluu-urakkaan [db urakka-id sanktio-id]
  "Tarkistaa, että sanktio kuuluu annettuun urakkaan"
  (when (id-olemassa? sanktio-id)
    (let [sanktion-urakka (:urakka (first (sanktiot/hae-sanktion-urakka-id db {:sanktioid sanktio-id})))]
      (when-not (= sanktion-urakka urakka-id)
        (throw (SecurityException. (str "Sanktio " sanktio-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " sanktion-urakka)))))))

(defn tallenna-laatupoikkeaman-sanktio
  [db user {:keys [id perintapvm laji tyyppi summa indeksi suorasanktio
                   toimenpideinstanssi vakiofraasi poistettu] :as sanktio} laatupoikkeama urakka]
  (log/debug "TALLENNA sanktio: " sanktio ", urakka: " urakka ", tyyppi: " tyyppi ", laatupoikkeamaon " laatupoikkeama)
  (log/debug "LAJI ON: " (pr-str laji))
  (when (id-olemassa? id) (vaadi-sanktio-kuuluu-urakkaan db urakka id))
  (let [sanktiotyyppi (if (:id tyyppi)
                        (:id tyyppi)
                        (when laji
                          (:id (first (sanktiot/hae-sanktiotyyppi-sanktiolajilla db {:sanktiolaji (name laji)})))))
        _ (vaadi-sanktiolaji-ja-sanktiotyyppi-yhteensopivat db laji sanktiotyyppi)
        params {:perintapvm (konv/sql-timestamp perintapvm)
                :ryhma (when laji (name laji))
                ;; hoitourakassa sanktiotyyppi valitaan kälistä, ylläpidosta päätellään implisiittisesti
                :tyyppi sanktiotyyppi
                :vakiofraasi (when vakiofraasi (name vakiofraasi))
                :tpi_id toimenpideinstanssi
                :urakka urakka
                ;; bonukselle miinus etumerkiksi, muistutuksen summa on kuitenkin nil
                :summa (when summa
                         (if (= :yllapidon_bonus laji)
                           (- (Math/abs summa))
                           (Math/abs summa)))
                :indeksi indeksi
                :laatupoikkeama laatupoikkeama
                :suorasanktio (or suorasanktio false)
                :id id
                :poistettu poistettu
                :muokkaaja (:id user)
                :luoja (:id user)}]
    (if-not (id-olemassa? id)
      (let [uusi-sanktio (sanktiot/luo-sanktio<! db params)]
        (sanktiot/merkitse-maksuera-likaiseksi! db (:id uusi-sanktio))
        (:id uusi-sanktio))
      (do
        (sanktiot/paivita-sanktio! db params)
        (sanktiot/merkitse-maksuera-likaiseksi! db id)
        id))))

(defn- valita-tieto-pyydetysta-selvityksesta [{:keys [db fim email urakka-id laatupoikkeama selvityksen-pyytaja]}]
  (go (viestinta/laheta-sposti-laatupoikkeamasta-selvitys-pyydetty
        {:db db :fim fim :email email :laatupoikkeama laatupoikkeama
         :selvityksen-pyytaja selvityksen-pyytaja :urakka-id urakka-id})))

(defn- tallenna-laatupoikkeaman-kommentit [{:keys [db user urakka laatupoikkeama id]}]
  (when-let [uusi-kommentti (:uusi-kommentti laatupoikkeama)]
    (log/info "UUSI KOMMENTTI LAATUPOIKKEAMAAN: " uusi-kommentti)
    (let [liite (some->> uusi-kommentti
                         :liite
                         :id
                         (liitteet/hae-urakan-liite-id db urakka)
                         first
                         :id)
          kommentti (kommentit/luo-kommentti<! db
                                               (name (:tekija laatupoikkeama))
                                               (:kommentti uusi-kommentti)
                                               liite
                                               (:id user))]
      ;; Liitä kommentti laatupoikkeamaon
      (laatupoikkeamat-q/liita-kommentti<! db id (:id kommentti)))))

(defn- tallenna-laatupoikkeaman-liitteet [db laatupoikkeama id]
  (when-let [uusi-liite (:uusi-liite laatupoikkeama)]
    (log/info "UUSI LIITE LAATUPOIKKEAMAAN: " uusi-liite)
    (laatupoikkeamat-q/liita-liite<! db id (:id uusi-liite))))

(defn- tallenna-laatupoikkeaman-paatos [{:keys [db urakka user laatupoikkeama id]}]
  ;; Urakanvalvoja voi kirjata päätöksen
  (when (and (:paatos (:paatos laatupoikkeama))
             (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-laadunseuranta-sanktiot urakka user))
    (log/info "Kirjataan päätös havainnolle: " id ", päätös: " (:paatos laatupoikkeama))
    (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos laatupoikkeama)]
      (laatupoikkeamat-q/kirjaa-laatupoikkeaman-paatos!
        db
        (konv/sql-timestamp kasittelyaika)
        (name paatos) perustelu
        (name kasittelytapa) muukasittelytapa
        (:id user)
        id))
    (when (= :sanktio (:paatos (:paatos laatupoikkeama)))
      (doseq [sanktio (:sanktiot laatupoikkeama)]
        (tallenna-laatupoikkeaman-sanktio db user sanktio id urakka)))))

(defn tallenna-laatupoikkeama [db user fim email {:keys [urakka] :as laatupoikkeama}]
  (log/info "Tuli laatupoikkeama: " laatupoikkeama)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka)
  (jdbc/with-db-transaction [c db]
    (let [osapuoli (roolit/osapuoli user)
          laatupoikkeama-kannassa-ennen-tallennusta
          (first (into []
                       yhteiset/laatupoikkeama-xf
                       (laatupoikkeamat-q/hae-laatupoikkeaman-tiedot db urakka (:id laatupoikkeama))))
          laatupoikkeama (assoc laatupoikkeama
                           ;; Jos osapuoli ei ole urakoitsija, voidaan asettaa selvitys-pyydetty päälle
                           :selvitys-pyydetty (and (not= :urakoitsija osapuoli)
                                                   (:selvitys-pyydetty laatupoikkeama)))]

      (let [id (laatupoikkeamat-q/luo-tai-paivita-laatupoikkeama c user laatupoikkeama)]
        (tallenna-laatupoikkeaman-kommentit {:db c :user user :urakka urakka
                                             :laatupoikkeama laatupoikkeama :id id})
        (tallenna-laatupoikkeaman-liitteet db laatupoikkeama id)
        (tallenna-laatupoikkeaman-paatos {:db c :urakka urakka :user user
                                          :laatupoikkeama laatupoikkeama :id id})

        (when (and (not (:selvitys-pyydetty laatupoikkeama-kannassa-ennen-tallennusta))
                   (:selvitys-pyydetty laatupoikkeama))
          (valita-tieto-pyydetysta-selvityksesta {:db db :fim fim :email email :urakka-id urakka
                                                  :laatupoikkeama (assoc laatupoikkeama :id id)
                                                  :selvityksen-pyytaja (str (:etunimi user)
                                                                            " "
                                                                            (:sukunimi user))}))

        (hae-laatupoikkeaman-tiedot c user urakka id)))))

(defn hae-sanktiotyypit
  "Palauttaa kaikki sanktiotyypit, hyvin harvoin muuttuvaa dataa."
  [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
        ;; Muunnetaan sanktiolajit arraysta, keyword setiksi
        (map #(konv/array->set % :laji keyword))
        (sanktiot/hae-sanktiotyypit db)))

(defn tallenna-suorasanktio [db user sanktio laatupoikkeama urakka [hk-alkupvm hk-loppupvm]]
  ;; Roolien tarkastukset on kopioitu laatupoikkeaman kirjaamisesta,
  ;; riittäisi varmaan vain roolit/urakanvalvoja?
  (log/debug "Tallenna suorasanktio " (:id sanktio) " laatupoikkeamaan " (:id laatupoikkeama)
             ", urakassa " urakka)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka)
  (when (id-olemassa? (:yllapitokohde laatupoikkeama))
    (yllapitokohteet-yleiset/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka (:yllapitokohde laatupoikkeama)))
  (jdbc/with-db-transaction [c db]
    ;; poistetaan laatupoikkeama vain jos kyseessä on suorasanktio,
    ;; koska laatupoikkeamalla voi olla 0...n sanktiota
    (let [poista-laatupoikkeama? (boolean (and (:suorasanktio sanktio) (:poistettu sanktio)))
          id (laatupoikkeamat-q/luo-tai-paivita-laatupoikkeama c user (assoc laatupoikkeama :tekija "tilaaja"
                                                                                            :poistettu poista-laatupoikkeama?))]

      (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos laatupoikkeama)]
        (laatupoikkeamat-q/kirjaa-laatupoikkeaman-paatos! c
                                                          (konv/sql-timestamp kasittelyaika)
                                                          (name paatos) perustelu
                                                          (name kasittelytapa) muukasittelytapa
                                                          (:id user)
                                                          id))
      (tallenna-laatupoikkeaman-sanktio c user sanktio id urakka)
      (hae-urakan-sanktiot c user {:urakka-id urakka :alku hk-alkupvm :loppu hk-loppupvm}))))

(defn hae-urakkatyypin-sanktiolajit
  "Palauttaa urakkatyypin sanktiolajit settinä"
  [db user urakka-id urakkatyyppi]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (let [sanktiotyypit (into []
                            (map #(konv/array->set % :sanktiolaji keyword))
                            (sanktiot/hae-urakkatyypin-sanktiolajit
                              db (name urakkatyyppi)))
        sanktiolajit (apply clojure.set/union
                            (map :sanktiolaji sanktiotyypit))]
    sanktiolajit))

(defrecord Laadunseuranta []
  component/Lifecycle
  (start [{:keys [http-palvelin db karttakuvat fim sonja-sahkoposti] :as this}]

    (julkaise-palvelut
      http-palvelin

      :hae-urakan-laatupoikkeamat
      (fn [user tiedot]
        (hae-urakan-laatupoikkeamat db user tiedot))

      :tallenna-laatupoikkeama
      (fn [user laatupoikkeama]
        (tallenna-laatupoikkeama db user fim sonja-sahkoposti laatupoikkeama))

      :tallenna-suorasanktio
      (fn [user tiedot]
        (tallenna-suorasanktio db user (:sanktio tiedot) (:laatupoikkeama tiedot)
                               (get-in tiedot [:laatupoikkeama :urakka])
                               (:hoitokausi tiedot)))

      :hae-laatupoikkeaman-tiedot
      (fn [user {:keys [urakka-id laatupoikkeama-id]}]
        (hae-laatupoikkeaman-tiedot db user urakka-id laatupoikkeama-id))

      :hae-urakan-sanktiot
      (fn [user tiedot]
        (hae-urakan-sanktiot db user tiedot))

      :hae-sanktiotyypit
      (fn [user]
        (hae-sanktiotyypit db user))

      :hae-urakkatyypin-sanktiolajit
      (fn [user {:keys [urakka-id urakkatyyppi]}]
        (hae-urakkatyypin-sanktiolajit db user urakka-id urakkatyyppi)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :hae-urakan-laatupoikkeamat
                     :tallenna-laatupoikkeama
                     :hae-laatupoikkeaman-tiedot
                     :hae-urakan-sanktiot
                     :hae-sanktiotyypit
                     :tallenna-suorasanktio
                     :hae-urakkatyypin-sanktiolajit
                     :lisaa-tarkastukselle-laatupoikkeama
                     :hae-tarkastusajon-reittipisteet)
    this))
