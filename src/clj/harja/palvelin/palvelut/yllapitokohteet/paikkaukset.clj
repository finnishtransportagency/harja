(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset
  (:require [com.stuartsierra.component :as component]
            [specql.op :as op]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.paikkaus :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn- muodosta-tr-ehdot
  [tr]
  (let [tr (into {} (filter (fn [[_ arvo]] arvo) tr))
        ;; Täytyy luoda yhteinen pohja, koska nämä kaikki ehdot yhdistetään OR:lla
        alun-yhteinen-pohja (cond-> {}
                               (:loppuosa tr) (assoc ::tierekisteri/losa (op/<= (:loppuosa tr)))
                               (:loppuetaisyys tr) (assoc ::tierekisteri/let (op/<= (:loppuetaisyys tr)))
                               (:alkuosa tr) (assoc ::tierekisteri/aosa (op/>= (:alkuosa tr)))
                               (:numero tr) (assoc ::tierekisteri/tie (:numero tr)))
        ;; Jos molemmat, alkuosa ja alkuetaisyys ovat annettuna, tarvitaan ehdot 1 ja 3. Muutenhan nuo voisi yhdistää.
        ehto-1 (when (:alkuosa tr)
                 (assoc alun-yhteinen-pohja ::tierekisteri/aosa ((if (:alkuetaisyys tr)
                                                              op/>
                                                              op/>=)
                                                             (:alkuosa tr))))
        ehto-2 (when (:alkuetaisyys tr)
                 (assoc alun-yhteinen-pohja ::tierekisteri/aet (op/>= (:alkuetaisyys tr))))
        ehto-3 (when (and (:alkuosa tr) (:alkuetaisyys tr))
                 (assoc alun-yhteinen-pohja ::tierekisteri/aosa (:alkuosa tr)
                                            ::tierekisteri/aet (op/>= (:alkuetaisyys tr))))

        lopun-yhteinen-pohja (cond-> {}
                                     (:alkuosa tr) (assoc ::tierekisteri/aosa (op/>= (:alkuosa tr)))
                                     (:alkuetaisyys tr) (assoc ::tierekisteri/aet (op/>= (:alkuetaisyys tr)))
                                     (:loppuosa tr) (assoc ::tierekisteri/losa (op/<= (:loppuosa tr)))
                                     (:numero tr) (assoc ::tierekisteri/tie (:numero tr)))
        ehto-4 (when (:loppuosa tr)
                 (assoc lopun-yhteinen-pohja ::tierekisteri/losa ((if (:loppuetaisyys tr)
                                                                    op/<
                                                                    op/<=)
                                                                   (:loppuosa tr))))
        ehto-5 (when (:loppuetaisyys tr)
                 (assoc lopun-yhteinen-pohja ::tierekisteri/let (op/<= (:loppuetaisyys tr))))
        ehto-6 (when (and (:loppuosa tr) (:loppuetaisyys tr))
                 (assoc lopun-yhteinen-pohja ::tierekisteri/losa (:loppuosa tr)
                                             ::tierekisteri/let (op/<= (:loppuetaisyys tr))))
        ehto-7 (when (= '(:numero) (keys tr))
                 {::tierekisteri/tie (:numero tr)})]
    (apply op/or
           (keep #(when-not (nil? %)
                    {::paikkaus/tierekisteriosoite %})
                 [ehto-1 ehto-2 ehto-3 ehto-4 ehto-5 ehto-6 ehto-7]))))

(defn hae-urakan-paikkauskohteet [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-urakka-id (select-keys tiedot #{::paikkaus/urakka-id})
        kysely-params-tieosa (when-let [tr (into {} (filter (fn [[_ arvo]] arvo) (:tr tiedot)))]
                               (when (not (empty? tr))
                                 (muodosta-tr-ehdot tr)))
        kysely-params-aika (if-let [aikavali (:aikavali tiedot)]
                             (assoc kysely-params-urakka-id ::paikkaus/alkuaika (cond
                                                                                  (and (first aikavali) (not (second aikavali))) (op/>= (first aikavali))
                                                                                  (and (not (first aikavali)) (second aikavali)) (op/<= (second aikavali))
                                                                                  :else (apply op/between aikavali)))
                             kysely-params-urakka-id)
        kysely-params-paikkaus-idt (if-let [paikkaus-idt (:paikkaus-idt tiedot)]
                                     (assoc kysely-params-aika ::paikkaus/paikkauskohde {::paikkaus/id (op/in paikkaus-idt)})
                                     kysely-params-aika)
        kysely-params (if kysely-params-tieosa
                        (op/and kysely-params-paikkaus-idt
                                kysely-params-tieosa)
                        kysely-params-paikkaus-idt)]
    (q/hae-paikkaukset db kysely-params)))

(defn hae-paikkausurakan-kustannukset [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-template {:alkuosa nil :numero nil :urakka-id nil :loppuaika nil :alkuaika nil
                                :alkuetaisyys nil :loppuetaisyys nil :loppuosa nil :paikkaus-idt nil}]
    (if (and (not (nil? (:paikkaus-idt tiedot)))
             (empty? (:paikkaus-idt tiedot)))
      []
      (q/hae-paikkaustoteumat-tierekisteriosoitteella db (assoc (merge kysely-params-template (:tr tiedot))
                                                           :urakka-id (::paikkaus/urakka-id tiedot)
                                                           :paikkaus-idt (:paikkaus-idt tiedot)
                                                           :alkuaika (first (:aikavali tiedot))
                                                           :loppuaika (second (:aikavali tiedot)))))))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-urakan-paikkauskohteet
                        (fn [user tiedot]
                          (hae-urakan-paikkauskohteet db user tiedot))
                        {:kysely-spec ::paikkaus/urakan-paikkauskohteet-kysely
                         :vastaus-spec ::paikkaus/urakan-paikkauskohteet-vastaus})
      (julkaise-palvelu http :hae-paikkausurakan-kustannukset
                        (fn [user tiedot]
                          (hae-paikkausurakan-kustannukset db user tiedot))
                        {:kysely-spec ::paikkaus/paikkausurakan-kustannukset-kysely
                         :vastaus-spec ::paikkaus/paikkausurakan-kustannukset-vastaus})
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-paikkauskohteet
      :hae-paikkausurakan-kustannukset)
    this))