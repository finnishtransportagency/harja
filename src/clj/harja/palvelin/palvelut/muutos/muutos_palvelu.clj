(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.domain.kulut :as kulut-domain]
            [harja.domain.mhu :as mhu]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.pvm :as pvm]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.kulut :as kulu-kyselyt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.kyselyt.liitteet :as liite-kyselyt]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.yleiset :as yleiset]
            [taoensso.timbre :as log]))


(defn tavoitehinnan-muutos
  "Laskee rivin tavoitehinnan muutoksen. Sen sijainti vaihtelee tyyppikohtaisesti."
  ;; on hyvä saada tavoitehinnan muutos samaan avaimeen, niin summauslaskennat jne. toimivat myöhemmin suoraan
  [muutokset]
  (mapv (fn [rivi]
          (let [total (if (= (:tyyppi rivi)
                            "johto-ja-hallintokorvaus")
                        (or (:jjh-muutosten-summa rivi) 0)
                        (some->>
                          (:kustannusvaikutukset rivi)
                          (map :summa)
                          (reduce + 0)))]
            (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn- rahavarausten-summarivi [rahavaraukset]
  (let [{:keys [summa-indeksikorjattu toteumat]}
        (reduce (fn [acc {:keys [summa-indeksikorjattu toteumat]}]
                  {:summa-indeksikorjattu (+ (:summa-indeksikorjattu acc 0)
                                            (or summa-indeksikorjattu 0))
                   :toteumat (+ (:toteumat acc 0)
                               (or toteumat 0))})
          {}
          rahavaraukset)]
    {:id :yhteenveto
     :summa-indeksikorjattu summa-indeksikorjattu
     :toteumat toteumat
     :tavoitehinnan-muutos (- toteumat summa-indeksikorjattu)}))


(defn hae-hoitovuosien-yksikkohinnat
  [db user {:keys [urakka-id hoitokaudet tehtava_id] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (map-indexed (fn [idx valittu-hoitokausi]
                 (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                       alkupvm (str hoitokauden-alkuvuosi "-10-01")
                       loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
                       parameterssit {:urakka urakka-id
                                      :tehtavaryhma nil
                                      :tehtava (or tehtava_id nil)
                                      :alkupvm alkupvm
                                      :loppupvm loppupvm
                                      :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}
                       yksikkohinta (->
                                      (muutos-kyselyt/hae-tehtava-maaramuutokset db parameterssit)
                                      first
                                      :yksikkohinta)]
                   ;; esim:: 7,90 (1. hoitovuoden yksikköhinta)
                   {:valinta (str
                               yksikkohinta " "
                               "(" (inc idx) ". hoitovuoden yksikköhinta)")
                    :arvo yksikkohinta
                    :hk-nro (inc idx)
                    :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
    hoitokaudet))


(defn hae-tehtava-maaramuutokset
  [db user {:keys [urakka-id valittu-hoitokausi hoitokaudet] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        alkupvm (str hoitokauden-alkuvuosi "-10-01")
        loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
        parameterssit {:urakka urakka-id
                       :tehtavaryhma nil
                       :tehtava nil
                       :alkupvm alkupvm
                       :loppupvm loppupvm
                       :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}
        vastaus (muutos-kyselyt/hae-tehtava-maaramuutokset db parameterssit)

        vastaus-yksikkohinta-tarkistettu (map (fn [rivi]
                                                ;;(println "\n\n rivit:: " rivi)
                                                (let [tehtavalla-ei-toteumia? (or
                                                                                (not (:maara rivi))
                                                                                (>= (:maara rivi) 0))

                                                      aikaisemmat-yksikkohinnat (when tehtavalla-ei-toteumia?
                                                                                  (hae-hoitovuosien-yksikkohinnat db user {:urakka-id urakka-id
                                                                                                                           :hoitokaudet hoitokaudet
                                                                                                                           :tehtava_id (:tehtava_id rivi)}))

                                                      ;;_ (println "\n aikaisemmat-yksikkohinnat PRE:: " aikaisemmat-yksikkohinnat)
                                                      aikaisemmat-yksikkohinnat (filter #(and
                                                                                           ;; Vaadi että jokin yksikköhinta saatavilla 
                                                                                           (some? (:arvo %))
                                                                                           ;; Suodata kuluva hk pois, tulee mukaan esim jos yksikköhinnan lähde on aseta  
                                                                                           (not= hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi %))) aikaisemmat-yksikkohinnat)

                                                      ;;_ (println "\n aikaisemmat-yksikkohinnat POST:: " aikaisemmat-yksikkohinnat)

                                                      loytyi-aikaisemmat-yksikkohinnat? (some? (seq aikaisemmat-yksikkohinnat))
                                                      ;;_ (println "\n löytyi aikaisemmat:: " loytyi-aikaisemmat-yksikkohinnat?)

                                                      anna-kirjata-tavoitehinta? (and
                                                                                   tehtavalla-ei-toteumia?
                                                                                   (not loytyi-aikaisemmat-yksikkohinnat?))]

                                                  (assoc rivi :anna-kirjata-tavoitehinta? anna-kirjata-tavoitehinta?))) vastaus)

        ;;_ (println "\n merkitse:: " merkitse-tehtavat)
        ]
    vastaus-yksikkohinta-tarkistettu))


(defn tallenna-tehtava-maaramuutokset
  [db user {:keys [urakka-id valittu-hoitokausi tehtava_id] :as _tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)

  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        parameterssit {:urakka urakka-id
                       :tehtavaryhma nil
                       :tehtava nil
                       :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}])
  ;; TODO 
  )


(defn tallenna-maaramuutos-yksikkohinta [db
                                         {:keys [id] :as kayttaja}
                                         {:keys [urakka-id valittu-hoitokausi rivi hoitokaudet] :as _tiedot}]

  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)

  (let [{:keys [syy
                tehtava_id
                yksikkohinta
                yksikkohinnan_alkuvuosi
                syotetty_tavoitehintamuutos]} rivi
        
        ;; TODO 
        lahde "aseta"#_(cond
                         (and
                           (some? yksikkohinnan_alkuvuosi)))
        hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        parameterssit {:urakka urakka-id
                       :tehtava tehtava_id
                       :hk_alkuvousi hoitokauden-alkuvuosi
                       :yksikkohinta_hk_alkuvuosi yksikkohinnan_alkuvuosi
                       :kasin_syotetty_tavoitehinta syotetty_tavoitehintamuutos
                       :lahde lahde
                       :asetettu_yksikkohinta yksikkohinta
                       :syy syy
                       :kayttaja id}
        _ (println "\n params:: " parameterssit " \n")]
    (muutos-kyselyt/paivita-tehtava-maaramuutos<! db parameterssit)
    (hae-tehtava-maaramuutokset db kayttaja (assoc parameterssit
                                              :urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi))))


(defn hae-urakan-muutostiedot
  [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (log/debug "hae-urakan-muutostiedot: " tiedot)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        kirjatut-muutokset-vastaus (mapv
                                     (fn [rivi]
                                       (-> rivi
                                         (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                                         (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
                                         (update :liitteet #(konv/jsonb->clojuremap %))))
                                     (muutos-kyselyt/hae-urakan-hoitovuoden-muutostiedot db {:urakka urakka-id
                                                                                             :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
        kirjatut-muutokset (tavoitehinnan-muutos kirjatut-muutokset-vastaus)
        rahavarausten-suunnitelmat (map
                                     #(select-keys % [:id :nimi :summa-indeksikorjattu])
                                     (rahavaraus-kyselyt/hae-urakan-suunnitellut-rahavarausten-kustannukset db {:urakka_id urakka-id
                                                                                                               ;; haetaan vain valitulle hoitovuodelle
                                                                                                               :alkuvuosi hoitokauden-alkuvuosi
                                                                                                               :loppuvuosi (inc hoitokauden-alkuvuosi)}))
        rahavarausten-toteumat (muutos-kyselyt/rahavarausten-toteumat db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavarausten-suunnitelmat rahavarausten-toteumat :id)
        rahavarausmuutosten-syyt (muutos-kyselyt/rahavarausmuutosten-syyt db {:urakka urakka-id
                                                                              :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavaraukset rahavarausmuutosten-syyt :id)
        rahavaraukset (mapv
                        ;; lasketaan erotus vain jos molemmat arvot ovat olemassa
                        #(if (and (:summa-indeksikorjattu %)
                               (:toteumat %))
                           (assoc % :tavoitehinnan-muutos (- (:toteumat %)
                                                            (:summa-indeksikorjattu %)))
                           %)
                        rahavaraukset)
        rahavaraukset-yhteensa (rahavarausten-summarivi rahavaraukset)
        rahavaraukset (conj rahavaraukset rahavaraukset-yhteensa)
        budjettitavoiteet (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)
        muutosten-vaikutus-yhteensa (reduce + 0
                                      (remove nil?
                                        (concat
                                          (map :tavoitehinnan-muutos kirjatut-muutokset)
                                          [(:tavoitehinnan-muutos (last rahavaraukset))])))]
    ;; kirjatut muutokset jos hoitokausi 2025-2026 tai jälkeen
    {:kirjatut-muutokset kirjatut-muutokset
     ;; TODO: laskennat lasketuille muutoksille jos hoitokausi 2025-2026 tai jälkeen
     :lasketut-muutokset []
     :rahavarausten-muutokset rahavaraukset
     ;; TODO: laskennat vanhojen tavoitehintojen muutoksille jos hoitokausi ennen 2025-2026
     :tavoitehinnan-muutokset []
     ;; TODO: laskennat vanhojen suunniteltujen määrien muutoksille jos hoitokausi ennen 2025-2026
     :suunniteltujen-maarien-muutokset []
     :budjettitavoitteet {:indeksikorjaus-vahvistettu? (:indeksikorjaus-vahvistettu budjettitavoiteet)
                          :hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                          :muutosten-vaikutus-yhteensa muutosten-vaikutus-yhteensa
                          :hoitovuoden-lopun-tavoitehinta (when (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                                                            (+
                                                              (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                                                              ;; TODO: tässä huomioitava kaikkien muutosten vaikutus, työversiossa vasta kirjatut muutokset mukana
                                                              muutosten-vaikutus-yhteensa))}}))

(defn hae-muutoksen-tiedot
  "Palauttaa yksittäisen muutoksen tarkat tiedot lomaketta varten."
  [db user {:keys [urakka-id muutos] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [tyypikohtaiset-tiedot (case (:tyyppi muutos)
                                "johto-ja-hallintokorvaus"
                                (mapv
                                  (fn [rivi]
                                    (-> rivi
                                      (update :kulut #(mapv (fn [kulu]
                                                              (update kulu :pvm pvm/dateksi))
                                                        (konv/jsonb->clojuremap %)))))
                                  (muutos-kyselyt/hae-johto-ja-hallintokorvausmuutoksen-tiedot db {:id (:id muutos)
                                                                                                   :versio (:versio muutos)
                                                                                                   :urakka urakka-id}))

                                ;; tähän puuttuvien muutostyyppien lomakehaut...
                                [{}])
        _ (when (> (count tyypikohtaiset-tiedot)  1)
            (do
              (log/error "Muutoksia palautui lomakkeelle enemmän kuin yksi urakassa " urakka-id)
              (throw (Error. "Muutoksia palautui enemmän kuin yksi, kyseessä on ongelmatilanne. Ota yhteys Harja-palautteeseen."))))
        liitteet (when-not (empty? (:liite-idt muutos))
                   (liite-kyselyt/hae-liitteiden-tiedot db {:idt (:liite-idt muutos)
                                                            :urakka urakka-id}))
        vastaus (assoc (first tyypikohtaiset-tiedot) :liitteet liitteet)]
    vastaus))


(defn- poista-vanhat-kulutiedot!
  "Asettaa poistetuksi vanhat kulutiedot, jotta ne eivät näy käyttöliittymässä tai raporteissa."
  ;; halutaan saada historiatieto talteen, tämä on siihen käytännöllinen tapa tekemättä valtavaa refaktorointia kulu tauluun (ja sille omaa historiataulua ja triggereitä)
  [db user rivi]
  (let [kulu-id (:kulu-id rivi)]
    (when kulu-id
      (log/info "Poistetaan vanha kulu id:llä " kulu-id)
      (kulu-kyselyt/poista-kulu! db {:id kulu-id
                                     :kayttaja (:id user)})
      (kulu-kyselyt/poista-kulun-kohdistukset! db {:id kulu-id
                                                   :kayttaja (:id user)}))))

(defn- tallenna-johto-ja-hallintokorvauksen-muutokset
  "Tallentaa johto- ja hallintokorvauksen muutokset tietokantaan kuluiksi, linkittää muutokseen."
  [db user urakka muutos-id-ja-versio rivit]
  (log/debug "Tallennetaan johto- ja hallintokorvauksen muutokset: " rivit " muutos-id-ja-versio" muutos-id-ja-versio)
  (let [hoidon-johto-tpi-id (:id (first
                                      (tpi-q/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                        {:urakka (:id urakka)
                                         :koodi (mhu/toimenpide-avain->toimenpide :mhu-johto)})))]
    (doseq [rivi rivit
            :let [kulu {:urakka (:id urakka)
                        :erapaiva (:pvm rivi)
                        :numero (:numero rivi)
                        :kayttaja (:id user)
                        :lisatieto "Muutoksesta automaattisesti luotu kulu"
                        :laskun_numero (:laskun-numero rivi)
                        :koontilaskun-kuukausi (kulut-domain/pvm->koontilaskun-kuukausi (:pvm rivi) (:alkupvm urakka))
                        :kokonaissumma (:tavoitehinnan-muutos rivi)}
                  ;; 1.10.2025 ja jälkeen alkavissa urakassa vaaditaan negatiivinen summa näiden muutosten kuluissa
                  _ (when (and
                            (= :vahennys
                              (muutos-domain/jjh-korvaus-muutos-vai-vahennys? (:alkupvm urakka)))
                            (> (:tavoitehinnan-muutos rivi) 0))
                    (throw (Error. "1.10.2025 tai sen jälkeen alkavissa urakoissa Johto- ja hallintokorvausmuutoksen kulut voivat olla vain vähennyksiä eli miinusmerkkisiä.")))
                  ;; jotta saadaan talteen muutoshistoria, aina luodaan uusi kulu ja sen kohdistus, vanhat merkitään poistetuksi
                  kulu-id-db (:id (kulu-kyselyt/luo-kulu<! db kulu))
                  ;; luodaan aina uusi kohdistus, jos kyseessä "päivitys", poistetaan vanha kohdistus jotta jää historia talteen
                  kulu-kohdistus-db (muutos-kyselyt/luo-jjh-kulun-kohdistus<! db
                                      {:kulu kulu-id-db
                                       :summa (:tavoitehinnan-muutos rivi)
                                       :toimenpideinstanssi hoidon-johto-tpi-id
                                       :kayttaja (:id user)
                                       :tyyppi "jjh-muutos"})]]

      (muutos-kyselyt/luo-muutos-kulu-linkitys<! db {:versio (:versio muutos-id-ja-versio)
                                                     :muutos (:id muutos-id-ja-versio)
                                                     :kulu kulu-id-db})
      (poista-vanhat-kulutiedot! db user rivi))))

(defn- poista-muutos
  "Poistaa muutoksen."
  ;; Hox: tätä ei vielä käytetä. Jossain kohti tulee varmasti lomakkeelle poistaminen mahdolliseksi
  [db user muutos]
  (when (and (:id muutos) (:versio muutos))
    (log/debug "Poistetaan muutos id:llä " (:id muutos))
    (muutos-kyselyt/poista-muutos! db {:id (:id muutos)
                                       :versio (:versio muutos)
                                       :kayttaja (:id user)})))

(defn- tallenna-muutoksen-liitteet [db muutoksen-paluurivi liitteet]
  (doseq [liite liitteet]
    (let [liite-id (:id liite)
          muutos-id (:id muutoksen-paluurivi)
          muutos-versio (:versio muutoksen-paluurivi)]
      (when (and liite-id muutos-id muutos-versio)
        (muutos-kyselyt/linkita-muutos-ja-liite<! db {:muutos muutos-id
                                                      :liite liite-id
                                                      :versio muutos-versio})))))

(defn tallenna-muutos [db user {:keys [urakka-id valittu-hoitokausi muutos] :as tiedot}]
  (log/debug "tallenna-muutos: " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [urakka (first (q-urakat/hae-urakka db urakka-id))
        kulut (:kulut muutos)
        liitteet (:liitteet muutos)
        muutos {:id (:id muutos)
                :versio (:versio muutos)
                :urakka urakka-id
                :voimassa_alkaen (:voimassa_alkaen muutos)
                :nimi (:nimi muutos)
                :syy (:syy muutos)
                :kulu_kohdistus (:kulu-kohdistus muutos)
                :luonnos (:luonnos muutos)
                :hoitokauden_alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                :tyyppi (:tyyppi muutos)
                :kayttaja (:id user)}]
    (jdbc/with-db-transaction [db db]
      (let [muutos-paluurivi (if (:id muutos)
                               (muutos-kyselyt/paivita-muutos<! db muutos)
                               (muutos-kyselyt/luo-muutos<! db muutos))]
        (tallenna-muutoksen-liitteet db muutos-paluurivi liitteet)
        ;; hox: voi tehdä case:lla, kun myöhemmin tulee lisää tyyppejä
        (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
          (tallenna-johto-ja-hallintokorvauksen-muutokset db user urakka muutos-paluurivi kulut)))
    (hae-urakan-muutostiedot db user {:urakka-id urakka-id
                                      :valittu-hoitokausi valittu-hoitokausi}))))

(defn tallenna-rahavarausmuutosten-syyt
  [db user {:keys [urakka-id valittu-hoitokausi rivit]}]
  (log/debug "Tallenna rahavarausmuutosten syyt" urakka-id valittu-hoitokausi rivit)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))]
    (jdbc/with-db-transaction [db db]
      (doseq [{:keys [id syy]} rivit]
        (muutos-kyselyt/upsert-rahavarausmuutosten-syyt!
          db
          {:urakka urakka-id
           :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
           :rahavaraus_id id
           :syy syy
           :kayttaja (:id user)}))
      (hae-urakan-muutostiedot db user {:urakka-id urakka-id
                                        :valittu-hoitokausi valittu-hoitokausi}))))

(defrecord Muutos [asetukset]
  component/Lifecycle
  (start [this]
    (when (ominaisuus-kaytossa? :mhu-muutokset)
      (julkaise-palvelut
        (:http-palvelin this)

        :hae-urakan-muutostiedot
        (fn [user tiedot]
          (hae-urakan-muutostiedot (:db this) user tiedot))

        :hae-muutoksen-tiedot
        (fn [user tiedot]
          (hae-muutoksen-tiedot (:db this) user tiedot))

        :hae-tehtava-maaramuutokset
        (fn [user tiedot]
          (hae-tehtava-maaramuutokset (:db this) user tiedot))
        
        :hae-hoitovuosien-yksikkohinnat
        (fn [user tiedot]
          (hae-hoitovuosien-yksikkohinnat (:db this) user tiedot))
        
        :tallenna-tehtava-maaramuutokset
        (fn [user tiedot]
          (tallenna-tehtava-maaramuutokset (:db this) user tiedot))
        
        :tallenna-maaramuutos-yksikkohinta
        (fn [user tiedot]
          (tallenna-maaramuutos-yksikkohinta (:db this) user tiedot))
        
        :tallenna-muutos
        (fn [user tiedot]
          (tallenna-muutos (:db this) user tiedot))

        :tallenna-rahavarausmuutosten-syyt
        (fn [user tiedot]
          (tallenna-rahavarausmuutosten-syyt (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-urakan-muutostiedot
      :hae-muutoksen-tiedot
      :hae-tehtava-maaramuutokset
      :hae-hoitovuosien-yksikkohinnat
      :tallenna-muutos
      :tallenna-tehtava-maaramuutokset
      :tallenna-maaramuutos-yksikkohinta
      :tallenna-rahavarausmuutosten-syyt)
    this))
