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
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.sanktiot :as sanktiot]
            [harja.kyselyt.tarkastukset :as tarkastukset]

            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.sanktiot :as sanktiot-domain]
            [harja.geo :as geo]

            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.laadunseuranta :as laadunseuranta]

            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]
            [clojure.core.async :as async]))

(def laatupoikkeama-xf
  (comp
   (geo/muunna-pg-tulokset :sijainti)
   (map konv/alaviiva->rakenne)
   (map #(assoc % :selvitys-pyydetty (:selvityspyydetty %)))
   (map #(dissoc % :selvityspyydetty))
   (map #(assoc % :tekija (keyword (:tekija %))))
   (map #(update-in % [:paatos :paatos]
                    (fn [p]
                      (when p (keyword p)))))
   (map #(update-in % [:paatos :kasittelytapa]
                    (fn [k]
                      (when k (keyword k)))))
   (map #(if (nil? (:kasittelyaika (:paatos %)))
           (dissoc % :paatos)
           %))))

(def tarkastus-xf
  (comp
    (geo/muunna-pg-tulokset :sijainti)
    (map konv/alaviiva->rakenne)
    (map #(konv/array->set % :vakiohavainnot))
    (map laadunseuranta/tarkastus-tiedolla-onko-ok)
    (map #(konv/string->keyword % :tyyppi :tekija))
    (map #(dissoc % :sopimus))
    (map (fn [tarkastus]
           (condp = (:tyyppi tarkastus)
             :talvihoito (dissoc tarkastus :soratiemittaus)
             :soratie (dissoc tarkastus :talvihoitomittaus)
             :tiesto (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
             tarkastus)))))

(defn hae-urakan-laatupoikkeamat [db user {:keys [listaus urakka-id alku loppu]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [tietokannasta-nostetut
          ((case listaus
             :omat laatupoikkeamat/hae-omat-laatupoikkeamat
             :kaikki laatupoikkeamat/hae-kaikki-laatupoikkeamat
             :selvitys laatupoikkeamat/hae-selvitysta-odottavat-laatupoikkeamat
             :kasitellyt laatupoikkeamat/hae-kasitellyt-laatupoikkeamat)
           db
           {:urakka urakka-id
            :alku (konv/sql-timestamp alku)
            :loppu (konv/sql-timestamp loppu)
            :kayttaja (:id user)})
          uniikit (map (fn [[_ vektori]] (first vektori)) (group-by :id tietokannasta-nostetut))
          tulos (into [] laatupoikkeama-xf uniikit)]
      tulos)))

(defn hae-laatupoikkeaman-tiedot
  "Hakee yhden laatupoikkeaman kaiken tiedon muokkausnäkymää varten: laatupoikkeaman perustiedot, kommentit ja liitteet, päätös ja sanktiot.
   Ottaa urakka-id:n ja laatupoikkeama-id:n. Urakka id:tä käytetään oikeustarkistukseen, laatupoikkeaman tulee olla annetun urakan
   toimenpiteeseen kytketty."
  [db user urakka-id laatupoikkeama-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (let [laatupoikkeama (first (into []
                              laatupoikkeama-xf
                              (laatupoikkeamat/hae-laatupoikkeaman-tiedot db urakka-id laatupoikkeama-id)))]
    (when laatupoikkeama
      (assoc laatupoikkeama
        :kommentit (into []
                         (comp (map konv/alaviiva->rakenne)
                               (map #(assoc % :tekija (name (:tekija %))))
                               (map (fn [{:keys [liite] :as kommentti}]
                                      (if (:id liite)
                                        kommentti
                                        (dissoc kommentti :liite)))))
                         (laatupoikkeamat/hae-laatupoikkeaman-kommentit db laatupoikkeama-id))
        :sanktiot (into []
                        (comp (map #(konv/array->set % :tyyppi_laji keyword))
                              (map konv/alaviiva->rakenne)
                              (map #(konv/string->keyword % :laji :vakiofraasi))
                              (map #(assoc %
                                     :sakko? (sanktiot-domain/sakko? %)
                                     :summa (some-> % :summa double))))
                        (sanktiot/hae-laatupoikkeaman-sanktiot db laatupoikkeama-id))
        :liitteet (into [] (laatupoikkeamat/hae-laatupoikkeaman-liitteet db laatupoikkeama-id))))))

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

(defn tallenna-laatupoikkeama [db user {:keys [urakka] :as laatupoikkeama}]
  (log/info "Tuli laatupoikkeama: " laatupoikkeama)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka)
  (jdbc/with-db-transaction [c db]
    (let [osapuoli (roolit/osapuoli user)
          laatupoikkeama (assoc laatupoikkeama
                                ;; Jos osapuoli ei ole urakoitsija, voidaan asettaa selvitys-pyydetty päälle
                                :selvitys-pyydetty (and (not= :urakoitsija osapuoli)
                                                        (:selvitys-pyydetty laatupoikkeama))

                                ;; Jos urakoitsija kommentoi, asetetaan selvitys annettu
                                :selvitys-annettu (and (:uusi-kommentti laatupoikkeama)
                                                       (= :urakoitsija osapuoli)))
          id (laatupoikkeamat/luo-tai-paivita-laatupoikkeama c user laatupoikkeama)]
      ;; Luodaan uudet kommentit
      (when-let [uusi-kommentti (:uusi-kommentti laatupoikkeama)]
        (log/info "UUSI KOMMENTTI: " uusi-kommentti)
        (let [liite (some->> uusi-kommentti
                             :liite
                             :id
                             (liitteet/hae-urakan-liite-id c urakka)
                             first
                             :id)
              kommentti (kommentit/luo-kommentti<! c
                                                   (name (:tekija laatupoikkeama))
                                                   (:kommentti uusi-kommentti)
                                                   liite
                                                   (:id user))]
          ;; Liitä kommentti laatupoikkeamaon
          (laatupoikkeamat/liita-kommentti<! c id (:id kommentti))))

      ;; Liitä liite laatupoikkeamaon
      (when-let [uusi-liite (:uusi-liite laatupoikkeama)]
        (log/info "UUSI LIITE: " uusi-liite)
        (laatupoikkeamat/liita-liite<! c id (:id uusi-liite)))

      ;; Urakanvalvoja voi kirjata päätöksen
      (when (and (:paatos (:paatos laatupoikkeama))
                 (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-laadunseuranta-sanktiot urakka user))
        (log/info "Kirjataan päätös havainnolle: " id ", päätös: " (:paatos laatupoikkeama))
        (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos laatupoikkeama)]
          (laatupoikkeamat/kirjaa-laatupoikkeaman-paatos! c
                                                          (konv/sql-timestamp kasittelyaika)
                                                          (name paatos) perustelu
                                                          (name kasittelytapa) muukasittelytapa
                                                          (:id user)
                                                          id))
        (when (= :sanktio (:paatos (:paatos laatupoikkeama)))
          (doseq [sanktio (:sanktiot laatupoikkeama)]
            (tallenna-laatupoikkeaman-sanktio c user sanktio id urakka))))

      (hae-laatupoikkeaman-tiedot c user urakka id))))

(defn hae-sanktiotyypit
  "Palauttaa kaikki sanktiotyypit, hyvin harvoin muuttuvaa dataa."
  [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
        ;; Muunnetaan sanktiolajit arraysta, keyword setiksi
        (map #(konv/array->set % :laji keyword))
        (sanktiot/hae-sanktiotyypit db)))


(defn hae-urakan-tarkastukset
  "Palauttaa urakan tarkastukset annetulle aikavälille."
  ([db user parametrit]
   (hae-urakan-tarkastukset db user parametrit false 501))
  ([db user {:keys [urakka-id alkupvm loppupvm tienumero tyyppi
                    havaintoja-sisaltavat? vain-laadunalitukset?]}
    palauta-reitti? max-rivimaara]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
   (let [urakoitsija? (roolit/urakoitsija? user)
         tarkastukset-raakana (tarkastukset/hae-urakan-tarkastukset
                                db
                                {:urakka                  urakka-id
                                 :kayttaja_on_urakoitsija urakoitsija?
                                 :alku                    (konv/sql-timestamp alkupvm)
                                 :loppu                   (konv/sql-timestamp loppupvm)
                                 :rajaa_tienumerolla      (boolean tienumero)
                                 :tienumero               tienumero
                                 :rajaa_tyypilla          (boolean tyyppi)
                                 :tyyppi                  (and tyyppi (name tyyppi))
                                 :havaintoja_sisaltavat   havaintoja-sisaltavat?
                                 :vain_laadunalitukset    vain-laadunalitukset?
                                 :maxrivimaara            max-rivimaara})
         tarkastukset (into []
                            (comp tarkastus-xf
                                  (if palauta-reitti?
                                    identity
                                    (map #(dissoc % :sijainti))))
                            tarkastukset-raakana)
         tarkastukset (konv/sarakkeet-vektoriin tarkastukset {:liite :liitteet})]
     tarkastukset)))

(defn hae-tarkastus [db user urakka-id tarkastus-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (let [urakoitsija? (roolit/urakoitsija? user)
        tarkastus (first (into [] tarkastus-xf (tarkastukset/hae-tarkastus
                                                 db
                                                 urakka-id
                                                 tarkastus-id
                                                 urakoitsija?)))]
    (when tarkastus
      (assoc tarkastus
       :liitteet (into [] (tarkastukset/hae-tarkastuksen-liitteet db tarkastus-id))))))

(defn tallenna-tarkastus [db user urakka-id tarkastus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (try
    (jdbc/with-db-transaction [c db]
      (let [uusi-tarkastus? (nil? (:id tarkastus))
            tarkastustyyppi (:tyyppi tarkastus)
            talvihoitomittaus? (some #(get-in (:talvihoitomittaus tarkastus) %)
                                     laadunseuranta/talvihoitomittauksen-kentat)
            soratiemittaus? (some #(get-in (:soratiemittaus tarkastus) %)
                                     laadunseuranta/soratiemittauksen-kentat)
            tarkastus (assoc tarkastus :lahde "harja-ui")
            id (tarkastukset/luo-tai-paivita-tarkastus c user urakka-id tarkastus)]

        (when (and (or
                     (= :talvihoito tarkastustyyppi)
                     (= :laatu tarkastustyyppi))
                   talvihoitomittaus?)
          (tarkastukset/luo-tai-paivita-talvihoitomittaus
           c id (or uusi-tarkastus? (not (:tarkastus (:talvihoitomittaus tarkastus))))
           (-> (:talvihoitomittaus tarkastus)
               (assoc :lampotila-tie
                      (get-in (:talvihoitomittaus tarkastus) [:lampotila :tie]))
               (assoc :lampotila-ilma
                      (get-in (:talvihoitomittaus tarkastus) [:lampotila :ilma]))
               (assoc :tr
                      (:tr tarkastus)))))

        (when (and (or
                     (= :soratie tarkastustyyppi)
                     (= :laatu tarkastustyyppi))
                   soratiemittaus?)
          (tarkastukset/luo-tai-paivita-soratiemittaus
            c id
            (or uusi-tarkastus? (not (:tarkastus (:soratiemittaus tarkastus))))
            (:soratiemittaus tarkastus)))

        (when-let [uusi-liite (:uusi-liite tarkastus)]
          (log/info "UUSI LIITE: " uusi-liite)
          (tarkastukset/luo-liite<! c id (:id uusi-liite)))

        (hae-tarkastus c user urakka-id id)))
    (catch Exception e
      (log/info e "Tarkastuksen tallennuksessa poikkeus!"))))

(defn tallenna-suorasanktio [db user sanktio laatupoikkeama urakka [hk-alkupvm hk-loppupvm]]
  ;; Roolien tarkastukset on kopioitu laatupoikkeaman kirjaamisesta,
  ;; riittäisi varmaan vain roolit/urakanvalvoja?
  (log/debug "Tallenna suorasanktio " (:id sanktio) " laatupoikkeamaan " (:id laatupoikkeama)
            ", urakassa " urakka)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka)
  (when (id-olemassa? (:yllapitokohde laatupoikkeama))
    (yllapitokohteet-yleiset/vaadi-yllapitokohde-kuuluu-urakkaan db urakka (:yllapitokohde laatupoikkeama)))
  (jdbc/with-db-transaction [c db]
     ;; poistetaan laatupoikkeama vain jos kyseessä on suorasanktio,
     ;; koska laatupoikkeamalla voi olla 0...n sanktiota
    (let [poista-laatupoikkeama? (boolean (and (:suorasanktio sanktio) (:poistettu sanktio)))
          id (laatupoikkeamat/luo-tai-paivita-laatupoikkeama c user (assoc laatupoikkeama :tekija "tilaaja"
                                                                                          :poistettu poista-laatupoikkeama?))]

      (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos laatupoikkeama)]
        (laatupoikkeamat/kirjaa-laatupoikkeaman-paatos! c
                                            (konv/sql-timestamp kasittelyaika)
                                            (name paatos) perustelu
                                            (name kasittelytapa) muukasittelytapa
                                            (:id user)
                                            id))
      (tallenna-laatupoikkeaman-sanktio c user sanktio id urakka)
      (hae-urakan-sanktiot c user {:urakka-id urakka :alku hk-alkupvm :loppu hk-loppupvm}))))

(defn- tarkastusreittien-parametrit
  [user {:keys [havaintoja-sisaltavat? vain-laadunalitukset? tienumero
                alkupvm loppupvm tyyppi urakka-id] :as parametrit}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset
                             user :urakka-id)
  {:urakka urakka-id
   :alku alkupvm :loppu loppupvm
   :rajaa_tienumerolla (some? tienumero) :tienumero tienumero
   :rajaa_tyypilla (some? tyyppi) :tyyppi (and tyyppi (name tyyppi))
   :havaintoja_sisaltavat havaintoja-sisaltavat?
   :vain_laadunalitukset vain-laadunalitukset?
   :kayttaja_on_urakoitsija (roolit/urakoitsija? user)})

(defn hae-tarkastusreitit-kartalle [db user {:keys [extent parametrit]}]
  (let [parametrit (tarkastusreittien-parametrit user parametrit)
        [x1 y1 x2 y2] extent
        alue {:xmin x1 :ymin y1 :xmax x2 :ymax y2}
        alue (assoc alue :toleranssi (geo/karkeistustoleranssi alue))
        ch (async/chan 32
                       (comp
                         (map #(konv/array->set % :vakiohavainnot))
                         (map laadunseuranta/tarkastus-tiedolla-onko-ok)
                         (map #(konv/string->keyword % :tyyppi :tekija))
                         (map #(assoc %
                                 :tyyppi-kartalla :tarkastus
                                 :sijainti (:reitti %)))
                         (esitettavat-asiat/kartalla-esitettavaan-muotoon-xf)))]
    (async/thread
      (try
        (jdbc/with-db-transaction [db db
                                   {:read-only? true}]
          (tarkastukset/hae-urakan-tarkastukset-kartalle
           db ch
           (merge alue
                  parametrit)))
        (catch Throwable t
          (log/warn t "Virhe haettaessa tarkastuksia kartalle"))))

    ch))

(defn hae-tarkastusreittien-asiat-kartalle
  [db user {x :x y :y toleranssi :toleranssi :as parametrit}]
  (let [parametrit (tarkastusreittien-parametrit user parametrit)]
    (into []
          (comp
            (map #(konv/array->set % :vakiohavainnot))
            (map #(assoc % :tyyppi-kartalla :tarkastus))
            (map konv/alaviiva->rakenne)
            (map #(konv/string->keyword % :tyyppi))
            (map #(update % :tierekisteriosoite konv/lue-tr-osoite)))
          (tarkastukset/hae-urakan-tarkastusten-asiat-kartalle
            db
            (assoc parametrit
              :x x :y y
              :toleranssi toleranssi)))))

(defn lisaa-tarkastukselle-laatupoikkeama [db user urakka-id tarkastus-id]
  (log/debug (format "Luodaan laatupoikkeama tarkastukselle (id: %s)" tarkastus-id))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (when-let [tarkastus (hae-tarkastus db user urakka-id tarkastus-id)]
    (jdbc/with-db-transaction [db db]
      (let [laatupoikkeama {:sijainti (:sijainti tarkastus)
                            :kuvaus (:havainnot tarkastus)
                            :aika (:aika tarkastus)
                            :yllapitokohde (:yllapitokohde tarkastus)
                            :tr (:tr tarkastus)
                            :urakka urakka-id
                            :tekija (:tekija tarkastus)}
            laatupoikkeama-id (laatupoikkeamat/luo-tai-paivita-laatupoikkeama db user laatupoikkeama)]
        (tarkastukset/liita-tarkastukselle-laatupoikkeama<!
          db
          {:tarkastus tarkastus-id
           :laatupoikkeama laatupoikkeama-id})
        (tarkastukset/liita-tarkastuksen-liitteet-laatupoikkeamalle<!
          db
          {:tarkastus tarkastus-id
           :laatupoikkeama laatupoikkeama-id})
        laatupoikkeama-id))))

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

(defn hae-tarkastusajon-reittipisteet
  "Palauttaa tarkastusajon id:tä vastaan ko. ajon reittipisteet kartalle piirtoa varten.
  Ajateltu käyttö ainoastaan debug-tarkoituksiin jvh-käyttäjällä ns. salaisessa TR-osiossa."
  [db user tarkastusajon-id]
  (roolit/vaadi-rooli user roolit/jarjestelmavastaava)
  (into []
        (comp
          (map #(konv/array->vec % :havainnot))
          (geo/muunna-pg-tulokset :sijainti))
        (tarkastukset/hae-tarkastusajon-reittipisteet db {:tarkastusajoid tarkastusajon-id})))

(defrecord Laadunseuranta []
  component/Lifecycle
  (start [{:keys [http-palvelin db karttakuvat] :as this}]

    (karttakuvat/rekisteroi-karttakuvan-lahde!
     karttakuvat :tarkastusreitit
     (partial #'hae-tarkastusreitit-kartalle db)
     (partial #'hae-tarkastusreittien-asiat-kartalle db)
     "tr")

    (julkaise-palvelut
      http-palvelin

      :hae-urakan-laatupoikkeamat
      (fn [user tiedot]
        (hae-urakan-laatupoikkeamat db user tiedot))

      :tallenna-laatupoikkeama
      (fn [user laatupoikkeama]
        (tallenna-laatupoikkeama db user laatupoikkeama))

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

      :hae-urakan-tarkastukset
      (fn [user tiedot]
        (hae-urakan-tarkastukset db user tiedot))

      :tallenna-tarkastus
      (fn [user {:keys [urakka-id tarkastus]}]
        (tallenna-tarkastus db user urakka-id tarkastus))

      :hae-tarkastus
      (fn [user {:keys [urakka-id tarkastus-id]}]
        (hae-tarkastus db user urakka-id tarkastus-id))

      :hae-urakkatyypin-sanktiolajit
      (fn [user {:keys [urakka-id urakkatyyppi]}]
        (hae-urakkatyypin-sanktiolajit db user urakka-id urakkatyyppi))

      :lisaa-tarkastukselle-laatupoikkeama
      (fn [user {:keys [urakka-id tarkastus-id]}]
        (lisaa-tarkastukselle-laatupoikkeama db user urakka-id tarkastus-id))

      :hae-tarkastusajon-reittipisteet
      (fn [user {:keys [tarkastusajon-id]}]
        (hae-tarkastusajon-reittipisteet db user tarkastusajon-id)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :hae-urakan-laatupoikkeamat
                     :tallenna-laatupoikkeama
                     :hae-laatupoikkeaman-tiedot
                     :hae-urakan-sanktiot
                     :hae-sanktiotyypit
                     :hae-urakan-tarkastukset
                     :tallenna-tarkastus
                     :tallenna-suorasanktio
                     :hae-tarkastus
                     :hae-urakkatyypin-sanktiolajit
                     :lisaa-tarkastukselle-laatupoikkeama
                     :hae-tarkastusajon-reittipisteet)
    this))
