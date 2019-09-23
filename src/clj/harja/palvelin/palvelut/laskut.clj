(ns harja.palvelin.palvelut.laskut
  "Nimiavaruutta käytetään vain urakkatyypissä teiden-hoito (MHU)."
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt
             [laskut :as q]
             [aliurakoitsijat :as ali-q]]
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

(defn hae-urakan-laskuerittelyt
  "Palauttaa urakan laskut valitulta ajanjaksolta laskuerittelyineen."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (let [laskukohdistukset (group-by :laskun-id (q/hae-urakan-laskuerittelyt db {:urakka   (:urakka-id hakuehdot)
                                                                                :alkupvm  (:alkupvm hakuehdot)
                                                                                :loppupvm (:loppupvm hakuehdot)}))]
    (map #(into {} {:id            (first %)
                    :viite         (:viite (second %))
                    :tyyppi        (:tyyppi (second %))
                    :kokonaissumma (:kokonaissumma (second %))
                    :erapaiva      (:erapaiva (second %))
                    :liite-id      (:liite-id (second %))
                    :liite-nimi    (:liite-nimi (second %))
                    :liite-tyyppi  (:liite-tyyppi (second %))
                    :liite-koko    (:liite-koko (second %))
                    :liite-oid     (:liite-oid (second %))
                    :kohdistukset  (into []
                                         (map (fn [v]
                                                (dissoc v :viite
                                                        :tyyppi
                                                        :kokonaissumma
                                                        :erapaiva
                                                        :liite-id
                                                        :liite-nimi
                                                        :liite-tyyppi
                                                        :liite-koko
                                                        :liite-oid))
                                              (second %)))})
         laskukohdistukset)))

(defn hae-laskuerittely
  "Hakee yksittäisen laskun tiedot laskuerittelyineen."
  [db user {:keys [urakka-id viite]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [lasku (first (q/hae-lasku db {:urakka urakka-id
                                      :viite  viite}))
        laskun-kohdistukset (into [] (q/hae-laskun-kohdistukset db {:lasku (:id lasku)}))]
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
  (q/luo-tai-paivita-laskun-kohdistus<! db {:lasku               lasku-id
                                            :id                  (:kohdistus-id laskurivi)
                                            :rivi                (:rivi laskurivi)
                                            :summa               (:summa laskurivi)
                                            :toimenpideinstanssi (:toimenpideinstanssi laskurivi)
                                            :tehtavaryhma        (:tehtavaryhma laskurivi)
                                            :tehtava             (:tehtava laskurivi)
                                            :maksueratyyppi      (laskuerittelyn-maksueratyyppi db (:tehtava laskurivi) (:tehtava laskurivi))
                                            :suorittaja          (kasittele-suorittaja db user (:suorittaja-nimi laskurivi))
                                            :alkupvm             (:suoritus-alku laskurivi)
                                            :loppupvm            (:suoritus-loppu laskurivi)
                                            :kayttaja            (:id user)}))

(defn luo-tai-paivita-laskuerittely
  "Tallentaa uuden laskun ja siihen liittyvät kohdistustiedot (laskuerittelyn).
  Päivittää laskun tai kohdistuksen tiedot, jos rivi on jo kannassa.
  Palauttaa tallennetut tiedot."
  [db user urakka-id laskuerittely]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [lasku (q/luo-tai-paivita-lasku<! db {:viite         (:viite laskuerittely)
                                             :erapaiva      (konv/sql-date (:erapaiva laskuerittely))
                                             :kokonaissumma (:kokonaissumma laskuerittely)
                                             :urakka        (:urakka laskuerittely)
                                             :tyyppi        (:tyyppi laskuerittely)
                                             :kayttaja      (:id user)})]
    (doseq [kohdistusrivi (:kohdistukset laskuerittely)]
      (as-> kohdistusrivi r
            (update r :summa big/unwrap)
            (assoc r :lasku (:id lasku))
            (luo-tai-paivita-laskun-kohdistus db user
                                              (:urakka laskuerittely)
                                              (:id lasku)
                                              r))))
  (hae-laskuerittely db user {:urakka-id (:urakka laskuerittely)
                              :viite     (:viite laskuerittely)}))

(defn poista-lasku
  "Merkitsee laskun sekä kaikki siihen liittyvät kohdistukset poistetuksi."
  [db user {:keys [urakka-id viite]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-lasku! db {:urakka   urakka-id
                       :viite    viite
                       :kayttaja (:id user)})
  (q/poista-laskun-kohdistukset! db {:urakka   urakka-id
                                     :viite    viite
                                     :kayttaja (:id user)})
  (hae-laskuerittely db user {:urakka urakka-id
                              :viite  viite}))

(defn poista-laskun-kohdistus
  "Poistaa yksittäisen rivin laskuerittelystä (kohdistuksista). Palauttaa päivittyneen kantatilanteen."
  [db user {:keys [urakka-id laskun-viite laskuerittelyn-rivi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-laskun-kohdistus! db {:viite    laskun-viite
                                  :urakka   urakka-id
                                  :rivi     laskuerittelyn-rivi
                                  :kayttaja (:id user)})
  (hae-laskuerittely db user {:urakka-id urakka-id
                              :viite     laskun-viite}))

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
    (poista-palvelut (:http-palvelin this) :hae-laskut)
    (poista-palvelut (:http-palvelin this) :hae-lasku)
    (poista-palvelut (:http-palvelin this) :tallenna-lasku)
    (poista-palvelut (:http-palvelin this) :poista-lasku)
    (poista-palvelut (:http-palvelin this) :poista-laskurivi)
    this))
