(ns harja.palvelin.palvelut.tehtavamaarat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.tyokalut.big :as big]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.roolit :as roolit]))

(defn hae-validit-tehtavat
  "Palauttaa tehtava-id:t niille tehtäville, joille teiden hoidon urakoissa (MHU) voi kirjata."
  [db]
  (into []
        (tehtavamaarat-kyselyt/hae-validit-tehtava-idt db)))

(defn hae-suunnitellut-tehtavamaarat
  "Palauttaa urakan suunnitellut hoitokausikohtaiset tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
    (tehtavamaarat-kyselyt/hae-suunnitellut-hoitokauden-tehtavamaarat-urakassa db {:urakka urakka-id
                                                               :hoitokausi hoitokauden-alkuvuosi})))

(defn tehtavaryhmat-ja-toimenpiteet
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (when (not urakka-id)
    (throw (IllegalArgumentException. (str "Urakka-id puuttuu"))))
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        ;; Varmista, että urakka on olemassa
        _ (when (nil? urakan-tiedot)
            (throw (IllegalArgumentException. (format "Urakkaa %s ei ole olemassa." urakka-id))))
        alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
        tehtavaryhmat-ja-toimenpiteet (into []
                                        (tehtavamaarat-kyselyt/tehtavaryhmat-ja-toimenpiteet-urakalle db
                                          {:urakka urakka-id
                                           :urakka-voimassaolo-alkuvuosi alkuvuosi}))]
    tehtavaryhmat-ja-toimenpiteet))

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
  (fn [tasot {:keys [tehtava-id sopimuksen-tehtavamaarat urakka tehtava otsikko yksikko samat-maarat-vuosittain?
                     sopimuksen-aluetieto-maara jarjestys suunnitellut-maarat aluetieto muuttuneet-tarjouksesta] :as rivi}]
    (let [valitaso-id (luo-id-fn otsikko idt)]
      (mapv (fn [{:keys [id] :as taso}]
              (if (= id valitaso-id)
                (update taso :tehtavat conj (merge
                                              {:id tehtava-id
                                               :nimi tehtava
                                               :vanhempi valitaso-id
                                               :jarjestys jarjestys
                                               :aluetieto? aluetieto
                                               :urakka urakka                                               
                                               :yksikko yksikko
                                               :suunnitellut-maarat suunnitellut-maarat
                                               :muuttuneet-tarjouksesta muuttuneet-tarjouksesta
                                               :taso 4
                                               :samat-maarat-vuosittain? samat-maarat-vuosittain?
                                               :rahavaraus? (:onko-rahavaraus? rivi)}
                                              (if aluetieto
                                                ;; Pahoittelut tästä monimutkaisuudesta. Mutta: Aluetiedoilla ei pitäisi olla kuin yksi sopimus/tarjous määrä. Jostain syystä niillä voi olla
                                                ;; kaikille hoitokauden vuosille tietokannassa jokin arvo ja ne arvot tulevat vielä väärässä järjestyksessä. Ojennetaan ne siis oikeaan järjestykseen
                                                ;; Koska frontti käyttää sitä ensimmäistä.
                                                {:sopimuksen-aluetieto-maara (into (sorted-map) sopimuksen-aluetieto-maara)}
                                                {:samat-maarat-vuosittain? samat-maarat-vuosittain?
                                                 :sopimuksen-tehtavamaarat sopimuksen-tehtavamaarat})))
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
      (-> kaikki
        (assoc-in [(:tehtava-id rivi) :suunnitellut-maarat (:hoitokauden-alkuvuosi rivi)] (:suunniteltu-maara rivi))
        (assoc-in [(:tehtava-id rivi) :muuttuneet-tarjouksesta (:hoitokauden-alkuvuosi rivi)] (:muuttunut-tarjouksesta? rivi)))
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

(defn hae-tehtavahierarkia-koko-urakan-ajalle
  "Haetaan kaikkien hoitokausien tehtävämäärät"
  [db {:keys [urakka]}]
  (let [urakkatiedot (first (urakat-q/hae-urakka db {:id urakka}))
        _ (when (nil? urakkatiedot)
            (throw (IllegalArgumentException. (str "Urakka ei ole olemassa."))))
        alkuvuosi (-> urakkatiedot :alkupvm pvm/vuosi)
        loppuvuosi (-> urakkatiedot :loppupvm pvm/vuosi)]
    (tehtavamaarat-kyselyt/mhu-suunniteltavat-tehtavat db {:urakka urakka
                                          :hoitokausi (range alkuvuosi
                                                        (inc loppuvuosi))})))

(defn laske-tehtavien-sopimusmaarat-urakalle
  [tehtavat]
  (into {} 
    (map
      (fn [[id vuodet]]
        [id (reduce (fn [kaikki {:keys [sopimuksen-tehtavamaara hoitovuosi]} ]
                      (assoc-in kaikki [hoitovuosi] sopimuksen-tehtavamaara))
              {}
              vuodet)]))
    (group-by :tehtava tehtavat)))

(defn- samat-summat-pred
  [verrokki summa]
  (= verrokki summa))

(defn onko-samat-summat?
  [sopimusmaarat]
  (let [yksi-sopimusmaarista (get sopimusmaarat (first (keys sopimusmaarat)))
        samat? (every? (partial samat-summat-pred yksi-sopimusmaarista) (vals sopimusmaarat))] 
    (if (some? sopimusmaarat) 
      samat?
      true)))

(defn yhdista-sopimusmaarat
  [tehtavat {:keys [tehtava-id aluetieto] :as rivi}]
  (let [sopimusmaarat (get-in tehtavat [tehtava-id])
        samat? (when-not (:aluetieto rivi) (onko-samat-summat? sopimusmaarat))]
    (cond-> rivi
      aluetieto (assoc :sopimuksen-aluetieto-maara sopimusmaarat)
      (not aluetieto) (assoc :sopimuksen-tehtavamaarat sopimusmaarat)
      true (assoc :samat-maarat-vuosittain? samat?))))

(defn mhu-suunniteltavat-tehtavat
  "Palauttaa tehtävähierarkian otsikko- ja tehtävärivit Suunnittelu > Tehtävä- ja määräluettelo-näkymää varten."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]
  (log/debug "mhu-suunniteltavat-tehtavat :: tiedot" tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (when (or (nil? urakka-id)
          (nil? hoitokauden-alkuvuosi))
    (throw (IllegalArgumentException. (str "Urakan id ja/tai hoitokauden alkuvuosi puuttuu."))))
  (log/debug "mhu-suunniteltavat-tehtavat :: tiedot: " tiedot)
  (let [hierarkia (muodosta-hierarkia
                    (let [urakan-sopimusmaarat (laske-tehtavien-sopimusmaarat-urakalle
                                                 (tehtavamaarat-kyselyt/hae-sopimuksen-tehtavamaarat-urakalle db {:urakka urakka-id}))
                          tehtavat (hae-tehtavahierarkia-koko-urakan-ajalle db {:urakka urakka-id})
                          yhdistetyt (mapv (partial yhdista-sopimusmaarat urakan-sopimusmaarat) tehtavat)]
                      yhdistetyt))]
    hierarkia))

(defn tallenna-tehtavamaarat
  "Luo tai päivittää urakan tehtävämääriä."
  [db user {:keys [urakka-id tehtavamaarat nykyinen-hoitokausi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        validit-tehtavat (hae-validit-tehtavat db)]

    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei suunnitella tehtävä- ja määäräluettelon tietoja."))))

    (jdbc/with-db-transaction [c db]
      (doseq [tm tehtavamaarat]
        (let [{:keys [hoitokauden-alkuvuosi tehtava-id maara]} tm
              maara (bigdec (fmt/pilkku->piste maara))
              nykyiset-arvot (hae-suunnitellut-tehtavamaarat c user {:urakka-id urakka-id
                                                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
              tehtavamaara-avain (fn [rivi]
                                   [(:hoitokauden-alkuvuosi rivi) (:tehtava-id rivi) (:urakka rivi)])
              tehtava-kannasta (some #(when (= tehtava-id (:tehtava-id %))
                                      %) nykyiset-arvot)
              tehtavamaarat-kannassa (into #{} (map tehtavamaara-avain nykyiset-arvot))
              parametrit {:urakka urakka-id
                          :hoitokausi hoitokauden-alkuvuosi
                          :tehtava tehtava-id
                          :maara maara
                          :kayttaja (:id user)}
              sopimuksen-maarat (when (nil? maara)
                                  (tehtavamaarat-kyselyt/hae-sopimuksen-tehtavamaaran-maara c {:urakka-id urakka-id
                                                                           :tehtava-id tehtava-id}))
              vuosittainen-sopimuksen-maara (fn [vuosi]
                                              ;; sopimuksen_tehtavamaarat tauluun tallennetaan kaikkille muille paitsi aluetiedoille jokaiselle vuodelle oma
                                              ;; määrä. Aluetiedoilla on oltava jokaisena vuonna sama määrä
                                              (let [maara (if (:aluetieto? (first sopimuksen-maarat))
                                                            (:sopimuksen-maara (first sopimuksen-maarat))
                                                            (some #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                                     (:sopimuksen-maara %)) sopimuksen-maarat))]
                                                maara))]
          ;; TODO: Kaikki feilaa jos yksi feilaa. Olisiko parempi tallentaa ne mitkä voidaan?
          (when (empty?
                  (filter #(= tehtava-id
                             (:tehtava-id %))
                    validit-tehtavat))
            (throw (IllegalArgumentException. (str "Tehtävälle " (:tehtava-id tm) " ei voi tallentaa määrätietoja."))))

          (if-not (tehtavamaarat-kannassa
                    (tehtavamaara-avain
                      (merge tm {:urakka urakka-id
                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))
            ;; insert
            (tehtavamaarat-kyselyt/lisaa-tehtavamaara<! c (merge parametrit
                                              (if (:aluetieto? tehtava-kannasta)
                                                {:muuttunut-tarjouksesta? false}
                                                {:muuttunut-tarjouksesta? true})))
            ;;  update - Päivitetään mahdollisesti :muuttunut-tarjouksesta? falseksi, jos suunniteltavaksi määräksi annetaan nil.
            ;;  urakka_tehtavamaarat taulun pitää kuitenkin pitää sisällään aina suunniteltu määrä, joka nil tilanteessa tarkoittaa sopimukseen asetettua määrä.
            ;; Joten siinä tilanteessa haetaan sopimuksen määrä ja tallennetaan se suunnitelluksi määräksi.
            (tehtavamaarat-kyselyt/paivita-tehtavamaara! c
              (merge parametrit
                {:muuttunut-tarjouksesta? (if (nil? maara) false true)
                 :maara (if (nil? maara)
                          (vuosittainen-sopimuksen-maara hoitokauden-alkuvuosi)
                          maara)}))))))
    (mhu-suunniteltavat-tehtavat db user {:urakka-id urakka-id
                                             :hoitokauden-alkuvuosi nykyinen-hoitokausi})))

(defn tallenna-sopimuksen-tehtavamaara [db user {:keys [urakka-id tehtava-id maara hoitovuosi samat-maarat-vuosittain?] :as tiedot}]
  (log/debug "tallenna-sopimuksen-tehtavamaara :: tiedot" tiedot)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        validit-tehtavat (hae-validit-tehtavat db)]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
    (when (empty?
            (filter #(= tehtava-id
                       (:tehtava-id %))
              validit-tehtavat))
      (throw (IllegalArgumentException. (str "Tehtävälle " tehtava-id " ei voi antaa sopimuksessa määrätietoja."))))

    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei ole sopimuksella tehtävä- ja määräluettelon tietoja."))))
    (let [maara (when maara (bigdec maara))
          urakkatiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
          alkuvuosi (-> urakkatiedot :alkupvm pvm/vuosi)
          loppuvuosi (-> urakkatiedot :loppupvm pvm/vuosi)
          sopimuksen-tehtavamaarat (mapv (fn [hoitokauden-alkuvuosi]
                                           (if maara
                                             (tehtavamaarat-kyselyt/tallenna-sopimuksen-tehtavamaara db user urakka-id tehtava-id maara hoitokauden-alkuvuosi)
                                             (when (and (int? urakka-id) (int? tehtava-id) (int? hoitokauden-alkuvuosi))
                                               (tehtavamaarat-kyselyt/poista-sopimuksen-tehtavamaara! db {:urakka-id urakka-id
                                                                                      :tehtava-id tehtava-id
                                                                                      :vuosi hoitokauden-alkuvuosi}))))
                                     (if-not samat-maarat-vuosittain?
                                       [hoitovuosi]
                                       (range alkuvuosi loppuvuosi)))]
      sopimuksen-tehtavamaarat)))

(defn tallenna-sopimuksen-tehtavamaara-kaikille-tehtaville-test
  "Testausta helpottava palvelu, jota ei voi eikä saa käyttää tuotannossa. Tallentaa kaikille tehtäville suunnitellun määrän."
  [db user {:keys [urakka-id] :as tiedot} kehitysmoodi?]
  (log/debug "tallenna-sopimuksen-tehtavamaara-test :: tiedot" tiedot " kehitysmoodi? " kehitysmoodi?)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        validit-tehtavat (hae-validit-tehtavat db)]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei ole sopimuksella tehtävä- ja määräluettelon tietoja."))))
    (let [maara 100M
          urakkatiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
          alkuvuosi (-> urakkatiedot :alkupvm pvm/vuosi)
          loppuvuosi (-> urakkatiedot :loppupvm pvm/vuosi)
          sopimuksen-tehtavamaarat (mapv (fn [hoitokauden-alkuvuosi]
                                           (doseq [t validit-tehtavat]
                                             (when (and
                                                     (roolit/roolissa? user roolit/jarjestelmavastaava) kehitysmoodi?)
                                               (tehtavamaarat-kyselyt/tallenna-sopimuksen-tehtavamaara db user urakka-id (:tehtava-id t) maara hoitokauden-alkuvuosi))))
                                         (range alkuvuosi loppuvuosi))]
      (mhu-suunniteltavat-tehtavat db user {:urakka-id urakka-id
                                               :hoitokauden-alkuvuosi alkuvuosi}))))

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

(defn- kopioi-tarjouksen-tiedot-suunnitelmaksi
  "Vahvistaessa kopioidaan sopimuksen syötetyt määrätiedot valmiiksi urakan tehtävämääriin ja merkitään ei aluetiedot muokatuksi"
  [db user urakka-id]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db urakka-id))
        alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
        sopimuksen-tehtavamaarat (tehtavamaarat-kyselyt/hae-sopimuksen-tehtavamaarat-urakalle db {:urakka urakka-id})
        sopimuksen-tehtavamaarat (reduce (fn [lopputulos rivi]
                                           (if (:aluetieto rivi)
                                             (let [lopputulos (remove #(= % rivi) lopputulos)]
                                               (flatten
                                                    (conj (if (seq? lopputulos) lopputulos [lopputulos])
                                                      (reduce (fn [r vuosi]
                                                                (conj r (merge rivi {:hoitovuosi vuosi})))
                                                        [] (range alkuvuosi loppuvuosi)))))
                                             lopputulos))
                                   sopimuksen-tehtavamaarat sopimuksen-tehtavamaarat)]
    (doseq [rivi sopimuksen-tehtavamaarat]
      (let [{:keys [aluetieto hoitovuosi tehtava sopimuksen-tehtavamaara]} rivi]
        (tehtavamaarat-kyselyt/lisaa-urakka-tehtavamaara-mutta-ala-paivita<! db {:urakka urakka-id
                                                             :hoitokauden-alkuvuosi hoitovuosi
                                                             :tehtava tehtava
                                                             :maara sopimuksen-tehtavamaara
                                                             :kayttaja (:id user)
                                                             :muuttunut-tarjouksesta? (not aluetieto)})))))

(defn tallenna-sopimuksen-tila
  "Vahvistetaan sopimuksessa sovitut ja HARJAan syötetyt määräluvut urakalle"
  [db user {:keys [urakka-id] :as parametrit}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))]
    (when-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei suunnitella tehtävä- ja määräluettelon tietoja.")))))
  (jdbc/with-db-transaction [db db]
    ;; Kopioi sopimuksen tiedot urakka_tehtavamaarat tauluun (eli suunnitelmaksi) vain jos
    ;; sopimus tallennetaan, ei silloin kun sitä aletaan muokkaamaan
    (when (:tallennettu parametrit)
      (kopioi-tarjouksen-tiedot-suunnitelmaksi db user urakka-id))
    (poista-tuloksista-namespace
      (tehtavamaarat-kyselyt/tallenna-sopimuksen-tila db parametrit (:tallennettu parametrit)))))

(defn hae-sopimuksen-tila
  "Haetaan sopimuksen tila kannasta"
  [db user {:keys [urakka-id] :as parametrit}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [tulos (first 
                (poista-tuloksista-namespace 
                  (tehtavamaarat-kyselyt/hae-sopimuksen-tila db parametrit)))]
    (if-not tulos
      {:tallennettu nil}
      tulos)))

(defrecord Tehtavamaarat [kehitysmoodi?]
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
        :hae-mhu-suunniteltavat-tehtavat
        (fn [user tiedot]
          (mhu-suunniteltavat-tehtavat db user tiedot)))
      (julkaise-palvelu
        :tehtavamaarat
        (fn [user tiedot]
          (hae-suunnitellut-tehtavamaarat db user tiedot)))
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
          (tallenna-sopimuksen-tehtavamaara db user tiedot)))
      (julkaise-palvelu
        :tallenna-sopimuksen-tehtavamaara-kaikille-tehtaville-test
        (fn [user tiedot]
          (tallenna-sopimuksen-tehtavamaara-kaikille-tehtaville-test db user tiedot kehitysmoodi?))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :tallenna-sopimuksen-tila)
    (poista-palvelu (:http-palvelin this) :hae-sopimuksen-tila)
    (poista-palvelu (:http-palvelin this) :hae-mhu-suunniteltavat-tehtavat)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tallenna-tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tehtavaryhmat-ja-toimenpiteet)
    (poista-palvelu (:http-palvelin this) :tallenna-sopimuksen-tehtavamaara)
    (poista-palvelu (:http-palvelin this) :tallenna-sopimuksen-tehtavamaara-kaikille-tehtaville-test)
    this))
