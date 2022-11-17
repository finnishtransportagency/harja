(ns harja.palvelin.palvelut.kulut
  "Nimiavaruutta käytetään vain urakkatyypissä teiden-hoito (MHU)."
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.kyselyt
             [kulut :as q]
             [kustannusarvioidut-tyot :as kust-q]
             [valikatselmus :as valikatselmus-kyselyt]
             [urakat :as urakka-kyselyt]]
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
  (let [[vvvv1 kk1] (str/split (or pvm1 "0/0") #"/")
        [vvvv2 kk2] (str/split (or pvm2 "0/0") #"/")
        vvvv1 (Integer/parseInt vvvv1)
        vvvv2 (Integer/parseInt vvvv2)
        kk1 (Integer/parseInt kk1)
        kk2 (Integer/parseInt kk2)]
    (if (= vvvv1 vvvv2)
      (> kk1 kk2)
      (> vvvv1 vvvv2))))

(defn jarjesta-koko-pvm-mukaan
  [pvm1 pvm2]
  (pvm/jalkeen? (pvm/->pvm pvm1) (pvm/->pvm pvm2)))

(defn- laske-summat [k rivi]
  (+ k (or (:summa rivi) 0)))

(defn- laske-summat-nro-ja-pvm-tasolle 
  [k [_ {:keys [summa]}]]
  (+ k summa))

(defn- erapaiva-pvm-stringina
  [rivi]
  (when (:erapaiva rivi) (-> rivi :erapaiva pvm/pvm)))

(defn- ota-erapaiva
  "ryhmittely luo rivejä mallia esim. [[tpi-id kulu-erapaiva] rivit], sorttausta varten. ne sitten siivotaan myöhemmin poies."
  [tiedot]
  (-> tiedot first second))

(defn- poista-erapaiva
  [[tagi tiedot]]
  [(first tagi) tiedot])

(defn- jarjesta-laskun-nro-0-tai-pvm-mukaan 
  "Laskun numero 0 on, jos laskun numeroa ei ole määritelty, joten ne tulevat erikseen aina pohjalle"
  [rivi1 rivi2]
  (cond 
    (and (= 0 (first rivi1))
         (not= 0 (first rivi2)))
    false

    (and (= 0 (first rivi2))
         (not= 0 (first rivi1)))
    true
    
    :else
    (jarjesta-koko-pvm-mukaan (second rivi1) (second rivi2))))

(defn- kasittele-toimenpideinstanssi-ryhmitellyt-rivit 
  [[tpi rivit]]
  [tpi {:rivit rivit 
        :summa (reduce laske-summat 0 rivit)}])

(defn- kasittele-laskun-nro-ryhmitellyt-rivit [[laskun-nro rivit]]
  (let [kasitellyt (mapv kasittele-toimenpideinstanssi-ryhmitellyt-rivit 
                         (group-by (juxt :toimenpideinstanssi 
                                         erapaiva-pvm-stringina) 
                                   rivit))
        kasitellyt (sort-by ota-erapaiva jarjesta-koko-pvm-mukaan kasitellyt)
        kasitellyt (mapv poista-erapaiva kasitellyt)] 
    [[laskun-nro (-> rivit first erapaiva-pvm-stringina)] 
     {:rivit kasitellyt
      :summa (reduce 
              laske-summat-nro-ja-pvm-tasolle
              0
              kasitellyt)}]))

(defn- kasittele-vuoden-ja-kuukauden-mukaan-ryhmitellyt-rivit [[paivamaara rivit]]
  (let [kasitellyt
        (sort-by first
                 jarjesta-laskun-nro-0-tai-pvm-mukaan
                 (mapv kasittele-laskun-nro-ryhmitellyt-rivit
                       (group-by #(or (:laskun-numero %)
                                      0)
                                 rivit)))
        kasitellyt (mapv poista-erapaiva
                         kasitellyt)] 
    [paivamaara 
     {:rivit kasitellyt 
      :summa (reduce 
              laske-summat-nro-ja-pvm-tasolle
              0
              kasitellyt)} ]))

(defn ryhmittele-urakan-kulut
  "Kulutaulusta tulevat tiedot ryhmitellään VVVV/kk mukaan, laskun numeron mukaan ja viimeisenä toimenpideinstanssin mukaan"
  [uudet-rivit]
  (into [] 
        (sort-by first jarjesta-vuoden-ja-kuukauden-mukaan 
                 (mapv kasittele-vuoden-ja-kuukauden-mukaan-ryhmitellyt-rivit 
                       (group-by #(when (:erapaiva %) (pvm/kokovuosi-ja-kuukausi (:erapaiva %))) uudet-rivit)))))

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

(defn kasittele-kohdistukset
  [db kulut-ja-kohdistukset] 
  (reduce
   (fn [acc [id kohdistukset]]
     (let [liitteet (into [] (q/hae-liitteet db {:kulu-id id}))]
       (apply conj acc (mapv #(assoc % :liitteet liitteet) kohdistukset))))
   []
   kulut-ja-kohdistukset))

(defn hae-kulut-kohdistuksineen
  "Helpottaa REPL-käyttöä, niin siksi tämä eriytetty (ei tarvi keksiä useria)"
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
  (hae-kulut-kohdistuksineen db hakuehdot))

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

(defn tarkista-saako-kulua-tallentaa
  "Kuluja voi lisätä ja muokata vain siihen asti, että hoitokauden välikatselmus on pidetty. Tarkistetaan siis
  hoitokauden päätökset (välikatselmointi tehty) ja mikäli niitä löytyy, niin estetään tallennus."
  [db urakka-id erapaiva vanha-erapaiva]
  (let [joda-local-time-erapaiva (pvm/ajan-muokkaus (pvm/joda-timeksi erapaiva) true 1 :paiva)
        joda-local-time-vanha-erapaiva (when vanha-erapaiva
                                         (pvm/ajan-muokkaus (pvm/joda-timeksi vanha-erapaiva) true 1 :paiva))
        erapaivan-vuosi (pvm/hoitokauden-alkuvuosi joda-local-time-erapaiva)
        vanhan-erapaivan-vuosi (when vanha-erapaiva
                                 (pvm/hoitokauden-alkuvuosi joda-local-time-vanha-erapaiva))
        valikatselmus-pidetty? (valikatselmus-kyselyt/onko-valikatselmus-pidetty? db {:urakka-id urakka-id
                                                                                      :vuodet [erapaivan-vuosi vanhan-erapaivan-vuosi]})]
    ;; Muutetaan negaatioksi, koska kysymyksen asettelu
    (not valikatselmus-pidetty?)))

(defn luo-tai-paivita-kulukohdistukset
  "Tallentaa uuden kulun ja siihen liittyvät kohdistustiedot.
  Päivittää kulun tai kohdistuksen tiedot, jos rivi on jo kannassa.
  Palauttaa tallennetut tiedot."
  [db user urakka-id {:keys [erapaiva kokonaissumma urakka tyyppi laskun-numero
                             lisatieto koontilaskun-kuukausi id kohdistukset liitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (varmista-erapaiva-on-koontilaskun-kuukauden-sisalla db koontilaskun-kuukausi erapaiva urakka-id)
  (let [vanha-erapaiva (when id (:erapaiva (first (q/hae-kulu db {:id id}))))
        saako-tallentaa (tarkista-saako-kulua-tallentaa db urakka-id erapaiva vanha-erapaiva)
        _ (when (not saako-tallentaa)
            (throw (IllegalArgumentException.
                     (str "Kulu on tai kohdistuu hoitokaudelle, jonka välikatselmus on jo pidetty. Tallentaminen ei enää onnistu!"))))
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
                                        :id id)))
        vanhat-kohdistukset (q/hae-kulun-kohdistukset db {:kulu (:id kulu)})
        sisaan-tulevat-kohdistus-idt (into #{} (map :kohdistus-id kohdistukset))
        puuttuvat-kohdistukset (remove
                                 #(sisaan-tulevat-kohdistus-idt (:kohdistus-id %))
                                 vanhat-kohdistukset)]
    (when-not (or (nil? liitteet)
                  (empty? liitteet))
      (doseq [liite liitteet]
        (q/linkita-kulu-ja-liite<! db {:kulu-id (:id kulu)
                                        :liite-id (:liite-id liite)
                                        :kayttaja (:id user)})))

    ;; Kannassa on kohdistuksia, joita ei lähetetty kulun päivityksen yhteydessä. Poistetaan ne.
    (doseq [puuttuva-kohdistus puuttuvat-kohdistukset]
        (poista-kulun-kohdistus db user
          {:id id
           :urakka-id urakka-id
           :kohdistuksen-id (:kohdistus-id puuttuva-kohdistus)
           :kohdistus puuttuva-kohdistus}))

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
  [db user {:keys [urakka-id kulu-kohdistuksineen] :as tiedot}]
  (log/debug "tallenna-kulu :: tiedot" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (luo-tai-paivita-kulukohdistukset db user urakka-id kulu-kohdistuksineen))

(defn- poista-kulun-liite
  "Merkkaa kulun liitteen poistetuksi"
  [db user {:keys [urakka-id kulu-id liite-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (q/poista-kulun-ja-liitteen-linkitys! db {:kulu-id kulu-id :liite-id liite-id :kayttaja (:id user)})
  (hae-kulu-kohdistuksineen db user {:id kulu-id}))

(defn- kulu-pdf
  [db user {:keys [urakka-id urakka-nimi alkupvm loppupvm] :as loput}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (assert (and alkupvm loppupvm) "alkupvm ja loppupvm oltava annettu")
  (let [kulut (q/hae-kulut-kohdistuksineen-tietoineen-vientiin db {:urakka   urakka-id
                                                            :alkupvm  (konversio/sql-timestamp alkupvm)
                                                            :loppupvm (konversio/sql-timestamp loppupvm)})
        kulut-kuukausien-mukaan (group-by #(pvm/kokovuosi-ja-kuukausi (:erapaiva %))
                                          (sort-by :erapaiva
                                                   kulut))]
    (kpdf/kulu-pdf urakka-nimi
                   (pvm/pvm alkupvm)
                   (pvm/pvm loppupvm)
                   kulut-kuukausien-mukaan)))

(defn hae-urakan-valikatselmukset
  "Haetaan urakalle vuodet, joille on olemassa välikatselmus/päätös. Ja ui:lla voidaan sen mukaan näyttää päiviä,
  joille kuluja voidaan lisäillä"
  [db user {:keys [urakka-id] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (let [urakan-tiedot (first (urakka-kyselyt/hae-urakka db {:id urakka-id}))
        _ (when (nil? urakan-tiedot)
            (throw (IllegalArgumentException.
                     (str "Virheellinen urakka-id " urakka-id))))
        alkupvm (:alkupvm urakan-tiedot)
        loppupvm (:loppupvm urakan-tiedot)
        valikatselmukset (map :hoitokauden-alkuvuosi (valikatselmus-kyselyt/hae-urakan-valikatselmukset-vuosittain db {:urakka-id urakka-id}))
        vuosittaiset-valikatselmukset (reduce (fn [listaus vuosi]
                                                (conj listaus {:vuosi vuosi
                                                               :paatos-tehty? (some #(= vuosi %) valikatselmukset)}))
                                        [] (range (pvm/vuosi alkupvm) (pvm/vuosi loppupvm)))]
    vuosittaiset-valikatselmukset))

(defn- kulu-excel
  [db workbook user {:keys [urakka-id urakka-nimi alkupvm loppupvm] :as loput}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskunkirjoitus user urakka-id)
  (assert (and alkupvm loppupvm) "alkupvm ja loppupvm oltava annettu")
  (let [kulut (sort-by :erapaiva
                       (q/hae-kulut-kohdistuksineen-tietoineen-vientiin db {:urakka   urakka-id
                                                                            :alkupvm  (konversio/sql-timestamp alkupvm)
                                                                            :loppupvm (konversio/sql-timestamp loppupvm)}))
        kulut-kuukausien-mukaan (group-by #(pvm/kokovuosi-ja-kuukausi (:erapaiva %))
                                          (sort-by :erapaiva
                                                   kulut))
        sarakkeet [{:otsikko "Eräpäivä"}
                   {:otsikko
                    "Maksuerä"}
                   {:otsikko
                    "Toimenpide"}
                   {:otsikko "Tehtäväryhmä"}
                   {:otsikko "Summa" :fmt :raha}]
        luo-rivi (fn [rivi]
                   [(-> rivi :erapaiva pvm/pvm str)
                    (str "HA" (:maksuera rivi))
                    (:toimenpide rivi)
                    (or (:tehtavaryhma rivi) "Lisätyö")
                    [:arvo-ja-yksikko {:arvo (:summa rivi)}]])
        eka-rivi-jossa-kustannuksia 4
        luo-data (fn [kaikki [vuosi-kuukausi rivit]]
                   (let [yhteenvetorivi [[nil nil nil "Yhteensä:" [:kaava {:kaava :summaa-yllaolevat
                                                                           :alkurivi eka-rivi-jossa-kustannuksia
                                                                           :loppurivi (+ (count rivit)
                                                                                         (- eka-rivi-jossa-kustannuksia 1))}]]]]
                     (conj kaikki
                           [:taulukko {:nimi urakka-nimi
                                       :sheet-nimi vuosi-kuukausi
                                       :viimeinen-rivi-yhteenveto? true}
                            sarakkeet (into []
                                            (concat
                                              (mapv luo-rivi rivit)
                                              yhteenvetorivi))])))
        taulukot (reduce luo-data [] kulut-kuukausien-mukaan)
        taulukko (concat
                   [:raportti {:raportin-yleiset-tiedot {:urakka urakka-nimi
                                                         :alkupvm (pvm/pvm alkupvm)
                                                         :loppupvm (pvm/pvm loppupvm)
                                                         :raportin-nimi "Kulujen kohdistus"}
                               :orientaatio :landscape}]
                   (if (empty? taulukot)
                     [[:taulukko {:nimi urakka-nimi}
                       [{:otsikko (str urakka-nimi " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))}]
                       [["Ei kuluja valitulla aikavälillä"]]]]
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
      (julkaise-palvelu http :hae-urakan-valikatselmukset
        (fn [user hakuehdot]
          (hae-urakan-valikatselmukset db user hakuehdot)))
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
