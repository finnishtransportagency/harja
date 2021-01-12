(ns harja.palvelin.palvelut.laskut
  "Nimiavaruutta käytetään vain urakkatyypissä teiden-hoito (MHU)."
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as c]
            [harja.kyselyt
             [laskut :as q]
             [aliurakoitsijat :as ali-q]
             [kustannusarvioidut-tyot :as kust-q]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.lasku :as lasku]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [harja.palvelin.palvelut.kulut.pdf :as kpdf]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konversio]))


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
  [db laskukohdistukset]
  (map
    (fn [[id kohdistukset]]
      (let [lasku (first kohdistukset)
            liitteet (into [] (q/hae-liitteet db {:lasku-id id}))]
        (into {} {:id                    id
                  :tyyppi                (:tyyppi lasku)
                  :kokonaissumma         (:kokonaissumma lasku)
                  :erapaiva              (:erapaiva lasku)
                  :laskun-numero         (:laskun-numero lasku)
                  :koontilaskun-kuukausi (:koontilaskun-kuukausi lasku)
                  :liitteet              liitteet
                  :kohdistukset          (mapv #(dissoc %
                                                        :tyyppi
                                                        :kokonaissumma
                                                        :erapaiva
                                                        :suorittaja-id
                                                        :id
                                                        :liitteet
                                                        :koontilaskun-kuukausi)
                                               kohdistukset)})))
    laskukohdistukset))

(defn hae-kaikki-urakan-laskuerittelyt
  "Palauttaa urakan laskut laskuerittelyineen."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (let [laskukohdistukset (group-by :id (q/hae-kaikki-urakan-laskuerittelyt db {:urakka (:urakka-id hakuehdot)}))]
    (kasittele-kohdistukset db laskukohdistukset)))

(defn hae-urakan-laskuerittelyt
  "Palauttaa urakan laskut valitulta ajanjaksolta laskuerittelyineen."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (let [laskukohdistukset (group-by :id (q/hae-urakan-laskuerittelyt db {:urakka   (:urakka-id hakuehdot)
                                                                         :alkupvm  (:alkupvm hakuehdot)
                                                                         :loppupvm (:loppupvm hakuehdot)}))]
    (kasittele-kohdistukset db laskukohdistukset)))

(defn hae-laskuerittely
  "Hakee yksittäisen laskun tiedot laskuerittelyineen."
  [db user {:keys [urakka-id id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [lasku (first (q/hae-lasku db {:urakka urakka-id
                                      :id     id}))
        laskun-kohdistukset (into []
                                  (q/hae-laskun-kohdistukset db {:lasku (:id lasku)}))
        liitteet (into [] (q/hae-liitteet db {:lasku-id id}))]
    (if-not (empty? lasku)
      (assoc lasku :kohdistukset laskun-kohdistukset :liitteet liitteet)
      lasku)))

(defn- laskuerittelyn-maksueratyyppi
  "Selvittää laskuerittelyn maksueratyypin, jotta laskun summa lasketaan myöhemmin oikeaan Sampoon lähetettvään maksuerään.
  Yleensä tyyppi on kokonaishintainen. Jos tehtävä on Äkillinen hoitotyö, maksuerätyyppi on akillinen-hoitotyö.
  Jos tehtävä on "
  [db tehtavaryhma-id tehtava-id lisatyo?]
  ;;TODO: tarkista ehdot, korjaa
  (cond
    (or lisatyo?) "lisatyo"
    (or (.contains (or (:nimi (first (q/hae-tehtavan-nimi db {:id tehtava-id}))) "") "Äkilliset hoitotytöt")
        (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "ÄKILLISET HOITOTYÖT"))
    "akilliset-hoitotyot"
    (or (.contains (or (:nimi (first (q/hae-tehtavan-nimi db {:id tehtava-id}))) "") "vahinkojen korja")
        (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "VAHINKOJEN KORJAAMINEN"))
    "muu"                                                   ;; vahinkojen korjaukset
    :default
    "kokonaishintainen"))

(defn luo-tai-paivita-laskun-kohdistus
  "Luo uuden laskuerittelyrivin (kohdistuksen) kantaan tai päivittää olemassa olevan rivin. Rivi tunnistetaan laskun viitteen ja rivinumeron perusteella."
  [db user urakka-id lasku-id laskurivi]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [yhteiset {:id                  (:kohdistus-id laskurivi)
                  :summa               (:summa laskurivi)
                  :toimenpideinstanssi (:toimenpideinstanssi laskurivi)
                  :tehtavaryhma        (:tehtavaryhma laskurivi)
                  :maksueratyyppi      (laskuerittelyn-maksueratyyppi db (:tehtavaryhma laskurivi) (:tehtava laskurivi) (:lisatyo? laskurivi))
                  :alkupvm             (:suoritus-alku laskurivi)
                  :loppupvm            (:suoritus-loppu laskurivi)
                  :kayttaja            (:id user)
                  :lisatyon-lisatieto  (:lisatyon-lisatieto laskurivi)}]
    (if (nil? (:kohdistus-id laskurivi))
      (q/luo-laskun-kohdistus<! db (assoc yhteiset :lasku lasku-id
                                                   :rivi (:rivi laskurivi)))
      (q/paivita-laskun-kohdistus<! db yhteiset)))
  (kust-q/merkitse-maksuerat-likaisiksi! db {:toimenpideinstanssi
                                             (:toimenpideinstanssi laskurivi)}))

(defn- tarkista-laskun-numeron-paivamaara [db user {:keys [urakka] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka)
  (let [erapaivat (q/hae-pvm-laskun-numerolla db hakuehdot)]
    (if (empty? erapaivat)
      false
      (first erapaivat))))

(defn- varmista-erapaiva-on-koontilaskun-kuukauden-sisalla [db koontilaskun-kuukausi erapaiva urakka-id]
  (let [{alkupvm :alkupvm loppupvm :loppupvm} (urakat/urakan-paivamaarat db urakka-id)
        sisalla?-fn (lasku/koontilaskun-kuukauden-sisalla?-fn koontilaskun-kuukausi
                                                              (pvm/joda-timeksi alkupvm)
                                                              (pvm/joda-timeksi loppupvm))]
    (when-not (sisalla?-fn (pvm/suomen-aikavyohykkeeseen (pvm/joda-timeksi erapaiva)))
      (throw (IllegalArgumentException.
               (str "Eräpäivä " erapaiva " ei ole koontilaskun-kuukauden " koontilaskun-kuukausi
                    " sisällä. Urakka id = " urakka-id))))))

(defn luo-tai-paivita-laskuerittely
  "Tallentaa uuden laskun ja siihen liittyvät kohdistustiedot (laskuerittelyn).
  Päivittää laskun tai kohdistuksen tiedot, jos rivi on jo kannassa.
  Palauttaa tallennetut tiedot."
  [db user urakka-id {:keys [erapaiva kokonaissumma urakka tyyppi laskun-numero
                             lisatieto koontilaskun-kuukausi id kohdistukset liitteet] :as _laskuerittely}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (varmista-erapaiva-on-koontilaskun-kuukauden-sisalla db koontilaskun-kuukausi erapaiva urakka-id)
  (let [tarkistettu-erapaiva (tarkista-laskun-numeron-paivamaara db user {:urakka urakka :laskun-numero laskun-numero})
        erapaiva (if (false? tarkistettu-erapaiva)
                   erapaiva
                   (:erapaiva tarkistettu-erapaiva))
        yhteiset-tiedot {:erapaiva              (konv/sql-date erapaiva)
                         :kokonaissumma         kokonaissumma
                         :urakka                urakka
                         :tyyppi                tyyppi
                         :numero                laskun-numero
                         :lisatieto             lisatieto
                         :kayttaja              (:id user)
                         :koontilaskun-kuukausi koontilaskun-kuukausi}
        lasku (if (nil? id)
                (q/luo-lasku<! db yhteiset-tiedot)
                (q/paivita-lasku<! db (assoc yhteiset-tiedot
                                        :id id)))]
    (when-not (or (nil? liitteet)
                  (empty? liitteet))
      (doseq [liite liitteet]
        (q/linkita-lasku-ja-liite<! db {:lasku-id (:id lasku)
                                        :liite-id (:liite-id liite)
                                        :kayttaja (:id user)})))
    (doseq [kohdistusrivi kohdistukset]
      (as-> kohdistusrivi r
            (update r :summa big/unwrap)
            (assoc r :lasku (:id lasku))
            (if (true? (:poistettu r))
              (q/poista-laskun-kohdistus! db {:id              id
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
  (let [liitteet (into [] (q/hae-liitteet db {:lasku-id id}))
        poistettu-lasku (hae-laskuerittely db user {:id id})]
    (when (not (empty? liitteet))
      (doseq [{liite-id :liite-id} liitteet]
        (q/poista-laskun-ja-liitteen-linkitys! db {:lasku-id id :liite-id liite-id :kayttaja (:id user)})))
    (q/poista-lasku! db {:urakka   urakka-id
                         :id       id
                         :kayttaja (:id user)})
    (q/poista-laskun-kohdistukset! db {:urakka   urakka-id
                                       :id       id
                                       :kayttaja (:id user)})
    poistettu-lasku))

(defn poista-laskun-kohdistus
  "Poistaa yksittäisen rivin laskuerittelystä (kohdistuksista). Palauttaa päivittyneen kantatilanteen."
  [db user {:keys [urakka-id id kohdistuksen-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-laskun-kohdistus! db {:id              id
                                  :urakka          urakka-id
                                  :kohdistuksen-id kohdistuksen-id
                                  :kayttaja        (:id user)})
  (hae-laskuerittely db user {:id id}))

(defn tallenna-lasku
  "Funktio tallentaa laskun ja laskuerittelyn (laskun kohdistuksen). Käytetään teiden hoidon urakoissa (MHU)."
  [db user {:keys [urakka-id laskuerittely]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (luo-tai-paivita-laskuerittely db user urakka-id laskuerittely))

(defn- poista-laskun-liite
  "Merkkaa laskun liitteen poistetuksi"
  [db user {:keys [urakka-id lasku-id liite-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-laskun-ja-liitteen-linkitys! db {:lasku-id lasku-id :liite-id liite-id :kayttaja (:id user)})
  (hae-laskuerittely db user {:id lasku-id}))

(defn- kulu-pdf
  [db user {:keys [urakka-id urakka-nimi alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [alkupvm (or alkupvm
                    (pvm/->pvm "01.01.1990"))
        loppupvm (or loppupvm
                     (pvm/nyt))
        kulut (q/hae-laskuerittelyt-tietoineen-vientiin db {:urakka   urakka-id
                                                            :alkupvm  (konversio/sql-timestamp alkupvm)
                                                            :loppupvm (konversio/sql-timestamp loppupvm)})
        kulut-kuukausien-mukaan (group-by #(pvm/kokovuosi-ja-kuukausi (:erapaiva %))
                                          (sort-by :erapaiva
                                                   kulut))]
    (kpdf/kulu-pdf urakka-nimi
                   (pvm/pvm alkupvm)
                   (pvm/pvm loppupvm)
                   kulut-kuukausien-mukaan)))

(defn- kulu-excel
  [db workbook user {:keys [urakka-id urakka-nimi alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [alkupvm (or alkupvm
                    (pvm/->pvm "01.01.1990"))
        loppupvm (or loppupvm
                     (pvm/nyt))
        kulut (sort-by :erapaiva
                       (q/hae-laskuerittelyt-tietoineen-vientiin db {:urakka   urakka-id
                                                                     :alkupvm  (konversio/sql-timestamp alkupvm)
                                                                     :loppupvm (konversio/sql-timestamp loppupvm)}))
        kulut-kuukausien-mukaan (group-by #(pvm/kokovuosi-ja-kuukausi (:erapaiva %))
                                          (sort-by :erapaiva
                                                   kulut))
        luo-sarakkeet (fn [& otsikot]
                        (mapv #(hash-map :otsikko %) otsikot))
        optiot {:nimi  urakka-nimi
                :tyhja (if (empty? kulut) "Ei kuluja valitulla aikavälillä.")}
        sarakkeet (luo-sarakkeet "Eräpäivä" "Toimenpide" "Tehtäväryhmä" "Maksuerä" "Summa")
        luo-rivi (fn [rivi] [(-> rivi
                                 :erapaiva
                                 pvm/pvm
                                 str)
                             (:toimenpide rivi)
                             (or (:tehtavaryhma rivi)
                                 "Lisätyö")
                             (str "HA" (:maksuera rivi))
                             [:arvo-ja-yksikko {:arvo (:summa rivi) :yksikko "€" :fmt? false}]])
        luo-data (fn [kaikki [vuosi-kuukausi rivit]]
                   (conj kaikki
                         [:teksti (str vuosi-kuukausi)]
                         [:otsikko (str vuosi-kuukausi)]
                         [:taulukko optiot sarakkeet (mapv luo-rivi rivit)]))
        taulukot (reduce luo-data [] kulut-kuukausien-mukaan)
        taulukko (concat
                   [:raportti {:nimi        (str urakka-nimi "_" (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))
                               :orientaatio :landscape}]
                   (if (empty? taulukot)
                       [[:taulukko optiot (luo-sarakkeet (str urakka-nimi "_" (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))) [["Ei kuluja valitulla aikavälillä"]]]]
                       taulukot))]
    (excel/muodosta-excel (vec taulukko)
                          workbook)))

(defn- luo-pdf
  [pdf user hakuehdot]
  (let [{:keys [tiedosto-bytet tiedostonimi]} (pdf-vienti/luo-pdf pdf :kulut user hakuehdot)]
    tiedosto-bytet))

(defrecord Laskut []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)
          pdf (:pdf-vienti this)
          excel (:excel-vienti this)]
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
                          (tallenna-lasku db user laskuerittely))
                        {:kysely-spec ::lasku/talenna-lasku})
      (julkaise-palvelu http :poista-lasku
                        (fn [user hakuehdot]
                          (poista-lasku db user hakuehdot)))
      (julkaise-palvelu http :poista-laskurivi
                        (fn [user hakuehdot]
                          (poista-laskun-kohdistus db user hakuehdot)))
      (julkaise-palvelu http :poista-laskun-liite
                        (fn [user hakuehdot]
                          (poista-laskun-liite db user hakuehdot)))
      (julkaise-palvelu http :tarkista-laskun-numeron-paivamaara
                        (fn [user hakuehdot]
                          (tarkista-laskun-numeron-paivamaara db user hakuehdot)))
      (when pdf
        (pdf-vienti/rekisteroi-pdf-kasittelija! pdf :kulut (partial #'kulu-pdf db)))
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :kulut (partial #'kulu-excel db)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :laskut
                     :lasku
                     :laskuerittelyt
                     :kaikki-laskuerittelyt
                     :tallenna-lasku
                     :poista-lasku
                     :poista-laskurivi
                     :poista-laskun-liite
                     :tarkista-laskun-numeron-paivamaara)
    (when (:pdf-vienti this)
      (pdf-vienti/poista-pdf-kasittelija! (:pdf-vienti this) :kulut))
    (when (:excel-vienti this)
      (excel-vienti/poista-excel-kasittelija! (:excel-vienti this) :kulut))
    this))

;[:raportti
; {:nimi Oulun MHU 2019-2024_Mon Jan 01 00:00:00 EET 1990-Wed May 06 09:51:32 EEST 2020, :orientaatio :landscape}
; [[:taulukko
;   {:sheet-nimi Oulun MHU 2019-2024, :otsikko Oulun MHU 2019-2024}
;   [{:otsikko Eräpäivä}
;    {:otsikko Toimenpide}
;    {:otsikko Tehtäväryhmä}
;    {:otsikko Maksuerä}
;    {:otsikko Summa}]
;   ([2019/09 blaa blaa blaa blaa]
;     [15.09.2019 Oulu MHU Talvihoito TP Talvihoito (A) HA69 [:arvo-ja-yksikko {:arvo 3666.66M, :yksikko €, :fmt? false}]])]
;  [:taulukko
;   {:sheet-nimi Oulun MHU 2019-2024, :otsikko Oulun MHU 2019-2024}
;   [{:otsikko Eräpäivä} {:otsikko Toimenpide} {:otsikko Tehtäväryhmä} {:otsikko Maksuerä} {:otsikko Summa}]
;   ([2019/10 blaa blaa blaa blaa] [15.10.2019 Oulu MHU Liikenneympäristön hoito TP Äkilliset hoitotyöt, Liikenneympäristön hoito (T1) HA70 [:arvo-ja-yksikko {:arvo 4444.44M, :yksikko €, :fmt? false}]] [15.10.2019 Oulu MHU Liikenneympäristön hoito TP Rummut, päällystetiet (R) HA70 [:arvo-ja-yksikko {:arvo 2222.22M, :yksikko €, :fmt? false}]] [15.10.2019 Oulu MHU Liikenneympäristön hoito TP Puhtaanapito (P) HA70 [:arvo-ja-yksikko {:arvo 111.11M, :yksikko €, :fmt? false}]] [15.10.2019 Oulu MHU Liikenneympäristön hoito TP Nurmetukset ja muut vihertyöt (N) HA70 [:arvo-ja-yksikko {:arvo 222.22M, :yksikko €, :fmt? false}]] [15.10.2019 Oulu MHU Liikenneympäristön hoito TP Vesakonraivaukset ja puun poisto (V) HA70 [:arvo-ja-yksikko {:arvo 333.33M, :yksikko €, :fmt? false}]])]
;  [:taulukko
;   {:sheet-nimi Oulun MHU 2019-2024, :otsikko Oulun MHU 2019-2024}
;   [{:otsikko Eräpäivä} {:otsikko Toimenpide} {:otsikko Tehtäväryhmä} {:otsikko Maksuerä} {:otsikko Summa}]
;   ([2020/04 blaa blaa blaa blaa]
;     [08.04.2020 Oulu MHU Talvihoito TP KFo, NaFo (B2) HA69 [:arvo-ja-yksikko {:arvo 22222M, :yksikko €, :fmt? false}]]
;     [23.04.2020 Oulu MHU MHU Ylläpito TP Lisätyö HA74 [:arvo-ja-yksikko {:arvo 12M, :yksikko €, :fmt? false}]])]]]