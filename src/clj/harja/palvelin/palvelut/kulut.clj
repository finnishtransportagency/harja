(ns harja.palvelin.palvelut.kulut
  "Nimiavaruutta käytetään vain urakkatyypissä teiden-hoito (MHU)."
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as c]
            [clojure.string :as str]
            [harja.kyselyt
             [kulut :as q]
             [kustannusarvioidut-tyot :as kust-q]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.kulut :as kulut]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [harja.palvelin.palvelut.kulut.pdf :as kpdf]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konversio]))


(defn jarjesta-vuoden-ja-kuukauden-mukaan
  [pvm1 pvm2]
  (let [[vvvv1 kk1] (str/split pvm1 #"/")
        [vvvv2 kk2] (str/split pvm2 #"/")
        vvvv1 (Integer/parseInt vvvv1)
        vvvv2 (Integer/parseInt vvvv2)
        kk1 (Integer/parseInt kk1)
        kk2 (Integer/parseInt kk2)]
    (if (= vvvv1 vvvv2)
      (> kk1 kk2)
      (> vvvv1 vvvv2))))

(defn ryhmittele-urakan-kulut
  [uudet-rivit]
  (let [laske-kokonaissumma (fn [k [avain arvo]]
                              (update k avain
                                      (fn [m]
                                        (-> m
                                            (assoc :rivit arvo :summa
                                                   (reduce #(+ %1 (:summa %2)) 0 arvo))))))
        pvm-mukaan (reduce laske-kokonaissumma
                           {} (group-by #(pvm/kokovuosi-ja-kuukausi (:erapaiva %)) uudet-rivit))
        nro-mukaan 
        (mapv (fn [[paivamaara rivit-ja-summa]]
                [paivamaara (assoc 
                             rivit-ja-summa 
                             :rivit 
                             (reduce laske-kokonaissumma 
                                     {}
                                     (group-by #(or (:laskun-numero %)
                                                    0) 
                                               (:rivit rivit-ja-summa))))])
              pvm-mukaan)

        tpi-mukaan 
        (into [] 
              (sort-by
               first
               jarjesta-vuoden-ja-kuukauden-mukaan
               (mapv (fn [[paivamaara rivit-ja-summa]]
                       [paivamaara (assoc 
                                    rivit-ja-summa 
                                    :rivit 
                                    (into {} (mapv (fn [[laskun-nro rivit-ja-summa]]
                                                     [laskun-nro (assoc rivit-ja-summa :rivit (reduce laske-kokonaissumma {} (group-by :toimenpideinstanssi (:rivit rivit-ja-summa))))])
                                                   (:rivit rivit-ja-summa))))]
                       ) nro-mukaan)))]
    tpi-mukaan))

(defn lisaa-tpi-rivit
  [acc [tpi {rivit :rivit summa :summa}]]
  (conj acc [:tpi tpi summa rivit]))

(defn lisaa-laskun-nro-rivit
  [acc [laskun-nro {rivit :rivit summa :summa}]]
  (apply conj acc [:laskun-numero laskun-nro summa] (reduce lisaa-tpi-rivit [] rivit)))

(defn- lisaa-paivamaara-rivit
  [acc [paivamaara {rivit :rivit summa :summa}]]
  (apply conj acc [:pvm paivamaara summa] (reduce lisaa-laskun-nro-rivit [] rivit)))

(defn muodosta-naytettava-rakenne
  "Luodaan nestatusta rakenteesta flatti rakenne ja laitetaan sinne mukaan "
  [kulut-ja-kohdistukset]
  (reduce lisaa-paivamaara-rivit [] kulut-ja-kohdistukset))

(defn hae-urakan-kulut
  "Palauttaa urakan kulut valitulta ajanjaksolta ilman kohdistuksia."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (q/hae-urakan-kulut db {:urakka   (:urakka-id hakuehdot)
                           :alkupvm  (:alkupvm hakuehdot)
                           :loppupvm (:loppupvm hakuehdot)}))

(defn- testi-prosessointi
  []
  (let [toivottu [[:pvm "2020/10"]
                  [:laskun-numero 5]
                  [:tpi 3]
                  {:laskun-numero 5}]
        prosessoitavat [{:laskun-numero 5 :erapaiva (pvm/->pvm "10.10.2020")} {:laskun-numero 6  :erapaiva (pvm/->pvm "10.10.2020")} {:laskun-numero 5  :erapaiva (pvm/->pvm "10.10.2020")}]] 
    (= toivottu (ryhmittele-urakan-kulut prosessoitavat))))

(defn _____anonymo
  []
  (testi-prosessointi)
)

(defn kasittele-kohdistukset
  [db kulut-ja-kohdistukset] 
  (reduce
   (fn [acc [id kohdistukset]]
     (let [liitteet (into [] (q/hae-liitteet db {:kulu-id id}))]
       (apply conj acc (mapv #(assoc % :liitteet liitteet) kohdistukset))
       #_(into {} {:id                    id
                 :tyyppi                (:tyyppi kulu)
                 :kokonaissumma         (:kokonaissumma kulu)
                 :erapaiva              (:erapaiva kulu)
                 :laskun-numero         (:laskun-numero kulu)
                 :koontilaskun-kuukausi (:koontilaskun-kuukausi kulu)
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
   []
   kulut-ja-kohdistukset))

(defn hae-kulut-ja-kohd
[db hakuehdot]
  (let [kulukohdistukset (group-by :id (q/hae-urakan-kulut-kohdistuksineen db {:urakka   (:urakka-id hakuehdot)
                                                                               :alkupvm  (:alkupvm hakuehdot)
                                                                               :loppupvm (:loppupvm hakuehdot)}))
        kulukohdistukset (kasittele-kohdistukset db kulukohdistukset)
        kulukohdistukset (ryhmittele-urakan-kulut kulukohdistukset)
        kulukohdistukset (muodosta-naytettava-rakenne kulukohdistukset)]
    kulukohdistukset))

(defn hae-urakan-kulut-kohdistuksineen
  "Palauttaa urakan kulut valitulta ajanjaksolta kohdistuksineen."
  [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user (:urakka-id hakuehdot))
  (hae-kulut-ja-kohd db hakuehdot))

(defn hae-kulu-kohdistuksineen
  "Hakee yksittäisen kulun tiedot kohdistuksineen."
  [db user {:keys [urakka-id id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [kulu (first (q/hae-kulu db {:urakka urakka-id
                                    :id     id}))
        kulun-kohdistukset (into []
                                 (q/hae-kulun-kohdistukset db {:kulu (:id kulu)}))
        liitteet (into [] (q/hae-liitteet db {:kulu-id id}))]
    (if-not (empty? kulu)
      (assoc kulu :kohdistukset kulun-kohdistukset :liitteet liitteet)
      kulu)))

(defn- kohdistuksen-maksueratyyppi
  "Selvittää kulukohdistuksien maksueratyypin, jotta kulun summa lasketaan myöhemmin oikeaan Sampoon lähetettvään maksuerään.
  Yleensä tyyppi on kokonaishintainen. Jos tehtävä on Äkillinen hoitotyö, maksuerätyyppi on akillinen-hoitotyö.
  Jos tehtävä on "
  [db tehtavaryhma-id tehtava-id lisatyo?]
  ;;TODO: tarkista ehdot, korjaa
  (cond
    (or lisatyo?) "lisatyo"
    (or (.contains (or (:nimi (first (q/hae-tehtavan-nimi db {:id tehtava-id}))) "") "Äkilliset hoitotytöt")
        (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "ÄKILLISET HOITOTYÖT")
        (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "Äkilliset hoitotyöt,"))
    "akillinen-hoitotyo"
    (or (.contains (or (:nimi (first (q/hae-tehtavan-nimi db {:id tehtava-id}))) "") "vahinkojen korja")
        (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "VAHINKOJEN KORJAAMINEN")
        (.contains (or (:nimi (first (q/hae-tehtavaryhman-nimi db {:id tehtavaryhma-id}))) "") "Vahinkojen korjaukset,"))
    "muu"                                                   ;; vahinkojen korjaukset
    :default
    "kokonaishintainen"))

(defn luo-tai-paivita-kulun-kohdistus
  "Luo uuden kohdistuksen kantaan tai päivittää olemassa olevan rivin. Rivi tunnistetaan kulun viitteen ja rivinumeron perusteella."
  [db user urakka-id kulu-id kohdistus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [yhteiset {:id                  (:kohdistus-id kohdistus)
                  :summa               (:summa kohdistus)
                  :toimenpideinstanssi (:toimenpideinstanssi kohdistus)
                  :tehtavaryhma        (:tehtavaryhma kohdistus)
                  :maksueratyyppi      (kohdistuksen-maksueratyyppi db (:tehtavaryhma kohdistus) (:tehtava kohdistus) (:lisatyo? kohdistus))
                  :alkupvm             (:suoritus-alku kohdistus)
                  :loppupvm            (:suoritus-loppu kohdistus)
                  :kayttaja            (:id user)
                  :lisatyon-lisatieto  (:lisatyon-lisatieto kohdistus)}]
    (if (nil? (:kohdistus-id kohdistus))
      (q/luo-kulun-kohdistus<! db (assoc yhteiset :kulu kulu-id
                                         :rivi (:rivi kohdistus)))
      (q/paivita-kulun-kohdistus<! db yhteiset)))
  (kust-q/merkitse-maksuerat-likaisiksi! db {:toimenpideinstanssi
                                             (:toimenpideinstanssi kohdistus)}))

(defn- tarkista-laskun-numeron-paivamaara [db user {:keys [urakka] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka)
  (let [erapaivat (q/hae-pvm-laskun-numerolla db hakuehdot)]
    (if (empty? erapaivat)
      false
      (first erapaivat))))

(defn- varmista-erapaiva-on-koontilaskun-kuukauden-sisalla [db koontilaskun-kuukausi erapaiva urakka-id]
  (let [{alkupvm :alkupvm loppupvm :loppupvm} (urakat/urakan-paivamaarat db urakka-id)
        sisalla?-fn (kulut/koontilaskun-kuukauden-sisalla?-fn koontilaskun-kuukausi
                                                              (pvm/joda-timeksi alkupvm)
                                                              (pvm/joda-timeksi loppupvm))]
    (when-not (sisalla?-fn (pvm/suomen-aikavyohykkeeseen (pvm/joda-timeksi erapaiva)))
      (throw (IllegalArgumentException.
              (str "Eräpäivä " erapaiva " ei ole koontilaskun-kuukauden " koontilaskun-kuukausi
                   " sisällä. Urakka id = " urakka-id))))))

(defn poista-kulun-kohdistus
  "Poistaa yksittäisen rivin kulun kohdistuksista. Palauttaa päivittyneen kantatilanteen."
  [db user {:keys [urakka-id id kohdistuksen-id kohdistus]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-kulun-kohdistus! db {:id              id
                                  :urakka          urakka-id
                                  :kohdistuksen-id kohdistuksen-id
                                  :kayttaja        (:id user)})
  (kust-q/merkitse-maksuerat-likaisiksi! db {:toimenpideinstanssi
                                             (:toimenpideinstanssi kohdistus)})
  (hae-kulu-kohdistuksineen db user {:id id}))

(defn luo-tai-paivita-kulukohdistukset
  "Tallentaa uuden kulun ja siihen liittyvät kohdistustiedot.
  Päivittää kulun tai kohdistuksen tiedot, jos rivi on jo kannassa.
  Palauttaa tallennetut tiedot."
  [db user urakka-id {:keys [erapaiva kokonaissumma urakka tyyppi laskun-numero
                             lisatieto koontilaskun-kuukausi id kohdistukset liitteet]}]
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
        kulu (if (nil? id)
                (q/luo-kulu<! db yhteiset-tiedot)
                (q/paivita-kulu<! db (assoc yhteiset-tiedot
                                        :id id)))]
    (when-not (or (nil? liitteet)
                  (empty? liitteet))
      (doseq [liite liitteet]
        (q/linkita-kulu-ja-liite<! db {:kulu-id (:id kulu)
                                        :liite-id (:liite-id liite)
                                        :kayttaja (:id user)})))
    (doseq [kohdistusrivi kohdistukset]
      (as-> kohdistusrivi r
            (update r :summa big/unwrap)
            (assoc r :kulu (:id kulu))
            (if (true? (:poistettu r))
              (poista-kulun-kohdistus db user {:id              id
                                                :urakka-id          urakka-id
                                                :kohdistuksen-id (:kohdistus-id r)
                                                :kohdistus r})
              (luo-tai-paivita-kulun-kohdistus db
                                                user
                                                urakka
                                                (:id kulu)
                                                r))))
    (hae-kulu-kohdistuksineen db user {:id (:id kulu)})))

(defn poista-kulu
  "Merkitsee kulun sekä kaikki siihen liittyvät kohdistukset poistetuksi."
  [db user {:keys [urakka-id id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [liitteet (into [] (q/hae-liitteet db {:kulu-id id}))
        poistettu-kulu (hae-kulu-kohdistuksineen db user {:id id})]
    (when (not (empty? liitteet))
      (doseq [{liite-id :liite-id} liitteet]
        (q/poista-kulun-ja-liitteen-linkitys! db {:kulu-id id :liite-id liite-id :kayttaja (:id user)})))
    (q/poista-kulu! db {:urakka   urakka-id
                         :id       id
                         :kayttaja (:id user)})
    (q/poista-kulun-kohdistukset! db {:urakka   urakka-id
                                       :id       id
                                       :kayttaja (:id user)})
    (kust-q/merkitse-maksuerat-likaisiksi! db {:toimenpideinstanssi
                                               (:toimenpideinstanssi poistettu-kulu)})
    poistettu-kulu))

(defn tallenna-kulu
  "Funktio tallentaa kulun kohdistuksineen. Käytetään teiden hoidon urakoissa (MHU)."
  [db user {:keys [urakka-id kulu-kohdistuksineen]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (luo-tai-paivita-kulukohdistukset db user urakka-id kulu-kohdistuksineen))

(defn- poista-kulun-liite
  "Merkkaa kulun liitteen poistetuksi"
  [db user {:keys [urakka-id kulu-id liite-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-kulun-ja-liitteen-linkitys! db {:kulu-id kulu-id :liite-id liite-id :kayttaja (:id user)})
  (hae-kulu-kohdistuksineen db user {:id kulu-id}))

(defn- kulu-pdf
  [db user {:keys [urakka-id urakka-nimi alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [alkupvm (or alkupvm
                    (pvm/->pvm "01.01.1990"))
        loppupvm (or loppupvm
                     (pvm/nyt))
        kulut (q/hae-kulut-kohdistuksineen-tietoineen-vientiin db {:urakka   urakka-id
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
                       (q/hae-kulut-kohdistuksineen-tietoineen-vientiin db {:urakka   urakka-id
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

(defrecord Kulut []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)
          pdf (:pdf-vienti this)
          excel (:excel-vienti this)]
      (julkaise-palvelu http :kulut
                        (fn [user hakuehdot]
                          (hae-urakan-kulut db user hakuehdot)))
      (julkaise-palvelu http :kulut-kohdistuksineen
                        (fn [user hakuehdot]
                          (hae-urakan-kulut-kohdistuksineen db user hakuehdot)))
      (julkaise-palvelu http :kulu
                        (fn [user hakuehdot]
                          (hae-kulu-kohdistuksineen db user hakuehdot)))
      (julkaise-palvelu http :tallenna-kulu
                        (fn [user kulu-kohdistuksineen]
                          (tallenna-kulu db user kulu-kohdistuksineen))
                        {:kysely-spec ::kulut/talenna-kulu})
      (julkaise-palvelu http :poista-kulu
                        (fn [user hakuehdot]
                          (poista-kulu db user hakuehdot)))
      (julkaise-palvelu http :poista-kohdistus
                        (fn [user hakuehdot]
                          (poista-kulun-kohdistus db user hakuehdot)))
      (julkaise-palvelu http :poista-kulun-liite
                        (fn [user hakuehdot]
                          (poista-kulun-liite db user hakuehdot)))
      (julkaise-palvelu http :tarkista-laskun-numeron-paivamaara
                        (fn [user hakuehdot]
                          (tarkista-laskun-numeron-paivamaara db user hakuehdot)))
      (when pdf
        (pdf-vienti/rekisteroi-pdf-kasittelija! pdf :kulut (partial #'kulu-pdf db)))
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :kulut (partial #'kulu-excel db)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :kulut
                     :kulu
                     :kulut-kohdistuksineen
                     :tallenna-kulu
                     :poista-kulu
                     :poista-kohdistus
                     :poista-kulun-liite
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
