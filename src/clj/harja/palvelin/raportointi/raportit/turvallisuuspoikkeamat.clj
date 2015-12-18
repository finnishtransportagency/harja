(ns harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat
  (:require [clojure.string :as str]
            [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet pylvaat]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [harja.pvm :as pvm]))

(defqueries "harja/palvelin/raportointi/raportit/turvallisuuspoikkeamat.sql")


(def turvallisuuspoikkeama-tyyppi
  {"turvallisuuspoikkeama" "Turvallisuuspoikkeama"
   "tyoturvallisuuspoikkeama" "Työturvallisuuspoikkeama"
   "prosessipoikkeama" "Prosessipoikkeama"})

(defn ilmoituksen-tyyppi [{tyyppi :tyyppi}]
  (into {}
        (map (juxt identity (constantly 1)))
        tyyppi))

(defn rivi [& asiat]
  (into [] (keep identity asiat)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id urakoittain?
                               alkupvm loppupvm] :as parametrit}]
  (log/info "PARAMS: " parametrit)
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        turpot (into []
                     (comp 
                      (map #(konv/array->vec % :tyyppi))
                      (map konv/alaviiva->rakenne))
                     (hae-turvallisuuspoikkeamat db 
                                                 (if urakka-id true false) urakka-id
                                                 (if hallintayksikko-id true false) hallintayksikko-id
                                                 alkupvm loppupvm))
        turpo-maarat-kuukausittain (group-by
                                     (comp vuosi-ja-kk :tapahtunut)
                                     turpot)
        _ (log/debug "turpot" turpot)
        _ (log/debug "turpo määrät kuukausittain" turpo-maarat-kuukausittain)
        turpomaarat-tyypeittain (reduce-kv
                                  (fn [tulos kk turpot]
                                    (let [maarat (reduce (fn [eka toka] (merge-with + eka toka))
                                                         (map ilmoituksen-tyyppi turpot))
                                          _ (log/debug "TURPO turpot: " turpot)
                                          _ (log/debug "TURPO määrät: " maarat)]
                                      (assoc tulos
                                        kk
                                        [(get maarat "turvallisuuspoikkeama")
                                         (get maarat "prosessipoikkeama")
                                         (get maarat "tyoturvallisuuspoikkeama")])))
                                  {} turpo-maarat-kuukausittain)
        _ (log/debug "turpomaarat-tyypeittain" turpomaarat-tyypeittain)
        raportin-nimi "Turvallisuusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko otsikko :viimeinen-rivi-yhteenveto? true}
      (into []
            (concat (when urakoittain?
                      [{:otsikko "Urakka"}])
                    [{:otsikko "Tyyppi"}
                     {:otsikko "Määrä"}]))
      
      (concat (mapcat (fn [[urakka turpot]]
                        (let [turpo-maarat-per-tyyppi (frequencies (mapcat :tyyppi turpot))]
                          [(rivi (:nimi urakka) "Turvallisuuspoikkeama" (or (turpo-maarat-per-tyyppi "turvallisuuspoikkeama") 0))
                           (rivi (:nimi urakka) "Prosessipoikkeama" (or (turpo-maarat-per-tyyppi "prosessipoikkeama") 0))
                           (rivi (:nimi urakka) "Työturvallisuuspoikkeama" (or (turpo-maarat-per-tyyppi "tyoturvallisuuspoikkeama") 0))]))
                      (if urakoittain?
                        (group-by :urakka turpot)
                        [[nil turpot]]))
              [(rivi (when urakoittain? "") "Yksittäisiä ilmoituksia yhteensä" (count turpot))])]
     
     (when (and (not= (vuosi-ja-kk alkupvm) (vuosi-ja-kk loppupvm))
                (> (count turpot) 0))
       (pylvaat {:otsikko "Turvallisuuspoikkeamat kuukausittain"
                 :alkupvm alkupvm :loppupvm loppupvm
                 :kuukausittainen-data turpomaarat-tyypeittain
                 :piilota-arvo? #{0}
                 :legend               ["Turvallisuuspoikkeamat" "Prosessipoikkeamat" "Työturvallisuuspoikkeamat"]}))
     [:taulukko {:otsikko (str "Turvallisuuspoikkeamat listana: " (count turpot) " kpl")
                 :viimeinen-rivi-yhteenveto? true}
      (into []
            (concat (when urakoittain?
                      [{:otsikko "Urakka"}])
                    [{:otsikko "Pvm"}
                     {:otsikko "Tyyppi"}
                     {:otsikko "Ammatti"}
                     {:otsikko "Työtehtävä"}
                     {:otsikko "Sairaala\u00advuorokaudet"}
                     {:otsikko "Sairaus\u00adpoissaolot\u00adpäivät"}]))

      (conj (mapv #(rivi (if urakoittain? (:nimi (:urakka %)) nil)
                         (pvm/pvm-aika (:tapahtunut %))
                         (str/join ", " (map turvallisuuspoikkeama-tyyppi (:tyyppi %)))
                         (:tyontekijanammatti %) (:tyotehtava %)
                         (:sairaalavuorokaudet %) (:sairauspoissaolopaivat %))

                  (sort-by :tapahtunut turpot))
            (rivi (if urakoittain? "" nil) "" "" "" "Yhteensä"
                  (reduce + 0 (keep :sairaalavuorokaudet turpot))
                  (reduce + 0 (keep :sairauspoissaolopaivat turpot))))]]))
