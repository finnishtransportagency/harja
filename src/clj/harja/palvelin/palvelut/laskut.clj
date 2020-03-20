(ns harja.palvelin.palvelut.laskut
  "Nimiavaruutta käytetään vain urakkatyypissä teiden-hoito (MHU)."
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt
             [laskut :as q]
             [aliurakoitsijat :as ali-q]
             [kustannusarvioidut-tyot :as kust-q]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]))


(defn kasittele-suorittaja
  "Tarkistaa onko aliurakoitsija-id olemassa tai löytyykö aliurakoitsija nimellä. Jos ei löydy, tallentaa aliurakoitsijan.
  Palauttaa olemassa olleen tai juuri tallennetun aliurakoitsijan id:n."
  [db user suorittaja-nimi]
  (let [suorittaja-id (:id (first (ali-q/hae-aliurakoitsija-nimella db
                                                                    {:nimi suorittaja-nimi})))]
    (if (nil? suorittaja-id)
      (do (ali-q/luo-aliurakoitsija<! db {:nimi     suorittaja-nimi
                                          :kayttaja (:id user)})
          (:id (first (ali-q/hae-aliurakoitsija-nimella db
                                                        {:nimi suorittaja-nimi}))))
      suorittaja-id)))

(defn hae-urakan-laskut
  "Palauttaa urakan laskut valitulta ajanjaksolta ilman laskuerittelyä (kohdennustietoja)."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (q/hae-urakan-laskut db {:urakka   (:urakka-id hakuehdot)
                           :alkupvm  (:alkupvm hakuehdot)
                           :loppupvm (:loppupvm hakuehdot)}))


(defn kasittele-kohdistukset
  [laskukohdistukset]
  (map
    (fn [[id kohdistukset]]
      (let [lasku (first kohdistukset)]
        (into {} {:id                    id
                  :tyyppi                (:tyyppi lasku)
                  :kokonaissumma         (:kokonaissumma lasku)
                  :erapaiva              (:erapaiva lasku)
                  :laskun-numero         (:laskun-numero lasku)
                  :koontilaskun-kuukausi (:koontilaskun-kuukausi lasku)
                  :suorittaja            (:suorittaja-id lasku)
                  :liite-id              (:liite-id lasku)
                  :liite-nimi            (:liite-nimi lasku)
                  :liite-tyyppi          (:liite-tyyppi lasku)
                  :liite-koko            (:liite-koko lasku)
                  :liite-oid             (:liite-oid lasku)
                  :kohdistukset          (mapv #(dissoc %
                                                        :tyyppi
                                                        :kokonaissumma
                                                        :erapaiva
                                                        :suorittaja-id
                                                        :liite-id
                                                        :liite-nimi
                                                        :liite-tyyppi
                                                        :liite-koko
                                                        :liite-oid
                                                        :koontilaskun-kuukausi)
                                               kohdistukset)})))
    laskukohdistukset))

(defn hae-kaikki-urakan-laskuerittelyt
  "Palauttaa urakan laskut laskuerittelyineen."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (let [laskukohdistukset (group-by :id (q/hae-kaikki-urakan-laskuerittelyt db {:urakka (:urakka-id hakuehdot)}))]
    (println "LK" laskukohdistukset)
    (kasittele-kohdistukset laskukohdistukset)))

(defn hae-urakan-laskuerittelyt
  "Palauttaa urakan laskut valitulta ajanjaksolta laskuerittelyineen."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (let [laskukohdistukset (group-by :id (q/hae-urakan-laskuerittelyt db {:urakka   (:urakka-id hakuehdot)
                                                                                :alkupvm  (:alkupvm hakuehdot)
                                                                                :loppupvm (:loppupvm hakuehdot)}))]
    (kasittele-kohdistukset laskukohdistukset)))

(defn hae-laskuerittely
  "Hakee yksittäisen laskun tiedot laskuerittelyineen."
  [db user {:keys [urakka-id id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [lasku (first (q/hae-lasku db {:urakka    urakka-id
                                      :id id}))
        laskun-kohdistukset (into [] (q/hae-laskun-kohdistukset db {:lasku (:id lasku)}))]
    (println "HAEN " lasku)
    (println "HAIN " laskun-kohdistukset)
    (assoc lasku :kohdistukset laskun-kohdistukset)))

(defn- laskuerittelyn-maksueratyyppi
  "Selvittää laskuerittelyn maksueratyypin, jotta laskun summa lasketaan myöhemmin oikeaan Sampoon lähetettvään maksuerään.
  Yleensä tyyppi on kokonaishintainen. Jos tehtävä on Äkillinen hoitotyö, maksuerätyyppi on akillinen-hoitotyö.
  Jos tehtävä on "
  [db tehtavaryhma-id tehtava-id]
  ;;TODO: tarkista ehdot, korjaa
  (cond (or (.contains (or (:nimi (first (q/hae-tehtavan-nimi db {:id tehtava-id}))) "") "Äkilliset hoitotytöt")
            (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "ÄKILLISET HOITOTYÖT"))
        "akilliset-hoitotyot"
        (or (.contains (or (:nimi (first (q/hae-tehtavan-nimi db {:id tehtava-id}))) "") "vahinkojen korja")
            (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "VAHINKOJEN KORJAAMINEN"))
        "muu"                                               ;; vahinkojen korjaukset
        :default
        "kokonaishintainen"))

(defn luo-tai-paivita-laskun-kohdistus
  "Luo uuden laskuerittelyrivin (kohdistuksen) kantaan tai päivittää olemassa olevan rivin. Rivi tunnistetaan laskun viitteen ja rivinumeron perusteella."
  [db user urakka-id lasku-id laskurivi]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (println "LUODAAN" laskurivi (nil? (:kohdistus-id laskurivi)))
  (if (nil? (:kohdistus-id laskurivi))
    (q/luo-laskun-kohdistus<! db {:lasku               lasku-id
                                  :id                  (:kohdistus-id laskurivi)
                                  :rivi                (:rivi laskurivi)
                                  :summa               (:summa laskurivi)
                                  :toimenpideinstanssi (:toimenpideinstanssi laskurivi)
                                  :tehtavaryhma        (:tehtavaryhma laskurivi)
                                  :maksueratyyppi      (laskuerittelyn-maksueratyyppi db (:tehtavaryhma laskurivi) (:tehtava laskurivi))
                                  :alkupvm             (:suoritus-alku laskurivi)
                                  :loppupvm            (:suoritus-loppu laskurivi)
                                  :kayttaja            (:id user)})
    (q/paivita-laskun-kohdistus<! db {:id                  (:kohdistus-id laskurivi)
                                      :summa               (:summa laskurivi)
                                      :toimenpideinstanssi (:toimenpideinstanssi laskurivi)
                                      :tehtavaryhma        (:tehtavaryhma laskurivi)
                                      :maksueratyyppi      (laskuerittelyn-maksueratyyppi db (:tehtavaryhma laskurivi) (:tehtava laskurivi))
                                      :alkupvm             (:suoritus-alku laskurivi)
                                      :loppupvm            (:suoritus-loppu laskurivi)
                                      :kayttaja            (:id user)}))
  (kust-q/merkitse-maksuerat-likaisiksi! db {:toimenpideinstanssi
                                             (:toimenpideinstanssi laskurivi)}))


(defn luo-tai-paivita-laskuerittely
  "Tallentaa uuden laskun ja siihen liittyvät kohdistustiedot (laskuerittelyn).
  Päivittää laskun tai kohdistuksen tiedot, jos rivi on jo kannassa.
  Palauttaa tallennetut tiedot."
  [db user urakka-id {:keys [erapaiva kokonaissumma urakka tyyppi suorittaja-nimi laskun-numero
                             lisatieto koontilaskun-kuukausi id kohdistukset] :as _laskuerittely}]
  (println "TYLI " kohdistukset)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [yhteiset-tiedot {:erapaiva              (konv/sql-date erapaiva)
                         :kokonaissumma         kokonaissumma
                         :urakka                urakka
                         :suorittaja            (when suorittaja-nimi (kasittele-suorittaja db user suorittaja-nimi))
                         :tyyppi                tyyppi
                         :numero                laskun-numero
                         :lisatieto             lisatieto
                         :kayttaja              (:id user)
                         :koontilaskun-kuukausi koontilaskun-kuukausi}
        lasku (if (nil? id)
                (q/luo-lasku<! db yhteiset-tiedot)
                (q/paivita-lasku<! db (assoc yhteiset-tiedot
                                        :id id)))]
    (doseq [kohdistusrivi kohdistukset]
      (println "KOHDISTUSRIVI" kohdistusrivi)
      (as-> kohdistusrivi r
            (update r :summa big/unwrap)
            (assoc r :lasku (:id lasku))
            (if (true? (:poistettu r))
              (q/poista-laskun-kohdistus! db {:id       id
                                              :urakka          urakka-id
                                              :kohdistuksen-id (:kohdistus-id r)
                                              :kayttaja        (:id user)})
              (luo-tai-paivita-laskun-kohdistus db
                                                user
                                                urakka
                                                (:id lasku)
                                                r))))
    (hae-laskuerittely db user {:id (:id lasku)})))

(defn poista-lasku
  "Merkitsee laskun sekä kaikki siihen liittyvät kohdistukset poistetuksi."
  [db user {:keys [urakka-id id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-lasku! db {:urakka    urakka-id
                       :id id
                       :kayttaja  (:id user)})
  (q/poista-laskun-kohdistukset! db {:urakka    urakka-id
                                     :id id
                                     :kayttaja  (:id user)})
  (hae-laskuerittely db user {:id id}))

(defn poista-laskun-kohdistus
  "Poistaa yksittäisen rivin laskuerittelystä (kohdistuksista). Palauttaa päivittyneen kantatilanteen."
  [db user {:keys [urakka-id id kohdistuksen-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-laskun-kohdistus! db {:id       id
                                  :urakka          urakka-id
                                  :kohdistuksen-id kohdistuksen-id
                                  :kayttaja        (:id user)})
  (hae-laskuerittely db user {:id id}))

(defn tallenna-lasku
  "Funktio tallentaa laskun ja laskuerittelyn (laskun kohdistuksen). Käytetään teiden hoidon urakoissa (MHU)."
  [db user {:keys [urakka-id laskuerittely]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)

  (luo-tai-paivita-laskuerittely db user urakka-id laskuerittely))


(defrecord Laskut []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :laskut
                        (fn [user hakuehdot]
                          (hae-urakan-laskut db user hakuehdot)))
      (julkaise-palvelu http :laskuerittelyt
                        (fn [user hakuehdot]
                          (hae-urakan-laskuerittelyt db user hakuehdot)))
      (julkaise-palvelu http :kaikki-laskuerittelyt
                        (fn [user hakuehdot]
                          (hae-kaikki-urakan-laskuerittelyt db user hakuehdot)))
      (julkaise-palvelu http :lasku
                        (fn [user hakuehdot]
                          (hae-laskuerittely db user hakuehdot)))
      (julkaise-palvelu http :tallenna-lasku
                        (fn [user laskuerittely]
                          (tallenna-lasku db user laskuerittely)))
      (julkaise-palvelu http :poista-lasku
                        (fn [user hakuehdot]
                          (poista-lasku db user hakuehdot)))
      (julkaise-palvelu http :poista-laskurivi
                        (fn [user hakuehdot]
                          (poista-laskun-kohdistus db user hakuehdot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :laskut)
    (poista-palvelut (:http-palvelin this) :lasku)
    (poista-palvelut (:http-palvelin this) :laskuerittelyt)
    (poista-palvelut (:http-palvelin this) :kaikki-laskuerittelyt)
    (poista-palvelut (:http-palvelin this) :tallenna-lasku)
    (poista-palvelut (:http-palvelin this) :poista-lasku)
    (poista-palvelut (:http-palvelin this) :poista-laskurivi)
    this))
