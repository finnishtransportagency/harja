(ns harja.palvelin.raportointi.raportit.indeksitarkistus
  (:require [harja.palvelin.raportointi.raportit.laskutusyhteenveto
             :refer [hae-laskutusyhteenvedon-tiedot]]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [taoensso.timbre :as log]))

(def ly (atom nil))

(defn summa [laskutusyhteenvedot avain]
  (reset! ly laskutusyhteenvedot)
  (reduce + 0
          (remove nil?
                  (mapcat #(map avain %)
                          laskutusyhteenvedot))))

(defn suorita [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id] :as parametrit}]
  (let [urakat (yleinen/hae-kontekstin-urakat db {:urakka urakka-id
                                                  :hallintayksikko hallintayksikko-id
                                                  :urakkatyyppi "hoito"
                                                  :alku alkupvm :loppu loppupvm})
        urakka-idt (mapv :urakka-id urakat)
        kuukaudet (yleinen/kuukausivalit alkupvm loppupvm)
        laskutusyhteenvedot-kk (zipmap kuukaudet
                                       (map (fn [[alku loppu]]
                                              (->> urakka-idt
                                                   (map #(hae-laskutusyhteenvedon-tiedot
                                                          db user {:urakka-id %
                                                                   :alkupvm alku
                                                                   :loppupvm loppu}))))
                                            kuukaudet))
        summa-kk (memoize (fn [kk kentta]
                            (summa (laskutusyhteenvedot-kk kk) kentta)))
        kentat [:kht_laskutettu_ind_korotus
                :yht_laskutetaan_ind_korotus
                :akilliset_hoitotyot_laskutettu_ind_korotus
                :sakot_laskutettu_ind_korotus
                :suolasakot_laskutettu_ind_korotus]]

    [:raportti {:nimi "Indeksitarkistus"}
     [:taulukko {:viimeinen-rivi-yhteenveto? true}
      [{:otsikko "Kuukausi" :leveys 2}
       {:otsikko "Kokonaishintaiset työt" :leveys 2 :fmt :raha :tasaa :oikea}
       {:otsikko "Yksikköhintaiset työt" :leveys 2 :fmt :raha :tasaa :oikea}
       {:otsikko "Äkillinen hoitotyö" :leveys 2 :fmt :raha :tasaa :oikea}
       {:otsikko "Sanktiot" :leveys 2 :fmt :raha :tasaa :oikea}
       {:otsikko "Suolabonukset ja -sanktiot" :leveys 2 :fmt :raha :tasaa :oikea}
       {:otsikko "Yhteensä (€)" :leveys 2 :fmt :raha :tasaa :oikea}]

      (into
       []
       (concat
        (for [[alku _ :as kk] kuukaudet
              :let [kentan-arvot (map #(summa-kk kk %) kentat)]]
          (into [(pvm/kuukauden-lyhyt-nimi (pvm/kuukausi alku))]
                (concat kentan-arvot
                        [(reduce + kentan-arvot)])))
        (let [summat (for [kentta kentat]
                       (reduce + (map #(summa-kk % kentta) kuukaudet)))]
          [(into ["Yhteensä:"]
                 (concat
                  summat
                  [(reduce + summat)]))])))]

     ]))
