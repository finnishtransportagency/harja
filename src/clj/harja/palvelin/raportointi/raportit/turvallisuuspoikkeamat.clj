(ns harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat
  "Turvallisuusraportti koostuu kolmesta elementistä. Elementtien sisältö riippuu kontekstista.

  URAKAN KONTEKSTI:

  Ensimmäisessä taulukossa on laskettu yhteen eri tyypisten turvallisuuspoikkeamien
  lukumäärät tietyllä aikavälillä. Taulukossa on kaksi saraketta, Tyyppi (esim. Työtapaturma)
  ja Määrä. Lopussa on yhteenvetorivi.

  Toinen elemntti on palkki, jolla ilmaistaan, milloin turpot on kirjattu. y-akseli
  on lukumäärä, x-akseli on päivämäärä.

  Viimeisessä taulukossa on turvallisuuspoikkeamat listana. Sarakkeet ovat
  Pvm, Tyyppi, Vakavuusaste, Ammatti, Sairaalavuorokaudet, Sairauspoissaolopäivät.
  Viimeinen rivi on yhteenvetorivi em. vuorokausista ja poissaloista.

  HALLINTAYKSIKÖN KONTEKSTI:

  Sama kuin urakan konteksti, paitsi jos on valittu \"Urakat eriteltynä\".

  Jos on valittu, kahteen taulukkoon lisätään ensimmäiseksi sarakkeeksi urakan nimi.

  KOKO MAAN KONTEKSTI:

  Koko maan konteksissa palkit pysyvät samana. Kaksi taulukkoa eroavat huomattavasti.

  Ensimmäisessä taulukossa on jälleen ilmaistu eri tyyppisten turpojen lukumäärä,
  mutta tällä kertaa Tyypit ovat sarakkeita. Sarakkeet ovat siis [Alue], Työtapaturmant, Vaaratilanteet,
  Turvallisuushavainnot, Muut. [Alue] on joko Hallintayksikkö tai Urakka, riippuen, onko
  erittely valittu. Lopussa on yhteenvetorivi, jossa Alueena on Koko Maa, ja eri tyypit
  laskettu yhteen.

  Toinen taulukko on \"Turvallisuuspoikkeamat vakavuusasteittain\". Sarakkeet ovat [Alue],
   Lievät, Vakavat. Lopussa on jälleen koko maan yhteenvetorivi.
  "
  (:require [clojure.string :as str]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.turvallisuuspoikkeama :as turpodomain]
            [harja.domain.raportointi :refer [info-solu]]
            [harja.domain.urakka :as urakka]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi raportin-otsikko vuosi-ja-kk
                                                                 vuosi-ja-kk-fmt kuukaudet
                                                                 pylvaat-kuukausittain
                                                                 naytettavat-alueet]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [clojure.string :as str]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(defqueries "harja/palvelin/raportointi/raportit/turvallisuuspoikkeamat.sql"
            {:positional? true})

(def turvallisuuspoikkeama-tyyppi
  {"tyotapaturma" "Ty\u00ADö\u00ADta\u00ADpa\u00ADtur\u00ADma"
   "vaaratilanne" "Vaa\u00ADra\u00ADti\u00ADlan\u00ADne"
   "turvallisuushavainto" "Tur\u00ADval\u00ADli\u00ADsuus\u00ADha\u00ADvain\u00ADto"
   "muu" "Muu"})

(defn- ilmoituksen-tyyppi [{tyyppi :tyyppi}]
  (into {}
        (map (juxt identity (constantly 1)))
        tyyppi))

(defn- turvallisuuspoikkeamat-pylvaat [turpot alkupvm loppupvm]
  (let [turpo-maarat-kuukausittain (group-by
                                     (comp vuosi-ja-kk :tapahtunut)
                                     turpot)
        turpomaarat-tyypeittain (reduce-kv
                                  (fn [tulos kk turpot]
                                    (let [maarat (reduce (fn [eka toka]
                                                           (merge-with + eka toka))
                                                         (map ilmoituksen-tyyppi turpot))]
                                      (assoc tulos
                                        kk
                                        [(get maarat "tyotapaturma")
                                         (get maarat "vaaratilanne")
                                         (get maarat "turvallisuushavainto")
                                         (get maarat "muu")])))
                                  {} turpo-maarat-kuukausittain)]
    (when (and (not= (vuosi-ja-kk alkupvm) (vuosi-ja-kk loppupvm))
               (> (count turpot) 0))
      (pylvaat-kuukausittain {:otsikko "Turvallisuuspoikkeamat kuukausittain"
                              :alkupvm alkupvm :loppupvm loppupvm
                              :kuukausittainen-data turpomaarat-tyypeittain
                              :piilota-arvo? #{0}
                              :legend ["Työtapaturmat" "Vaaratilanteet" "Turvallisuushavainnot" "Muut"]}))))

(defn- turvallisuuspoikkeamat-tyypeittain-rivit [turpot urakoittain?]
  (let [turporivi (fn [[urakka turpot]]
                    (let [turpo-maarat-per-tyyppi (frequencies (mapcat :tyyppi turpot))]
                      [(rivi (:nimi urakka) "Työtapaturma" (or (turpo-maarat-per-tyyppi "tyotapaturma") 0))
                       (rivi (:nimi urakka) "Vaaratilanne" (or (turpo-maarat-per-tyyppi "vaaratilanne") 0))
                       (rivi (:nimi urakka) "Turvallisuushavainto" (or (turpo-maarat-per-tyyppi "turvallisuushavainto") 0))
                       (rivi (:nimi urakka) "Muu" (or (turpo-maarat-per-tyyppi "muu") 0))]))]
    (concat (mapcat turporivi
                    (if urakoittain?
                      (group-by :urakka turpot)
                      [[nil turpot]]))
            (if urakoittain?
              [(rivi "Yksittäisiä ilmoituksia yhteensä" "" (count turpot))]
              [(rivi "Yksittäisiä ilmoituksia yhteensä" (count turpot))]))))

(defn- turvallisuuspoikkeamat-tyypeittain-sarakkeet [urakoittain?]
  (into []
        (concat (when urakoittain?
                  [{:otsikko "Urakka"}])
                [{:otsikko "Tyyppi"}
                 {:otsikko "Määrä"}])))

(defn- turvallisuuspoikkeamat-tyypeittain-koko-maan-rivit [turpot urakoittain? naytettavat-alueet]
  (let [turpo-maarat-per-tyyppi (fn [turpot tyyppi]
                                  (count (filter
                                           (fn [turpo]
                                             (some #(= % tyyppi) (:tyyppi turpo)))
                                           turpot)))
        hallintayksikon-turpot (fn [turpot alue]
                                 (filter
                                   #(= (get-in % [:hallintayksikko :id])
                                       (:hallintayksikko-id alue))
                                   turpot))
        turporivi (fn [turpot alue]
                    [(:nimi alue)
                     (or (turpo-maarat-per-tyyppi turpot "tyotapaturma") 0)
                     (or (turpo-maarat-per-tyyppi turpot "vaaratilanne") 0)
                     (or (turpo-maarat-per-tyyppi turpot "turvallisuushavainto") 0)
                     (or (turpo-maarat-per-tyyppi turpot "muu") 0)])]
    (concat (if urakoittain?
              (mapv
                (fn [alue]
                  (let [alueen-turpot (filter
                                        #(= (get-in % [:urakka :id])
                                            (:id alue))
                                        turpot)]
                    (turporivi alueen-turpot alue)))
                (distinct (mapv :urakka turpot)))
              (mapv
                (fn [alue]
                  (let [alueen-turpot (hallintayksikon-turpot turpot alue)]
                    (turporivi alueen-turpot alue)))
                naytettavat-alueet))

            [(turporivi turpot {:nimi "Koko maa"})])))

(defn- turvallisuuspoikkeamat-tyypeittain-koko-maan-sarakkeet [urakoittain?]
  (into []
        (concat (if urakoittain?
                  [{:otsikko "Urakka"}]
                  [{:otsikko "Hallintayksikkö"}])
                [{:otsikko "Työtapaturmat"}
                 {:otsikko "Vaaratilanteet"}
                 {:otsikko "Turvallisuushavainnot"}
                 {:otsikko "Muut"}])))

(defn- turvallisuuspoikkeamat-vakavuusasteittain-koko-maan-rivit [turpot urakoittain? naytettavat-alueet]
  (let [turpo-maarat-per-vakavuusaste (fn [turpot vakavuusaste]
                                        (count (filter #(= (:vakavuusaste %) vakavuusaste) turpot)))
        hallintayksikon-turpot (fn [turpot alue]
                                 (filter
                                   #(= (get-in % [:hallintayksikko :id])
                                       (:hallintayksikko-id alue))
                                   turpot))
        turporivi (fn [turpot alue]
                    [(:nimi alue)
                     (or (turpo-maarat-per-vakavuusaste turpot :lieva) 0)
                     (or (turpo-maarat-per-vakavuusaste turpot :vakava) 0)])]
    (concat (if urakoittain?
              (mapv
                (fn [alue]
                  (let [alueen-turpot (filter
                                        #(= (get-in % [:urakka :id])
                                            (:id alue))
                                        turpot)]
                    (turporivi alueen-turpot alue)))
                (distinct (mapv :urakka turpot)))
              (mapv
                (fn [alue]
                  (let [alueen-turpot (hallintayksikon-turpot turpot alue)]
                    (turporivi alueen-turpot alue)))
                naytettavat-alueet))

            [(turporivi turpot {:nimi "Koko maa"})])))

(defn- turvallisuuspoikkeamat-vakavuusasteittain-koko-maan-sarakkeet [urakoittain?]
  (into []
        (concat (if urakoittain?
                  [{:otsikko "Urakka"}]
                  [{:otsikko "Hallintayksikkö"}])
                [{:otsikko "Lievät"}
                 {:otsikko "Vakavat"}])))

(defn- turvallisuuspoikkeamat-listana-rivit [turpot urakoittain?]
  (keep identity
        (conj (mapv #(rivi (if urakoittain?
                             (:nimi (:urakka %))
                             nil)
                           (pvm/pvm-aika (:tapahtunut %))
                           (str/join ", " (map turvallisuuspoikkeama-tyyppi (:tyyppi %)))
                           (or (turpodomain/turpo-vakavuusasteet (:vakavuusaste %)) "")
                           (or (turpodomain/kuvaile-tyontekijan-ammatti %) "")
                           (or (:sairaalavuorokaudet %) "")
                           (or (:sairauspoissaolopaivat %) ""))

                    (sort-by :tapahtunut #(t/after? (c/from-sql-time %1)
                                                    (c/from-sql-time %2)) turpot))
              (when (not (empty? turpot))
                (if urakoittain?
                  (rivi "Yhteensä" "" "" ""  ""
                        (reduce + 0 (keep :sairaalavuorokaudet turpot))
                        (reduce + 0 (keep :sairauspoissaolopaivat turpot)))
                  (rivi "Yhteensä" "" "" ""
                        (reduce + 0 (keep :sairaalavuorokaudet turpot))
                        (reduce + 0 (keep :sairauspoissaolopaivat turpot))))))))

(defn- turvallisuuspoikkeamat-listana-sarakkeet [urakoittain?]
  (into []
        (concat (when urakoittain?
                  [{:otsikko "Urakka" :leveys 14}])
                [{:otsikko "Pvm" :leveys 14 :fmt :pvm-aika}
                 {:otsikko "Tyyppi" :leveys 24}
                 {:otsikko "Vakavuus\u00ADaste" :leveys 15}
                 {:otsikko "Ammatti" :leveys 14}
                 {:otsikko "Sairaala\u00advuoro\u00ADkaudet" :leveys 9 :fmt :numero}
                 {:otsikko "Sairaus\u00adpoissa\u00ADolo\u00adpäivät" :leveys 9 :fmt :numero}])))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id urakoittain?
                               alkupvm loppupvm urakkatyyppi] :as parametrit}]
  (log/info "PARAMS: " parametrit)
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        urakkatyyppi (when urakkatyyppi
                       (if (= urakkatyyppi :vesivayla)
                         (into [] urakka/vesivayla-urakkatyypit)
                         (if (= urakkatyyppi :hoito)
                           ["hoito" "teiden-hoito"]
                           [urakkatyyppi])))
        ;; Näytettävät alueet (hallintayksiköt) koko maan raporttia varten
        naytettavat-alueet (naytettavat-alueet
                             db
                             konteksti
                             {:urakka urakka-id
                              :hallintayksikko hallintayksikko-id
                              :urakkatyyppi (when urakkatyyppi (mapv name urakkatyyppi))
                              :alku alkupvm
                              :loppu loppupvm})
        _ (log/debug "HAE TURPOT: ")
        _ (log/debug (pr-str {:urakka_annettu (some? urakka-id)
                              :urakka urakka-id
                              :hallintayksikko_annettu (some? hallintayksikko-id)
                              :hallintayksikko hallintayksikko-id
                              :urakkatyyppi (when urakkatyyppi (mapv name urakkatyyppi))
                              :alku alkupvm
                              :loppu loppupvm}))
        turpot (into []
                     (comp
                       (map #(konv/array->vec % :tyyppi))
                       (map #(konv/string->keyword % :vakavuusaste))
                       (map #(konv/string->keyword % :tyontekijanammatti))
                       (map konv/alaviiva->rakenne))
                     (hae-turvallisuuspoikkeamat db
                                                 {:urakka_annettu (some? urakka-id)
                                                  :urakka urakka-id
                                                  :hallintayksikko_annettu (some? hallintayksikko-id)
                                                  :hallintayksikko hallintayksikko-id
                                                  :urakkatyyppi (when urakkatyyppi (mapv name urakkatyyppi))
                                                  :alku alkupvm
                                                  :loppu loppupvm}))
        raportin-nimi "Turvallisuusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko otsikko :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi raportin-nimi}
      (if (= konteksti :koko-maa)
        (turvallisuuspoikkeamat-tyypeittain-koko-maan-sarakkeet urakoittain?)
        (turvallisuuspoikkeamat-tyypeittain-sarakkeet urakoittain?))
      (if (= konteksti :koko-maa)
        (turvallisuuspoikkeamat-tyypeittain-koko-maan-rivit turpot urakoittain? naytettavat-alueet)
        (turvallisuuspoikkeamat-tyypeittain-rivit turpot urakoittain?))]

     (when (= konteksti :koko-maa)
       [:taulukko {:otsikko (str "Turvallisuuspoikkeamat vakavuusasteittain")
                   :viimeinen-rivi-yhteenveto? (= :koko-maa konteksti)}
        (turvallisuuspoikkeamat-vakavuusasteittain-koko-maan-sarakkeet urakoittain?)
        (turvallisuuspoikkeamat-vakavuusasteittain-koko-maan-rivit turpot urakoittain? naytettavat-alueet)])

     (turvallisuuspoikkeamat-pylvaat turpot alkupvm loppupvm)

     (when (not= konteksti :koko-maa)
       [:taulukko {:otsikko (str "Turvallisuuspoikkeamat listana: " (count turpot) " kpl")
                   :viimeinen-rivi-yhteenveto? true}
        (turvallisuuspoikkeamat-listana-sarakkeet urakoittain?)
        (turvallisuuspoikkeamat-listana-rivit turpot urakoittain?)])]))
