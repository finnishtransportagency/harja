(ns harja.palvelin.palvelut.tehtavamaarat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as clj-str]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.tehtavamaarat :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.big :as big]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [clojure.set :as set]))


(defn hae-validit-tehtavat
  "Palauttaa tehtava-id:t niille tehtäville, joille teiden hoidon urakoissa (MHU) voi kirjata."
  [db]
  (into []
        (q/hae-validit-tehtava-idt db)))

(defn hae-tehtavamaarat
  "Palauttaa urakan hoitokausikohtaiset tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
        (q/hae-hoitokauden-tehtavamaarat-urakassa db {:urakka     urakka-id
                                                      :hoitokausi hoitokauden-alkuvuosi})))

(defn hae-tehtavahierarkia
  "Palauttaa tehtävähierarkian kokonaisuudessaan ilman urakkaan liittyviä tietoja."
  [db user {:keys [urakka-id]}]
  (into []
        (q/hae-tehtavahierarkia db {:urakka urakka-id})))

(defn tehtavaryhmat-ja-toimenpiteet
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (when (not urakka-id)
    (throw (IllegalArgumentException. (str "Urakka-id puuttuu"))))
  (into [] (q/tehtavaryhmat-ja-toimenpiteet-urakalle db {:urakka urakka-id})))

(defn- paivita-tarvittaessa [idt polku arvo]
  (if (nil? (get idt arvo))
    (assoc idt polku arvo)
    idt))

(defn- luo-id-fn
  "Väliaikainen id ryhmittelyn helpottamiseksi"
  [polku idt]
  (let [arvo (get idt polku)]
    (if (nil? arvo)
      (Integer/parseInt
        (name
          (gensym "-")))
      arvo)))

(defn luo-kannan-tehtavien-pohjalta-valitasot
  "Luodaan tehtävämääräsivulla nähtävät välitasot (1.0 Talvihoito, jne) joiden perusteella ryhmitellään tehtäviä"
  [{:keys [idt] :as kaikki} {:keys [otsikko sopimus-tallennettu]}]
  (let [valitaso-id (luo-id-fn otsikko idt)]
    (-> kaikki 
      (update 
        :tasot 
        conj 
        {:id valitaso-id
         :nimi otsikko
         :taso 3
         :sopimus-tallennettu sopimus-tallennettu
         :tehtavat []})
      (update :tasot distinct)
      (update :idt paivita-tarvittaessa otsikko valitaso-id))))

(defn ryhmittele-tehtavat-valitasojen-perusteella 
  [idt]
  (fn [tasot {:keys [tehtava-id sopimuksen-tehtavamaara urakka tehtava otsikko yksikko jarjestys maarat] :as rivi}]
    #_(println rivi)
    (let [valitaso-id (luo-id-fn otsikko idt)] 
      (mapv (fn [{:keys [id] :as taso}]
              (if (= id valitaso-id)
                (update taso :tehtavat conj {:id        tehtava-id
                                             :nimi      tehtava
                                             :vanhempi  valitaso-id
                                             :jarjestys jarjestys
                                             :maarat maarat
                                             :urakka urakka
                                             :sopimuksen-tehtavamaara sopimuksen-tehtavamaara
                                             :yksikko   yksikko
                                             :taso      4})
                taso)) 
        tasot))))

(defn- jarjesta-tehtavat 
  [tehtavat] 
  (into [] (sort-by :jarjestys tehtavat)))

(defn- jarjesta-valitasojen-tehtavat 
  [taso]
  (-> taso
    (update :tehtavat jarjesta-tehtavat)))

(defn- luo-tehtavarakenne-idn-perusteella
  [kaikki rivi]
  (let [kaikki
        (if (contains? kaikki (:tehtava-id rivi))
          kaikki
          (assoc kaikki (:tehtava-id rivi) rivi))]
    (if (:hoitokauden-alkuvuosi rivi)
      (assoc-in kaikki [(:tehtava-id rivi) :maarat (:hoitokauden-alkuvuosi rivi)] (:maara rivi))
      kaikki)))

(defn- saman-tehtavan-maarat-yhteen-rakenteeseen
  "Eri vuosien määrät tulevat eri riveinä, yhdistetään ne tässä frontilla näyttöä varten"
  [tehtavat]
  (into [] 
    (map (fn [[_ r]] (dissoc r :maara)))
    (reduce luo-tehtavarakenne-idn-perusteella {} tehtavat)))

(defn- muodosta-hierarkia
  [kannasta]
  (let [valitasot (reduce luo-kannan-tehtavien-pohjalta-valitasot
                    {:idt {} :tasot []}
                    kannasta)
        tehtavat (saman-tehtavan-maarat-yhteen-rakenteeseen kannasta)]       
    (into [] 
      (sort-by :nimi 
               (mapv jarjesta-valitasojen-tehtavat                  
                 (reduce (ryhmittele-tehtavat-valitasojen-perusteella (:idt valitasot))
                   (:tasot valitasot) 
                   tehtavat))))))

(defn hae-tehtavat
  "Urakan tehtävähierarkia ilman määriä"
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [kannasta (into [] (q/hae-tehtavahierarkia db {:urakka urakka-id}))
        hierarkia (muodosta-hierarkia kannasta)]
    hierarkia))

(defn hae-tehtavahierarkia-koko-urakan-ajalle
  "Haetaan kaikkien hoitokausien tehtävämäärät"
  [db {:keys [urakka]}]
  (let [urakkatiedot (first (urakat-q/hae-urakka db {:id urakka}))
        alkuvuosi (-> urakkatiedot
                      :alkupvm
                      pvm/vuosi)
        loppuvuosi (-> urakkatiedot
                       :loppupvm
                       pvm/vuosi)]
    (q/hae-tehtavahierarkia-maarineen db {:urakka     urakka
                                          :hoitokausi (range alkuvuosi
                                                             (inc loppuvuosi))})))

(defn hae-tehtavahierarkia-maarineen
  "Palauttaa tehtävähierarkian otsikko- ja tehtävärivit Suunnittelu > Tehtävä- ja määräluettelo-näkymää varten."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (when (or (nil? urakka-id)
          (nil? hoitokauden-alkuvuosi))
    (throw (IllegalArgumentException. (str "Urakan id ja/tai hoitokauden alkuvuosi puuttuu."))))
  (muodosta-hierarkia
    (let [haettu (if (= :kaikki hoitokauden-alkuvuosi)
                   (hae-tehtavahierarkia-koko-urakan-ajalle db {:urakka urakka-id})
                   (q/hae-tehtavahierarkia-maarineen db {:urakka     urakka-id
                                                         :hoitokausi [hoitokauden-alkuvuosi]}))]
      haettu)))

(defn tallenna-tehtavamaarat
  "Luo tai päivittää urakan hoitokauden tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi tehtavamaarat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        validit-tehtavat (hae-validit-tehtavat db)]

    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei suunnitella tehtävä- ja määäräluettelon tietoja."))))

    (jdbc/with-db-transaction [c db]
                              (doseq [tm tehtavamaarat]
                                (let [nykyiset-arvot (hae-tehtavamaarat c user {:urakka-id             urakka-id
                                                                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
                                      tehtavamaara-avain (fn [rivi]
                                                           [(:hoitokauden-alkuvuosi rivi) (:tehtava-id rivi) (:urakka rivi)])
                                      tehtavamaarat-kannassa (into #{} (map tehtavamaara-avain nykyiset-arvot))
                                      parametrit [c {:urakka     urakka-id
                                                     :hoitokausi hoitokauden-alkuvuosi
                                                     :tehtava    (:tehtava-id tm)
                                                     :maara      (:maara tm)
                                                     :kayttaja   (:id user)}]]
                                  ;; TODO: Kaikki feilaa jos yksi feilaa. Olisiko parempi tallentaa ne mitkä voidaan?
                                  (when (empty?
                                          (filter #(= (:tehtava-id tm)
                                                      (:tehtava-id %))
                                                  validit-tehtavat))
                                    (throw (IllegalArgumentException. (str "Tehtävälle " (:tehtava-id tm) " ei voi tallentaa määrätietoja."))))

                                  (if-not (tehtavamaarat-kannassa 
                                            (tehtavamaara-avain 
                                              (merge tm {:urakka                urakka-id
                                                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))
                                    ;; insert                                   
                                    (apply q/lisaa-tehtavamaara<! parametrit)
                                    ;;  update                                   
                                    (apply q/paivita-tehtavamaara! parametrit))))))
  (hae-tehtavahierarkia-maarineen db user {:urakka-id             urakka-id
                                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

(defn tallenna-sopimuksen-tehtavamaara [db user {:keys [urakka-id tehtava-id maara]}]
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        validit-tehtavat (hae-validit-tehtavat db)]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (when (empty?
          (filter #(= tehtava-id
                     (:tehtava-id %))
            validit-tehtavat))
    (throw (IllegalArgumentException. (str "Tehtävälle " tehtava-id " ei voi suunnitella määrätietoja."))))

  (if-not (= urakkatyyppi :teiden-hoito)
    (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei suunnitella tehtävä- ja määräluettelon tietoja."))))
  (let [maara (if (big/big? maara) 
                maara
                (-> maara big/->big big/unwrap))]
    (q/tallenna-sopimuksen-tehtavamaara db user urakka-id tehtava-id maara))))

(defn poista-namespace 
  [[avain arvo]]
  [(-> avain name keyword) arvo])

(defn poista-mapista-namespacet
  [mappi]
  (into {} (map poista-namespace) mappi))

(defn poista-tuloksista-namespace
  [tulokset]
  (if (map? tulokset)
    (poista-mapista-namespacet tulokset)
    (into [] 
      (map poista-mapista-namespacet) 
      tulokset)))

(defn tallenna-sopimuksen-tila
  "Vahvistetaan sopimuksessa sovitut ja HARJAan syötetyt määräluvut urakalle"
  [db user {:keys [urakka-id] :as parametrit}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))]
    (when-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei suunnitella tehtävä- ja määräluettelon tietoja.")))))
  (poista-tuloksista-namespace 
    (q/tallenna-sopimuksen-tila db parametrit (:tallennettu parametrit))))

(defn hae-sopimuksen-tila
  "Haetaan sopimuksen tila kannasta"
  [db user {:keys [urakka-id] :as parametrit}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [tulos (first 
                (poista-tuloksista-namespace 
                  (q/hae-sopimuksen-tila db parametrit)))]
    (if-not tulos
      {:tallennettu nil}
      tulos)))

(defrecord Tehtavamaarat []
  component/Lifecycle
  (start [{:keys [db http-palvelin] :as this}]
    (doto http-palvelin
      (julkaise-palvelu
        :tallenna-sopimuksen-tila
        (fn [user tiedot]
          (tallenna-sopimuksen-tila db user tiedot)))
      (julkaise-palvelu 
        :hae-sopimuksen-tila
        (fn [user tiedot]
          (hae-sopimuksen-tila db user tiedot)))
      (julkaise-palvelu
        :tehtavat
        (fn [user tiedot]
          (hae-tehtavat db user tiedot)))
      (julkaise-palvelu
        :tehtavahierarkia
        (fn [user tiedot]
          (hae-tehtavahierarkia db user tiedot)))
      (julkaise-palvelu
        :tehtavamaarat-hierarkiassa
        (fn [user tiedot]
          (hae-tehtavahierarkia-maarineen db user tiedot)))
      (julkaise-palvelu
        :tehtavamaarat
        (fn [user tiedot]
          (hae-tehtavamaarat db user tiedot)))
      (julkaise-palvelu
        :tallenna-tehtavamaarat
        (fn [user tiedot]
          (tallenna-tehtavamaarat db user tiedot)))
      (julkaise-palvelu
        :tehtavaryhmat-ja-toimenpiteet
        (fn [user tiedot]
          (tehtavaryhmat-ja-toimenpiteet db user tiedot)))
      (julkaise-palvelu
        :tallenna-sopimuksen-tehtavamaara
        (fn [user tiedot]
          (tallenna-sopimuksen-tehtavamaara db user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :tallenna-sopimuksen-tila)
    (poista-palvelu (:http-palvelin this) :hae-sopimuksen-tila)
    (poista-palvelu (:http-palvelin this) :tehtavat)
    (poista-palvelu (:http-palvelin this) :tehtavahierarkia)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat-hierarkiassa)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tallenna-tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tehtavaryhmat-ja-toimenpiteet)
    (poista-palvelu (:http-palvelin this) :tallenna-suunnitellut-tehtavamaarat)
    this))
