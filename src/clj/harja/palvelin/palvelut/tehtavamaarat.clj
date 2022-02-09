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

(defn- muodosta-hierarkia
  [kannasta]
  (let [valitasot (reduce (fn [{:keys [idt] :as kaikki} {:keys [otsikko] :as rivi}]
                            (let [valitaso-id (luo-id-fn otsikko idt)]
                              (-> kaikki 
                                (update 
                                  :tasot 
                                  conj 
                                  {:id valitaso-id
                                   :nimi otsikko
                                   :taso 3
                                   :tehtavat []})
                                (update :tasot distinct)
                                (update :idt paivita-tarvittaessa otsikko valitaso-id))))
                    {:idt {} :tasot []}
                    kannasta)
        redusointi-fn (fn [idt]
                        (fn [tasot {:keys [tehtava-id tehtava otsikko yksikko jarjestys] :as rivi}]
                          (let [valitaso-id (luo-id-fn otsikko idt)] 
                            (mapv (fn [{:keys [id] :as taso}]
                                    (if (= id valitaso-id)
                                      (update taso :tehtavat conj {:id        tehtava-id
                                                                   :nimi      tehtava
                                                                   :vanhempi  valitaso-id
                                                                   :jarjestys jarjestys
                                                                   :yksikko   yksikko
                                                                   :taso      4})
                                      taso)) 
                              tasot))))]       
    (into [] 
      (sort-by :nimi 
               (mapv (fn [taso]
                       (-> taso
                         (update :tehtavat (fn [tehtavat] 
                                             (into [] (sort-by :jarjestys tehtavat)))))) 
                 
                 (reduce (redusointi-fn (:idt valitasot))
                   (:tasot valitasot) 
                   kannasta))))))

(defn hae-tehtavat
  "Urakan tehtävähierarkia ilman määriä"
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [kannasta (into [] (q/hae-tehtavahierarkia db {:urakka urakka-id}))
        hierarkia (muodosta-hierarkia kannasta)]
    hierarkia))

(defn- jarjesta-tehtavahierarkia
  "Järjestää tehtävähierarkian käyttöliittymän (Suunnittelu > Tehtävä- ja määräluettelo) tarvitsemaan muotoon.
  Suunnitteluosiossa ei tehtävähierarkian tasoilla (ylä-, väli- ja alataso) ole merkitystä. Tasoja käytetään budjettiseurannassa.
  Suunnittelussa tehtävähierarkia muodostuu sopimuksen liitteen mukaisista otsikkoriveistä sekä niiden alle jakautuvista tehtäväriveistä.
  Käyttäjä syöttää suunnitellut määrät tehtäväriveille. Käytä tehtävän id:tä tunnisteena, kun tallennat tiedot tietokantaan."
  [hierarkia]

  ;; [{:id "1" :nimi "1.0 TALVIHOITO" :tehtavaryhmatyyppi "otsikko" :piilotettu? false}
  ;; {:id "2" :tehtava-id 4548 :nimi "Ise 2-ajorat." :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "1" :piilotettu? false}
  ;; {:id "3" :nimi "2.1 LIIKENNEYMPÄRISTÖN HOITO" :tehtavaryhmatyyppi "otsikko" :piilotettu? false}
  ;; {:id "4" :tehtava-id 4565 :nimi "Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piilotettu? false}
  ;; {:id "5" :tehtava-id 4621  :nimi "Opastustaulun/-viitan uusiminen" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piilotettu? false}]

  ;; TODO: Muodosta palautettavat tiedot. Vrt. println tulostukset.
  (let [cnt (atom 1)
        tulos (atom [])
        tehtavahierarkia (sort-by first (group-by :otsikko hierarkia))] ;; Ryhmitelty hierarkia sisältää otsikot (first) ja niiden alle kuuluvat tehtävärivit (second)
    (doseq [rivi tehtavahierarkia]
      (let [emo (Long/valueOf @cnt)
            otsikko (first rivi)
            tehtavalista (second rivi)]
        ;; TODO: Muodosta otsikkotyyppinen rivi
        (swap! tulos conj {:id                 @cnt
                           :tehtavaryhmatyyppi "otsikko"
                           :nimi               otsikko
                           :piilotettu?       false})
        (doseq [{:keys [tehtava-id maara hoitokauden-alkuvuosi urakka sopimuksen-tehtavamaara]} tehtavalista]
          (swap! cnt + 1)
          (swap! tulos conj {:tehtava-id tehtava-id
                             :maara (if (nil? maara) 0 maara)
                             :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                             :urakka urakka
                             :piilotettu? false
                             :sopimuksen-tehtavamaara sopimuksen-tehtavamaara}))))
    ;; TODO: Muodosta tehtävätyyppinen rivi
    @tulos))

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
  (jarjesta-tehtavahierarkia
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

                                  (if-not (tehtavamaarat-kannassa (tehtavamaara-avain (merge tm {:urakka                urakka-id
                                                                                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))
                                    ;; insert
                                    (do
                                      (apply q/lisaa-tehtavamaara<! parametrit))
                                    ;;  update
                                    (do
                                      (apply q/paivita-tehtavamaara! parametrit)))))))
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
  (q/tallenna-sopimuksen-tehtavamaara db user urakka-id tehtava-id maara)))

(defrecord Tehtavamaarat []
  component/Lifecycle
  (start [{:keys [db http-palvelin] :as this}]
    (doto http-palvelin
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
    (poista-palvelu (:http-palvelin this) :tehtavat)
    (poista-palvelu (:http-palvelin this) :tehtavahierarkia)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat-hierarkiassa)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tallenna-tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tehtavaryhmat-ja-toimenpiteet)
    (poista-palvelu (:http-palvelin this) :tallenna-suunnitellut-tehtavamaarat)
    this))
