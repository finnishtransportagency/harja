(ns harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat
  (:require [clojure.string :as str]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.turvallisuuspoikkeamat :as turpodomain]
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
    (if (and (not= (vuosi-ja-kk alkupvm) (vuosi-ja-kk loppupvm))
             (> (count turpot) 0))
      (pylvaat-kuukausittain {:otsikko "Turvallisuuspoikkeamat kuukausittain"
                              :alkupvm alkupvm :loppupvm loppupvm
                              :kuukausittainen-data turpomaarat-tyypeittain
                              :piilota-arvo? #{0}
                              :legend ["Työtapaturmat" "Vaaratilanteet" "Turvallisuushavainnot" "Muut"]})
      ;; estää nillin pääsyn PDF:ään
      [:teksti ""])))

(defn- turvallisuuspoikkeamat-rivit [turpot urakoittain? konteksti naytettavat-alueet]
  (let [turporivi (fn [[urakka turpot]]
                    (let [turpo-maarat-per-tyyppi (frequencies (mapcat :tyyppi turpot))]
                      [(rivi (:nimi urakka) "Työtapaturma" (or (turpo-maarat-per-tyyppi "tyotapaturma") 0))
                       (rivi (:nimi urakka) "Vaaratilanne" (or (turpo-maarat-per-tyyppi "vaaratilanne") 0))
                       (rivi (:nimi urakka) "Turvallisuushavainto" (or (turpo-maarat-per-tyyppi "turvallisuushavainto") 0))
                       (rivi (:nimi urakka) "Muu" (or (turpo-maarat-per-tyyppi "muu") 0))]))]
    (concat (if (= konteksti :koko-maa)
              (let [hallintayksikon-turpot (fn [turpot] (filter
                                                          #(= (get-in % [:hallintayksikko :id]))
                                                          turpot))]
                (mapcat
                 (fn [alue]
                   (concat [{:otsikko (:nimi alue)}]
                           (mapcat turporivi
                                   (if urakoittain?
                                     (group-by :urakka (hallintayksikon-turpot turpot))
                                     [[nil (hallintayksikon-turpot turpot)]]))))
                 naytettavat-alueet))
             (mapcat turporivi
                     (if urakoittain?
                       (group-by :urakka turpot)
                       [[nil turpot]])))
            (if urakoittain?
               [(rivi "Yksittäisiä ilmoituksia yhteensä" "" (count turpot))]
               [(rivi "Yksittäisiä ilmoituksia yhteensä" (count turpot))]))))

(defn- turvallisuuspoikkeamat-sarakkeet [urakoittain?]
  (into []
        (concat (when urakoittain?
                  [{:otsikko "Urakka"}])
                [{:otsikko "Tyyppi"}
                 {:otsikko "Määrä"}])))

(defn- turvallisuuspoikkeamat-listana-rivit [turpot urakoittain?]
  (keep identity
        (conj (mapv #(rivi (if urakoittain?
                             (:nimi (:urakka %))
                             nil)
                           (pvm/pvm-aika (:tapahtunut %))
                           (str/join ", " (map turvallisuuspoikkeama-tyyppi (:tyyppi %)))
                           (or (turpodomain/turpo-vakavuusasteet (:vakavuusaste %)) "")
                           (or (turpodomain/kuvaile-tyontekijan-ammatti %) "")
                           (or (:tyotehtava %) "")
                           (or (:sairaalavuorokaudet %) "")
                           (or (:sairauspoissaolopaivat %) ""))

                    (sort-by :tapahtunut #(t/after? (c/from-sql-time %1)
                                                    (c/from-sql-time %2)) turpot))
              (when (not (empty? turpot))
                (if urakoittain?
                  (rivi "Yhteensä" "" "" "" "" ""
                        (reduce + 0 (keep :sairaalavuorokaudet turpot))
                        (reduce + 0 (keep :sairauspoissaolopaivat turpot)))
                  (rivi "Yhteensä" "" "" "" ""
                        (reduce + 0 (keep :sairaalavuorokaudet turpot))
                        (reduce + 0 (keep :sairauspoissaolopaivat turpot))))))))

(defn- turvallisuuspoikkeamat-listana-sarakkeet [urakoittain?]
  (into []
        (concat (when urakoittain?
                  [{:otsikko "Urakka" :leveys 14}])
                [{:otsikko "Pvm" :leveys 14}
                 {:otsikko "Tyyppi" :leveys 24}
                 {:otsikko "Vakavuusaste" :leveys 15}
                 {:otsikko "Ammatti" :leveys 14}
                 {:otsikko "Työ\u00ADtehtävä" :leveys 14}
                 {:otsikko "Sairaala\u00advuoro\u00ADkaudet" :leveys 9}
                 {:otsikko "Sairaus\u00adpoissa\u00ADolo\u00adpäivät" :leveys 9}])))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id urakoittain?
                               alkupvm loppupvm urakkatyyppi] :as parametrit}]
  (log/info "PARAMS: " parametrit)
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-alueet (naytettavat-alueet
                             db
                             konteksti
                             {:urakka urakka-id
                              :hallintayksikko hallintayksikko-id
                              :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                              :alku alkupvm
                              :loppu loppupvm})
        turpot (into []
                     (comp
                       (map #(konv/array->vec % :tyyppi))
                       (map #(konv/string->keyword % :vakavuusaste))
                       (map #(konv/string->keyword % :tyontekijanammatti))
                       (map konv/alaviiva->rakenne))
                     (hae-turvallisuuspoikkeamat db
                                                 (some? urakka-id) urakka-id
                                                 (some? hallintayksikko-id) hallintayksikko-id
                                                 (when urakkatyyppi (name urakkatyyppi))
                                                 alkupvm loppupvm))
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
      (turvallisuuspoikkeamat-sarakkeet urakoittain?)
      (turvallisuuspoikkeamat-rivit turpot urakoittain? konteksti naytettavat-alueet)]

     (turvallisuuspoikkeamat-pylvaat turpot alkupvm loppupvm)

     [:taulukko {:otsikko (str "Turvallisuuspoikkeamat listana: " (count turpot) " kpl")
                 :viimeinen-rivi-yhteenveto? true}
      (turvallisuuspoikkeamat-listana-sarakkeet urakoittain?)
      (turvallisuuspoikkeamat-listana-rivit turpot urakoittain?)]]))
