(ns harja.palvelin.raportointi.raportit.suolasakko
  "Suolasakkoraportti"
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [yesql.core :refer [defqueries]]
            [harja.fmt :as fmt]))

(defqueries "harja/kyselyt/suolasakkoraportti.sql")

(defn muodosta-suolasakkoraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan tiedot suolasakon raportille urakka-kontekstissa: " urakka-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [parametrit [db
                    urakka-id
                    (konv/sql-timestamp alkupvm)
                    (konv/sql-timestamp loppupvm)
                    (+ (.getYear (konv/sql-timestamp alkupvm)) 1900)
                    (+ (.getYear (konv/sql-timestamp loppupvm)) 1900)]
        raportin-tiedot (into [] (apply hae-tiedot-urakan-suolasakkoraportille parametrit))]
    (log/debug (str "Raporttidata saatu: " (pr-str raportin-tiedot)))
    raportin-tiedot))


(defn muodosta-suolasakkoraportti-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (log/debug "Haetaan tiedot suolasakon raportille hallintayksikkö-kontekstissa: " hallintayksikko-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [parametrit [db
                    hallintayksikko-id
                    (konv/sql-timestamp alkupvm)
                    (konv/sql-timestamp loppupvm)
                    (+ (.getYear (konv/sql-timestamp alkupvm)) 1900)
                    (+ (.getYear (konv/sql-timestamp loppupvm)) 1900)]
        raportin-tiedot (into [] (apply hae-tiedot-hallintayksikon-suolasakkoraportille parametrit))]
    (log/debug (str "Raporttidata saatu: " (pr-str raportin-tiedot)))
    raportin-tiedot))

(defn muodosta-suolasakkoraportti-koko-maalle [db user {:keys [alkupvm loppupvm]}]
  (log/debug "Haetaan tiedot suolasakon raportille koko maa -kontekstissa: " alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [parametrit [db
                    (konv/sql-timestamp alkupvm)
                    (konv/sql-timestamp loppupvm)
                    (+ (.getYear (konv/sql-timestamp alkupvm)) 1900)
                    (+ (.getYear (konv/sql-timestamp loppupvm)) 1900)]
        raportin-tiedot (into [] (apply hae-tiedot-koko-maan-suolasakkoraportille parametrit))]
    (log/debug (str "Raporttidata saatu: " (pr-str raportin-tiedot)))
    raportin-tiedot))

(defn suorita [db user {:keys [urakka-id hk-alkupvm hk-loppupvm hallintayksikko-id] :as parametrit}]
  (log/debug "Ajat:" (pr-str hk-alkupvm hk-loppupvm))
  (let [[konteksti raportin-data]
        (cond
          (and urakka-id hk-alkupvm hk-loppupvm)
          [:urakka (muodosta-suolasakkoraportti-urakalle db user {:urakka-id urakka-id
                                                                  :alkupvm   hk-alkupvm
                                                                  :loppupvm  hk-loppupvm})]

          (and hallintayksikko-id hk-alkupvm hk-loppupvm)
          [:hallintayksikko (muodosta-suolasakkoraportti-hallintayksikolle db user {:hallintayksikko-id hallintayksikko-id
                                                                                    :alkupvm            hk-alkupvm
                                                                                    :loppupvm           hk-loppupvm})]
          (and hk-alkupvm hk-loppupvm)
          [:koko-maa (muodosta-suolasakkoraportti-koko-maalle db user {:alkupvm hk-alkupvm :loppupvm hk-loppupvm})]

          :default
          ;; FIXME Pitäisikö tässä heittää jotain, tänne ei pitäisi päästä, jos parametrit ovat oikein?
          nil)
        otsikko (str (case konteksti
                       :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                       :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                       :koko-maa "KOKO MAA")
                     ", Suolabonus/sakkoraportti "
                     (pvm/pvm (or hk-alkupvm hk-alkupvm)) " \u2010 " (pvm/pvm (or hk-loppupvm hk-loppupvm)))]
    [:raportti {:nimi otsikko}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true}
      [{:leveys "10%" :otsikko "Urakka"}
       {:leveys "10%" :otsikko "Keskiläpötila"}
       {:leveys "10%" :otsikko "Pitkän aikavälin keskilämpötila"}
       {:leveys "10%" :otsikko "Sopimuksen mukainen suolamäärä (t)"}
       {:leveys "10%" :otsikko "Sakkoraja (t)"}
       {:leveys "10%" :otsikko "Kerroin"}
       {:leveys "10%" :otsikko "Kohtuullistarkistettu sakkoraja (t)"}
       {:leveys "10%" :otsikko "Käytetty suolamäärä (t)"}
       {:leveys "10%" :otsikko "Suolaerotus (t)"}
       {:leveys "10%" :otsikko "Sakko/Bonus"}
       {:leveys "10%" :otsikko "Indeksi"}]
      (for [rivi raportin-data]
        [(:urakka_nimi rivi)
         (str (:keskilampotila rivi) " C")
         (str (:pitkakeskilampotila rivi) "C")
         (:suola_suunniteltu rivi)
         (format "%.2f" (* (:suola_suunniteltu rivi) 1.05))
         (format "%.4f" (:kerroin rivi))
         (format "%.2f" (:kohtuullistarkistettu_sakkoraja rivi))
         (:suola_kaytetty rivi)
         (- (:suola_kaytetty rivi) (:suola_suunniteltu rivi))
         (fmt/euro-opt (:suolasakko rivi))
         (:indeksi rivi)])]]))