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
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat-q]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.sanktiot :as sanktiot]
            [harja.palvelin.palvelut.laadunseuranta.bonus-konfiguraatio :as bonus-konfiguraatio]
            [harja.palvelin.palvelut.laadunseuranta.sanktio-konfiguraatio :as sanktio-konfiguraatio]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.palvelut.laadunseuranta.viestinta :as viestinta]
            [harja.palvelin.palvelut.laadunseuranta.yhteiset :as yhteiset]
            [harja.palvelin.palvelut.laadunseuranta.laadunseuranta-tulosteet :as laadunseuranta-tulosteet]
            [harja.palvelin.raportointi.pdf :as raportointi-pdf]
            [harja.palvelin.raportointi.excel :as raportointi-excel]

            [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.muunnos :as muunnos]
            [harja.kyselyt.urakat :as urakat]
            [harja.domain.roolit :as roolit]
            [harja.domain.urakka :as domain-urakka]
            [harja.pvm :as pvm]
            [harja.domain.laadunseuranta.sanktio :as sanktiot-domain]

            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]))


(defn hae-urakan-laatupoikkeamat [db user {:keys [listaus urakka-id alku loppu]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [tietokannasta-nostetut
          ((case listaus
             :omat laatupoikkeamat-q/hae-omat-laatupoikkeamat
             :kaikki laatupoikkeamat-q/hae-kaikki-laatupoikkeamat
             :selvitys laatupoikkeamat-q/hae-selvitysta-odottavat-laatupoikkeamat
             :kasitellyt laatupoikkeamat-q/hae-kasitellyt-laatupoikkeamat
             :poikkeamaraportilliset laatupoikkeamat-q/hae-poikkeamaraportilliset-laatupoikkeamat)
           db
           {:urakka urakka-id
            :alku (konv/sql-timestamp alku)
            :loppu (konv/sql-timestamp loppu)
            :kayttaja (:id user)})
          uniikit (map (fn [[_ vektori]] (first vektori)) (group-by :id tietokannasta-nostetut))
          tulos (into [] yhteiset/laatupoikkeama-xf uniikit)]
      tulos)))

(defn vaadi-laatupoikkeama-kuuluu-urakkaan
  "Tarkistaa, että laatupoikkeama kuuluu annettuun urakkaan"
  [db urakka-id laatupoikkeama-id]
  (when (id-olemassa? laatupoikkeama-id)
    (let [laatupoikkeaman-urakka (:urakka (first (laatupoikkeamat-q/hae-laatupoikkeaman-urakka-id db {:laatupoikkeamaid laatupoikkeama-id})))]
      (when-not (= laatupoikkeaman-urakka urakka-id)
        (throw (SecurityException. (str "Laatupoikkeama " laatupoikkeama-id " ei kuulu valittuun urakkaan "
                                     urakka-id " vaan urakkaan " laatupoikkeaman-urakka)))))))

(defn- hae-bonuksen-liitteet
  "Hakee bonuksen liitteet"
  [db user urakka-id bonus-id]
  (log/info "hae-bonuksen-liitteet, bonus-id " bonus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-erilliskustannukset user urakka-id)
  (into [] (laatupoikkeamat-q/hae-bonuksen-liitteet db bonus-id)))

(defn- hae-sanktion-liitteet
  "Hakee yhden sanktion (laatupoikkeaman kautta) liitteet"
  [db user urakka-id laatupoikkeama-id]
  (log/info "hae-sanktion-liitteet, laatupoikkeama-id " laatupoikkeama-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (vaadi-laatupoikkeama-kuuluu-urakkaan db urakka-id laatupoikkeama-id)
  (into [] (laatupoikkeamat-q/hae-laatupoikkeaman-liitteet db laatupoikkeama-id)))

(defn- hae-urakan-laatupoikkeama-liitteet
  [db user urakka-id alkupvm loppupvm]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (into [] (laatupoikkeamat-q/hae-urakan-laatupoikkeama-liitteet db urakka-id alkupvm loppupvm)))

(defn hae-laatupoikkeaman-tiedot
  "Hakee yhden laatupoikkeaman kaiken tiedon muokkausnäkymää varten: laatupoikkeaman perustiedot, kommentit ja liitteet, päätös ja sanktiot.
   Ottaa urakka-id:n ja laatupoikkeama-id:n. Urakka id:tä käytetään oikeustarkistukseen, laatupoikkeaman tulee olla annetun urakan
   toimenpiteeseen kytketty."
  [db user urakka-id laatupoikkeama-id]
  (log/info "hae-laatupoikkeaman-tiedot laatupoikkeama-id " laatupoikkeama-id)
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
                              :sakko? (sanktiot-domain/muu-kuin-muistutus? %)
                              :summa (some-> % :summa double))))
                    (sanktiot/hae-laatupoikkeaman-sanktiot db laatupoikkeama-id))
        :liitteet (into [] (laatupoikkeamat-q/hae-laatupoikkeaman-liitteet db laatupoikkeama-id))))))

(defn hae-urakan-sanktiot-ja-bonukset
  "Hakee urakan sanktiot ja/tai bonukset perintäpvm:n ja urakka-id:n perusteella
  Oletusarvoisesti sekä sanktioden, että bonusten rivit molemmat haetaan ja palautetaan.
  Tarvittaessa optioilla voi estää sanktioiden/bonusten palauttamisen ja hakea vain toista tyyppiä."
  [db user {:keys [urakka-id alku loppu vain-yllapitokohteettomat? hae-sanktiot? hae-bonukset?] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (log/debug "hae-urakan-sanktiot-ja-bonukset :: tiedot:" tiedot)
  ;; Haetaan oletuksena sankiot ja bonukset.
  ;; HOX: Suurin osa muunnoksista tehdään hae-urakan-sanktiot/hae-urakan-bonukset "row-fn" -funktioissa.
  (let [hae-sanktiot? (if (boolean? hae-sanktiot?) hae-sanktiot? true)
        hae-bonukset? (if (boolean? hae-bonukset?) hae-bonukset? true)
        urakan-sanktiot (if hae-sanktiot?
                          (sanktiot/hae-urakan-sanktiot db {:urakka urakka-id
                                                            :alku (konv/sql-timestamp alku)
                                                            :loppu (konv/sql-timestamp loppu)})
                          [])
        urakan-bonukset (if hae-bonukset?
                          (sanktiot/hae-urakan-bonukset db {:urakka urakka-id
                                                            :alku (konv/sql-timestamp alku)
                                                            :loppu (konv/sql-timestamp loppu)})
                          []) ;;Hox! Sisältää myös ylläpidon bonukset, jotka ovat oikeasti sanktioita
        bonukset (into []
                   ;; Merkitse bonusrivit bonuksiksi, jotta ne erottaa helposti sanktioista.
                   (map #(assoc % :bonus? true))
                   urakan-bonukset)
        ;; Koostetaan lopuksi sanktio ja bonukset yhteen vektoriin ja ajetaan alaviiva->rakenne muunnos kaikille riveille
        sanktiot-ja-bonukset (into []
                               (map konv/alaviiva->rakenne
                                 (concat
                                   urakan-sanktiot
                                   bonukset)))]
    (if vain-yllapitokohteettomat?
      (filter #(nil? (get-in % [:yllapitokohde :id])) sanktiot-ja-bonukset)
      sanktiot-ja-bonukset)))

(defn- yhteiset-tulostetiedot [db user {:keys [urakka-id alku loppu suodattimet] :as tiedot}]
  (let [urakan-tiedot (first (urakat/hae-urakka db {:id urakka-id}))
        yllapitourakka? (domain-urakka/yllapitourakka? (keyword (:tyyppi urakan-tiedot)))
        sanktiot-ja-bonukset (hae-urakan-sanktiot-ja-bonukset db user tiedot)
        ;; Filtteröidään halutut matkaan
        kaikki-lajit (if (domain-urakka/yllapitourakka? (keyword (:tyyppi urakan-tiedot)))
                       #{:muistutukset :sanktiot :bonukset}
                       #{:muistutukset :sanktiot :bonukset :arvonvahennykset})
        rivit (if (= kaikki-lajit suodattimet)
                ;; rivin-tyyppi vertailuehto ei toimi kaikilla tyypeillä, joten tehdään sille oma tarkistus ensin
                sanktiot-ja-bonukset
                ;; Jos kaikki ei täsmää, niin sitten otetaan filtterillä oikeasti osa ulos
                (filter #(suodattimet (sanktiot-domain/rivin-tyyppi %)) sanktiot-ja-bonukset))]
    (laadunseuranta-tulosteet/sanktiot-ja-bonukset-raportti alku loppu (:nimi urakan-tiedot) yllapitourakka?
      suodattimet kaikki-lajit rivit)))

(defn- bonukset-ja-sanktiot-pdf
  [db user {:keys [urakka-id alku loppu suodattimet] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (log/debug "bonukset-ja-sanktiot-pdf :: tiedot:" tiedot)
  (raportointi-pdf/muodosta-pdf (yhteiset-tulostetiedot db user tiedot)))

(defn- bonukset-ja-sanktiot-excel
  [db workbook user {:keys [urakka-id alku loppu suodattimet] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (raportointi-excel/muodosta-excel (yhteiset-tulostetiedot db user tiedot) workbook))

(defn- vaadi-sanktiolaji-ja-sanktiotyyppi-yhteensopivat
  [db sanktiolaji sanktiotyypin-id urakan-alkupvm]
  (let [lajin-sanktiotyyppien-koodit (sanktiot-domain/sanktiolaji->sanktiotyyppi-koodi
                                       (keyword sanktiolaji) urakan-alkupvm)
        kaikki-sanktiotyypit (group-by :id (sanktiot/hae-sanktiotyypit db))
        mahdolliset-sanktiotyypit (into #{}
                                    (map :id (sanktiot/hae-sanktiotyyppi-koodilla
                                               db {:koodit lajin-sanktiotyyppien-koodit})))
        sanktiotyyppi (first (kaikki-sanktiotyypit sanktiotyypin-id))]
    (when-not (mahdolliset-sanktiotyypit sanktiotyypin-id)
      (throw (SecurityException. (str "Sanktiolaji: " sanktiolaji " ei mahdollinen sanktiotyypille id: "
                                   sanktiotyypin-id ", koodi: " (:koodi sanktiotyyppi)))))))

(defn vaadi-sanktio-kuuluu-urakkaan
  "Tarkistaa, että sanktio kuuluu annettuun urakkaan"
  [db urakka-id sanktio-id]
  (when (id-olemassa? sanktio-id)
    (let [sanktion-urakka (:urakka (first (sanktiot/hae-sanktion-urakka-id db {:sanktioid sanktio-id})))]
      (when-not (= sanktion-urakka urakka-id)
        (throw (SecurityException. (str "Sanktio " sanktio-id " ei kuulu valittuun urakkaan "
                                     urakka-id " vaan urakkaan " sanktion-urakka)))))))

(defn vaadi-talvisuolan-ylitys-ehto
  "Tarkistaa, että talvisuolan ylitys -sanktion ehdot täyttyvät:
  sanktion laji on talvisuolan ylitys
  ja sanktion käsittelyaika on urakan viimeisen hoitovuoden aikana."
  [urakan-tiedot kasittelyaika]
  (when-not (and
              (pvm/valissa? kasittelyaika
                (pvm/->pvm (str "01.10." (dec (-> urakan-tiedot :loppupvm pvm/vuosi))))
                (-> urakan-tiedot :loppupvm)))
    (throw (SecurityException. "Talvisuolan ylityksen ehdot eivät täyttyneet: Urakka ei ole teidenhoidon hoitourakka, tai sanktion perintäpäivä ei ole urakan viimeisen hoitovuoden aikana."))))

(defn- vaadi-sallittu-aktiivisessa-sanktio-konfiguraatiossa
  [db {:keys [urakka-id urakan-alkupvm paivamaara soveltuvuuskonteksti laji sanktiotyyppi-id]}]
  (when (and laji
          (not= :yllapidon_bonus laji)
          (not= :lupaussanktio laji)
          paivamaara)
    (sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
      db
      {:urakka-id urakka-id
       :hoitovuosi (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm paivamaara)
       :soveltuvuuskonteksti soveltuvuuskonteksti
       :laji laji
       :sanktiotyyppi-id sanktiotyyppi-id})))

(defn tallenna-laatupoikkeaman-sanktio
  [db user {:keys [id perintapvm maarattypvm maaraystapa laji tyyppi summa laskutusrajan-ylitys indeksi suorasanktio
                   toimenpideinstanssi vakiofraasi kasittelytapa poistettu tehtavaryhma tehtava] :as sanktio}
   laatupoikkeama-id urakka kasittelyaika {:keys [paivamaara soveltuvuuskonteksti]}]
  (log/debug "TALLENNA sanktio: " sanktio ", urakka: " urakka ", tyyppi: " tyyppi ", laatupoikkeamaan " laatupoikkeama-id)
  (when (id-olemassa? id) (vaadi-sanktio-kuuluu-urakkaan db urakka id))

  (let [urakan-tiedot (first (urakat/hae-urakka db urakka))
        _ (when (= :talvisuolan_ylitys laji) (vaadi-talvisuolan-ylitys-ehto urakan-tiedot kasittelyaika))
        summa (if (decimal? summa)
                (double summa) ;; Math/abs ei kestä BigDecimaalia, joten varmistetaan, ettei sitä käytetä
                summa)
        ;; MHU-urakoissa joiden alkuvuosi 2021 tai myöhemmin, ei koskaan sidota indeksiin
        indeksi (when-not (and
                            (= (:tyyppi urakan-tiedot) "teiden-hoito")
                            (> (-> urakan-tiedot :alkupvm pvm/vuosi) 2020))
                  indeksi)
        lajin-sanktiotyyppien-koodit (sanktiot-domain/sanktiolaji->sanktiotyyppi-koodi
                                       (keyword laji) (:alkupvm urakan-tiedot))
        sanktiotyyppi (if (:id tyyppi)
                        (:id tyyppi)
                        (when laji
                          (:id (first (sanktiot/hae-sanktiotyyppi-koodilla db {:koodit lajin-sanktiotyyppien-koodit})))))
        paivamaara (or paivamaara perintapvm)
        _ (when (= :yllapidon_bonus laji)
            (vaadi-sanktiolaji-ja-sanktiotyyppi-yhteensopivat db laji sanktiotyyppi (:alkupvm urakan-tiedot)))
        _ (vaadi-sallittu-aktiivisessa-sanktio-konfiguraatiossa
            db
            {:urakka-id urakka
             :urakan-alkupvm (:alkupvm urakan-tiedot)
             :paivamaara paivamaara
             :soveltuvuuskonteksti soveltuvuuskonteksti
             :laji laji
             :sanktiotyyppi-id sanktiotyyppi})
        params {;; Perintäpäivä voi olla null. UI:lla voi tapahtua niin, että jos sanktio on muokattu ensin tyhjälle perintäpäivälle ja sitten poistettu
                ;; Tätä ei kokonaan voi ui:lta estää. Joten tehdään perintäpäivän tallennuksesta ui:n kestävä, poistetuille sanktioille
                :perintapvm (if
                              (and poistettu (nil? perintapvm)) ;; Jos sanktio on poistettu ja perintäpäivä on nil, niin generoi tämä hetki
                              (konv/sql-timestamp (pvm/nyt))
                              (konv/sql-timestamp perintapvm))
                ;; Käytetään käsittelyaikaa, jos määrättypvm on asettamatta. Esim arvonvähennyksillä sitä ei ole pakko asettaa.
                :maarattypvm (if maarattypvm (konv/sql-date maarattypvm) (konv/sql-timestamp kasittelyaika))
                :kasittelytapa (name kasittelytapa)
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
                :laskutusrajan-ylitys laskutusrajan-ylitys
                :indeksi indeksi
                :laatupoikkeama laatupoikkeama-id
                :suorasanktio (or suorasanktio false)
                :id id
                :poistettu poistettu
                :muokkaaja (:id user)
                :luoja (:id user)
                ;; Arvonvähennyksen lisäkentät
                :maaraystapa (when maaraystapa (name maaraystapa))
                :tehtavaryhma (:id tehtavaryhma)
                :tehtava (:id tehtava)}]
    (if-not (id-olemassa? id)
      (let [uusi-sanktio (sanktiot/luo-sanktio<! db params)]
        (sanktiot/merkitse-maksuera-likaiseksi! db (:id uusi-sanktio))
        (:id uusi-sanktio))
      (do
        (sanktiot/paivita-sanktio! db params)
        (sanktiot/merkitse-maksuera-likaiseksi! db id)
        id))))

(defn- valita-tieto-pyydetysta-selvityksesta [{:keys [db sms fim email urakka-id
                                                      laatupoikkeama selvityksen-pyytaja]}]
  (viestinta/laheta-sposti-laatupoikkeamasta-selvitys-pyydetty
    {:db db :fim fim :email email :laatupoikkeama laatupoikkeama
     :selvityksen-pyytaja selvityksen-pyytaja :urakka-id urakka-id})
  (viestinta/laheta-tekstiviesti-laatupoikkeamasta-selvitys-pyydetty
    {:db db :fim fim :sms sms :laatupoikkeama laatupoikkeama
     :selvityksen-pyytaja selvityksen-pyytaja :urakka-id urakka-id}))

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
  (when-let [uusi-liite (:uudet-liitteet laatupoikkeama)]
    (let [liitteet (if (sequential? uusi-liite) uusi-liite [uusi-liite])]
      (doseq [liite liitteet]
        (log/info "UUSI LIITE LAATUPOIKKEAMAAN: " uusi-liite)
        (laatupoikkeamat-q/liita-liite<! db id (:id liite))))))

(defn- varmista-sanktion-tiedot [laatupoikkeama sanktio]
  (let [l-kasittelytapa (:kasittelytapa (:paatos laatupoikkeama))
        l-kasittelyaika (:kasittelyaika (:paatos laatupoikkeama))
        sanktio (if (and (nil? (:kasittelytapa sanktio)) l-kasittelytapa)
                  (assoc sanktio :kasittelytapa l-kasittelytapa)
                  sanktio)
        ;; Mikäli sanktion määräystapa on asettamatta, niin laitetaan siihen laatupoikkeaman käsittelytapa tai työmaakokous
        sanktio (if (and (nil? (:maaraystapa sanktio)) l-kasittelytapa)
                  (assoc sanktio :maaraystapa (name l-kasittelytapa))
                  sanktio)
        ;; Mikäli sanktion määrättypvm on asettamatta, niin laitetaan siihen laatupoikkeaman käsittelypäivämäärä
        sanktio (if (and (nil? (:maarattypvm sanktio)) l-kasittelyaika)
                  (assoc sanktio :maarattypvm l-kasittelyaika)
                  sanktio)]
    sanktio))

(defn- tallenna-laatupoikkeaman-paatos [{:keys [db urakka user laatupoikkeama id]}]
  ;; Urakanvalvoja voi kirjata päätöksen
  (when (and (:paatos (:paatos laatupoikkeama))
          (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-laadunseuranta-sanktiot urakka user))
    (log/info "Kirjataan päätös havainnolle: " id ", päätös: " (:paatos laatupoikkeama))
    (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos laatupoikkeama)]
      (laatupoikkeamat-q/kirjaa-laatupoikkeaman-paatos!
        db
        {:kasittelyaika (konv/sql-timestamp kasittelyaika)
         :paatos (name paatos)
         :perustelu perustelu
         :kasittelytapa (if kasittelytapa (name kasittelytapa) "ei-tiedossa")
         :muukasittelytapa muukasittelytapa
         :muokkaaja (:id user)
         :id id})
      (when (= :sanktio (:paatos (:paatos laatupoikkeama)))
        (doseq [sanktio (:sanktiot laatupoikkeama)]
          ;; Varmistetaan, että sanktion käsittelytapa on sama kuin laatupoikkeaman käsittelytapa, jos sanktion käsittelytapaa ei ole jostain syystä annettu
          (let [sanktio (varmista-sanktion-tiedot laatupoikkeama sanktio)]
            (tallenna-laatupoikkeaman-sanktio
              db user sanktio id urakka kasittelyaika
              {:paivamaara (:aika laatupoikkeama)
               :soveltuvuuskonteksti :laatupoikkeama})))))))

(defn tallenna-laatupoikkeama [{:keys [db user fim email sms laatupoikkeama]}]
  (let [urakka-id (:urakka laatupoikkeama)]
    (log/info "Tallenna laatupoikkeama urakkaan: " urakka-id)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
    (jdbc/with-db-transaction [db db]
      (let [osapuoli (roolit/osapuoli user)
            laatupoikkeama-kannassa-ennen-tallennusta
            (first (into []
                     yhteiset/laatupoikkeama-xf
                     (laatupoikkeamat-q/hae-laatupoikkeaman-tiedot db urakka-id (:id laatupoikkeama))))
            laatupoikkeama (assoc laatupoikkeama
                             ;; Jos osapuoli ei ole urakoitsija, voidaan asettaa selvitys-pyydetty päälle
                             :selvitys-pyydetty (and (not= :urakoitsija osapuoli)
                                                  (:selvitys-pyydetty laatupoikkeama)))]

        (let [id (laatupoikkeamat-q/luo-tai-paivita-laatupoikkeama db user laatupoikkeama)]
          (tallenna-laatupoikkeaman-kommentit {:db db :user user :urakka urakka-id
                                               :laatupoikkeama laatupoikkeama :id id})
          (tallenna-laatupoikkeaman-liitteet db laatupoikkeama id)
          (tallenna-laatupoikkeaman-paatos {:db db :urakka urakka-id :user user
                                            :laatupoikkeama laatupoikkeama :id id})

          (when (and (not (:selvitys-pyydetty laatupoikkeama-kannassa-ennen-tallennusta))
                  (:selvitys-pyydetty laatupoikkeama))
            (valita-tieto-pyydetysta-selvityksesta {:db db :fim fim :email email :urakka-id urakka-id
                                                    :sms sms :laatupoikkeama (assoc laatupoikkeama :id id)
                                                    :selvityksen-pyytaja (str (:etunimi user)
                                                                           " "
                                                                           (:sukunimi user))}))

          (hae-laatupoikkeaman-tiedot db user urakka-id id))))))

(defn hae-sanktiotyypit
  "Palauttaa kaikki sanktiotyypit, hyvin harvoin muuttuvaa dataa."
  [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
    (sanktiot/hae-sanktiotyypit db)))

(defn tallenna-suorasanktio [db user sanktio laatupoikkeama urakka [hk-alkupvm]]
  ;; Roolien tarkastukset on kopioitu laatupoikkeaman kirjaamisesta,
  ;; riittäisi varmaan vain roolit/urakanvalvoja?
  (log/debug "Tallenna suorasanktio " (:id sanktio) " laatupoikkeamaan " (:id laatupoikkeama) ", urakassa " urakka)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka)
  (when (id-olemassa? (:yllapitokohde laatupoikkeama))
    (yllapitokohteet-yleiset/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka (:yllapitokohde laatupoikkeama)))
  ;; Selvitetään palvelimelta, onko kyseessä olemassa olevan laatupoikkeaman kautta tehdyn
  ;; (ei-suora)sanktion muokkaus. Näiden muokkaus on sallittu vain ELY-urakanvalvojalle ja
  ;; ELY-pääkäyttäjälle. Uusien sanktioiden ja suorasanktioiden käsittely säilyy ennallaan.
  (let [olemassa-oleva-sanktio (when (id-olemassa? (:id sanktio))
                                 (first (sanktiot/hae-suorasanktion-tiedot db {:id (:id sanktio)})))
        laatupoikkeaman-sanktion-muokkaus? (boolean (and olemassa-oleva-sanktio
                                                       (not (:suorasanktio olemassa-oleva-sanktio))))]
    (when laatupoikkeaman-sanktion-muokkaus?
      (vaadi-sanktio-kuuluu-urakkaan db urakka (:id sanktio))
      (when-not (roolit/saa-muokata-laatupoikkeaman-sanktiota? user urakka)
        (throw (SecurityException.
                 (str "Käyttäjällä ei ole oikeutta muokata laatupoikkeaman kautta tehtyä sanktiota "
                   (:id sanktio) " urakassa " urakka)))))
    (jdbc/with-db-transaction [c db]
      ;; poistetaan laatupoikkeama vain jos kyseessä on suorasanktio,
      ;; koska laatupoikkeamalla voi olla 0...n sanktiota
      (let [poista-laatupoikkeama? (boolean (and (:suorasanktio sanktio) (:poistettu sanktio)))
            sanktio (varmista-sanktion-tiedot laatupoikkeama sanktio)
            ;; Laatupoikkeaman kautta tehtyä sanktiota muokattaessa säilytetään laatupoikkeaman ne
            ;; kentät, joita sanktiolomake ei näytä eikä käsittele oikein (tekijä, selvityspyyntö sekä
            ;; poikkeamaraportti-lippu). Näin muokkaus ei tyhjennä niitä. Suorasanktioille ja uusille
            ;; sanktioille tekijäksi asetetaan "tilaaja" kuten aiemminkin.
            tallennettava-laatupoikkeama (if laatupoikkeaman-sanktion-muokkaus?
                                           (let [sailytettavat (first (laatupoikkeamat-q/hae-laatupoikkeaman-tallennuksen-perustiedot
                                                                        c {:id (:id laatupoikkeama)}))]
                                             (assoc laatupoikkeama
                                               :tekija (:tekija sailytettavat)
                                               :selvitys-pyydetty (:selvitys_pyydetty sailytettavat)
                                               :sisaltaa-poikkeamaraportin? (get sailytettavat (keyword "sisaltaa-poikkeamaraportin?"))
                                               :poistettu poista-laatupoikkeama?))
                                           (assoc laatupoikkeama
                                             :tekija "tilaaja"
                                             :poistettu poista-laatupoikkeama?))
            id (laatupoikkeamat-q/luo-tai-paivita-laatupoikkeama c user tallennettava-laatupoikkeama)
            {:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos laatupoikkeama)
            _ (laatupoikkeamat-q/kirjaa-laatupoikkeaman-paatos! c
                (konv/sql-timestamp kasittelyaika)
                (name paatos) perustelu
                (name kasittelytapa) muukasittelytapa
                (:id user)
                id)
            sanktio-id (tallenna-laatupoikkeaman-sanktio
                         c user sanktio id urakka kasittelyaika
                         {:paivamaara hk-alkupvm
                          :soveltuvuuskonteksti :urakka})
            _ (tallenna-laatupoikkeaman-liitteet c laatupoikkeama id)]
        sanktio-id))))

(defn poista-suorasanktio
  "Merkitsee suorasanktion ja siihen liittyvän laatupoikkeaman poistetuksi. Palauttaa sanktion ID:n."
  [db user {sanktio-id :id urakka-id :urakka-id :as tiedot}]
  (assert (integer? sanktio-id) "Parametria 'sanktio-id' ei ole määritelty")
  (assert (integer? urakka-id) "Parametria 'urakka-id' ei ole määritelty")
  (log/debug "Merkitse suorasanktio " sanktio-id " ja siihen liittyvä laatupoikkeama poistetuksi urakassa " urakka-id)

  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)

  (jdbc/with-db-transaction [c db]

    (let [sanktio (first (sanktiot/hae-suorasanktion-tiedot db {:id sanktio-id}))
          ;; Poistetaan laatupoikkeama vain jos kyseessä on suorasanktio,
          ;;   koska laatupoikkeamalla voi olla 0...n sanktiota
          poista-laatupoikkeama? (and (boolean (:suorasanktio sanktio)) (:laatupoikkeama_id sanktio))]
      ;; Laatupoikkeaman kautta tehdyn (ei-suora)sanktion poisto on sallittu vain ELY-urakanvalvojalle
      ;; ja ELY-pääkäyttäjälle, samoin kuin muokkaus.
      (when (and sanktio (not (:suorasanktio sanktio)))
        (vaadi-sanktio-kuuluu-urakkaan c urakka-id sanktio-id)
        (when-not (roolit/saa-muokata-laatupoikkeaman-sanktiota? user urakka-id)
          (throw (SecurityException.
                   (str "Käyttäjällä ei ole oikeutta poistaa laatupoikkeaman kautta tehtyä sanktiota "
                     sanktio-id " urakassa " urakka-id)))))
      (when poista-laatupoikkeama?
        (laatupoikkeamat-q/poista-laatupoikkeama c user {:id (:laatupoikkeama_id sanktio)
                                                         :urakka-id urakka-id}))
      (sanktiot/poista-sanktio! db {:id sanktio-id
                                    :muokkaaja (:id user)})

      (sanktiot/merkitse-maksuera-likaiseksi! db sanktio-id)

      sanktio-id)))

(defrecord Laadunseuranta []
  component/Lifecycle
  (start [{:keys [http-palvelin db fim sms api-sahkoposti pdf-vienti excel-vienti] :as this}]

    (julkaise-palvelut
      http-palvelin

      :hae-urakan-laatupoikkeamat
      (fn [user tiedot]
        (hae-urakan-laatupoikkeamat db user tiedot))

      :tallenna-laatupoikkeama
      (fn [user laatupoikkeama]
        (tallenna-laatupoikkeama
          {:db db :user user :fim fim
           :email api-sahkoposti
           :sms sms :laatupoikkeama laatupoikkeama}))

      :tallenna-suorasanktio
      (fn [user tiedot]
        (tallenna-suorasanktio db user (:sanktio tiedot) (:laatupoikkeama tiedot)
          (get-in tiedot [:laatupoikkeama :urakka])
          (:hoitokausi tiedot)))

      :poista-suorasanktio
      (fn [user tiedot]
        (poista-suorasanktio db user tiedot))

      :hae-laatupoikkeaman-tiedot
      (fn [user {:keys [urakka-id laatupoikkeama-id]}]
        (hae-laatupoikkeaman-tiedot db user urakka-id laatupoikkeama-id))

      :hae-urakan-sanktiot-ja-bonukset
      (fn [user tiedot]
        (hae-urakan-sanktiot-ja-bonukset db user tiedot))

      :hae-sanktiotyypit
      (fn [user]
        (hae-sanktiotyypit db user))

      :hae-urakan-sanktio-konfiguraatio
      (fn [user tiedot]
        (sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio db user tiedot))

      :hae-urakan-bonus-konfiguraatio
      (fn [user tiedot]
        (bonus-konfiguraatio/hae-urakan-bonus-konfiguraatio db user tiedot))

      :hae-sanktio-profiilit-admin
      (fn [user _]
        (sanktio-konfiguraatio/hae-sanktio-profiilit-admin db user))

      :hae-sanktio-profiilin-detalji-admin
      (fn [user tiedot]
        (sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin db user tiedot))

      :hae-bonus-profiilit-admin
      (fn [user _]
        (bonus-konfiguraatio/hae-bonus-profiilit-admin db user))

      :hae-bonus-profiilin-detalji-admin
      (fn [user tiedot]
        (bonus-konfiguraatio/hae-bonus-profiilin-detalji-admin db user tiedot))

      :hae-urakan-laatupoikkeama-liitteet
      (fn [user {:keys [urakka-id alkupvm loppupvm]}]
        (hae-urakan-laatupoikkeama-liitteet db user urakka-id alkupvm loppupvm))

      :hae-sanktion-liitteet
      (fn [user {:keys [urakka-id laatupoikkeama-id]}]
        (hae-sanktion-liitteet db user urakka-id laatupoikkeama-id))

      :hae-bonuksen-liitteet
      (fn [user {:keys [urakka-id bonus-id]}]
        (hae-bonuksen-liitteet db user urakka-id bonus-id)))
    (when pdf-vienti
      (pdf-vienti/rekisteroi-pdf-kasittelija! pdf-vienti :bonukset-ja-sanktiot (partial #'bonukset-ja-sanktiot-pdf db)))
    (when excel-vienti
      (excel-vienti/rekisteroi-excel-kasittelija! excel-vienti :bonukset-ja-sanktiot (partial #'bonukset-ja-sanktiot-excel db)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakan-laatupoikkeamat
      :tallenna-laatupoikkeama
      :hae-laatupoikkeaman-tiedot
      :hae-urakan-sanktiot-ja-bonukset
      :hae-sanktiotyypit
      :hae-urakan-sanktio-konfiguraatio
      :hae-urakan-bonus-konfiguraatio
      :hae-sanktio-profiilit-admin
      :hae-sanktio-profiilin-detalji-admin
      :hae-bonus-profiilit-admin
      :hae-bonus-profiilin-detalji-admin
      :tallenna-suorasanktio
      :poista-suorasanktio
      :hae-urakan-laatupoikkeama-liitteet
      :hae-sanktion-liitteet
      :hae-bonuksen-liitteet)
    this))
