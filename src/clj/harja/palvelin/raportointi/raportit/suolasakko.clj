(ns harja.palvelin.raportointi.raportit.suolasakko
  "Suolasakkoraportti"
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [jeesql.core :refer [defqueries]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defqueries "harja/kyselyt/suolasakkoraportti.sql"
  {:positional? true})

(defn- suolasakko-ei-osu-aikavalille?
  ; Suolasakko hoitokaudelle 2016-2017 laskutetaan yhtenä kk:na touko-syyskuun aikana vuonna 2017.
  ; Samposta tulee usein hoitourakat niin että urakan loppupvm on 31.12., vaikka oikeasti
  ; viimeinen hoitokausi urakassa loppuikin jo 30.9. Tällöin voi olla sama alueurakka kahteen kertaan
  ; elykohtaisessa raportissa. Korjataan tästä aiheutuva bugi HAR-6145 tutkimalla urakan loppupvm:ää tarkemmin.
  "Kertoo meneekö urakan suolasakon laskutuskk varmuudella ohi valitusta aikavälistä"
  [valitun-hkn-loppupvm urakka]
  (let [urakan-loppupvm (pvm/joda-timeksi (:loppupvm urakka))
        valitun-hoitokauden-loppuvuosi (pvm/vuosi valitun-hkn-loppupvm)
        ensimmainen-mahdollinen-suolasakon-laskutuskuukausi
        (pvm/joda-timeksi (pvm/->pvm (str "1.5." valitun-hoitokauden-loppuvuosi)))]
    (pvm/jalkeen? ensimmainen-mahdollinen-suolasakon-laskutuskuukausi urakan-loppupvm)))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm hallintayksikko-id urakkatyyppi]
                        :as parametrit}]
  (let [urakat (yleinen/hae-kontekstin-urakat db {:urakka urakka-id
                                                      :hallintayksikko hallintayksikko-id
                                                      :alku alkupvm
                                                      :loppu loppupvm
                                                      :urakkatyyppi #{"hoito" "teiden-hoito"}})
        urakat-aikavalin-sisalla (filter
                                   #(not (suolasakko-ei-osu-aikavalille? loppupvm %))
                                   urakat)
        urakka-idt (into #{}
                     (map :urakka-id)
                     urakat-aikavalin-sisalla)
        raportin-data (hae-suolasakot db {:alkupvm (konv/sql-date alkupvm)
                                          :loppupvm (konv/sql-date loppupvm)
                                          :urakat  urakka-idt})
        konteksti (cond
                    (and urakka-id alkupvm loppupvm)
                    :urakka

                    (and hallintayksikko-id alkupvm loppupvm)
                    :hallintayksikko

                    (and alkupvm loppupvm)
                    :koko-maa

                    :default
                    nil)

        suolasakot-hallintayksikoittain (sort (group-by :hallintayksikko_elynumero
                                                        raportin-data))
        raportin-nimi "Suolasakkoraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :viimeinen-rivi-yhteenveto? true
                 :oikealle-tasattavat-kentat (set (range 1 14))}
      [{:leveys 10 :otsikko "Urakka"}
       {:otsikko "Keski\u00ADlämpö\u00ADtila" :leveys 3 :fmt :numero}
       {:otsikko "Pitkän aikavälin keski\u00ADlämpö\u00ADtila" :leveys 3 :fmt :numero}
       {:otsikko "Talvi\u00ADsuolan max-määrä (t)" :leveys 4 :fmt :numero}
       {:otsikko "Bonus\u00ADraja (t)" :leveys 4 :fmt :numero}
       {:otsikko "Sakko\u00ADraja (t)" :leveys 4 :fmt :numero}
       {:otsikko "Kerroin" :leveys 3 :fmt :numero}
       {:otsikko "Kohtuul\u00ADlis\u00ADtarkis\u00ADtettu sakko\u00ADraja (t)" :leveys 4 :fmt :numero}
       {:otsikko "Käytetty suola\u00ADmäärä (t)" :leveys 5 :fmt :numero}
       {:otsikko "Suola\u00ADerotus (t)" :leveys 5 :fmt :numero}
       {:otsikko "Sakko/\u00ADbonus \u20AC / t" :leveys 4 :fmt :raha}
       {:otsikko "Sakko € / t" :leveys 4 :fmt :raha}
       {:otsikko "Sakko/\u00ADbonus €" :leveys 6 :fmt :raha}
       {:otsikko "Indeksi €" :leveys 5 :fmt :raha}
       {:otsikko "Indeksi\u00ADkorotettu sakko €" :leveys 6 :fmt :raha}]

      (keep identity
            (conj
              (into []
                    (apply concat
                           (for [[elynum hyn-suolasakot] suolasakot-hallintayksikoittain
                                 :let [elynimi (:hallintayksikko_nimi (first hyn-suolasakot))]]
                             (concat
                               (for [rivi hyn-suolasakot]
                                 [(:urakka_nimi rivi)
                                  (:keskilampotila rivi)
                                  (:pitkakeskilampotila rivi)
                                  (:sallittu_suolankaytto rivi)
                                  (:suolankayton_bonusraja rivi)
                                  (:suolankayton_sakkoraja rivi)
                                  [:varillinen-teksti {:arvo  (or (:kerroin rivi) "Lämpötila puuttuu")
                                                       :tyyli (when-not (:kerroin rivi) :virhe)}]
                                  (:sakkoraja rivi)
                                  (:suolankaytto rivi)
                                  (:erotus rivi)
                                  (:maara rivi)
                                  (:vainsakkomaara rivi)
                                  (:suolasakko rivi)
                                  (:korotus rivi)
                                  (:korotettuna rivi)])
                               ;; jos koko maan rapsa, näytä kunkin Hallintayksikön summarivi
                               (when (and (= :koko-maa konteksti) elynum)
                                 [{:lihavoi? true
                                   :rivi
                                   [(str elynum " " elynimi)
                                    nil
                                    nil
                                    (reduce + (keep :sallittu_suolankaytto hyn-suolasakot))
                                    (reduce + (keep :suolankayton_bonusraja hyn-suolasakot))
                                    (reduce + (keep :suolankayton_sakkoraja hyn-suolasakot))
                                    nil
                                    (reduce + (keep :sakkoraja hyn-suolasakot))
                                    (reduce + (keep :suolankaytto hyn-suolasakot))
                                    (reduce + (keep :erotus hyn-suolasakot))
                                    nil
                                    nil
                                    (reduce + (keep :suolasakko hyn-suolasakot))
                                    (reduce + (keep :korotus hyn-suolasakot))
                                    (reduce + (keep :korotettuna hyn-suolasakot))]}])))))
              (when (not (empty? raportin-data))
                ["Yhteensä"
                 nil
                 nil
                 (reduce + (keep :sallittu_suolankaytto raportin-data))
                 (reduce + (keep :suolankayton_bonusraja raportin-data))
                 (reduce + (keep :suolankayton_sakkoraja raportin-data))
                 nil
                 (reduce + (keep :sakkoraja raportin-data))
                 (reduce + (keep :suolankaytto raportin-data))
                 (reduce + (keep :erotus raportin-data))
                 nil
                 nil
                 (reduce + (keep :suolasakko raportin-data))
                 (reduce + (keep :korotus raportin-data))
                 (reduce + (keep :korotettuna raportin-data))])))]
     [:teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä. Formiaatteja ei lasketa talvisuolan kokonaiskäyttöön."]]))
