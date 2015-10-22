(ns harja.palvelin.raportointi.raportit.ymparisto
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/ymparisto.sql")


(defn hae-raportti [db alkupvm loppupvm urakka-id hallintayksikko-id]
  (sort-by (comp :nimi :materiaali first)
           (group-by #(select-keys % [:materiaali])
                     (into []
                           (map konv/alaviiva->rakenne)
                           (hae-ymparistoraportti db alkupvm loppupvm
                                                  (if urakka-id true false) urakka-id
                                                  (if hallintayksikko-id true false) hallintayksikko-id)))))


(defn hae-raportti-urakoittain [db alkupvm loppupvm hallintayksikko-id]
  (sort-by (comp :nimi :urakka first)
           (seq (group-by #(select-keys % [:materiaali :urakka])
                          (into []
                                (map konv/alaviiva->rakenne)
                                (hae-ymparistoraportti-urakoittain db alkupvm loppupvm
                                                                   (if hallintayksikko-id true false) hallintayksikko-id))))))

(defn suorita [db user {:keys [hk-alkupvm hk-loppupvm alkupvm loppupvm
                               urakka-id hallintayksikko-id konteksti
                               urakoittain?] :as parametrit}]
  (let [[alku loppu] (if urakka-id
                       [hk-alkupvm hk-loppupvm]
                       [alkupvm loppupvm])
        materiaalit (if urakoittain?
                      (hae-raportti-urakoittain db alku loppu hallintayksikko-id)
                      ;; FIXME: urakka-id tai hallintayksikkö id
                      (hae-raportti db alku loppu urakka-id hallintayksikko-id))
        kk-lev (if urakoittain?
                 "4%" ; tehdään yksittäisestä kk:sta pienempi, jotta urakan nimi mahtuu
                 "5%")]

    (println "RAPORTTI: " (pr-str materiaalit))
    [:raportti {:otsikko "Ympäristöraportti"
                :orientaatio :landscape}
     [:taulukko {:otsikko "Ympäristöraportti"}
      (into []
            
            (concat
             (when urakoittain?
               [{:otsikko "Urakka" :leveys "10%"}])
             
             ;; Materiaalin nimi
             [{:otsikko "Materiaali" :leveys "16%"}]
             ;; Kaikki kuukaudet (otetaan ensimmäisestä materiaalista)
             (->> materiaalit first second
                  (keep :kk)
                  sort
                  (map (comp (fn [o] {:otsikko o :leveys kk-lev}) pvm/kuukausi-ja-vuosi)))

             [{:otsikko "Määrä yhteensä" :leveys "8%"}
              {:otsikko "Tot-%" :leveys "8%"}
              {:otsikko "Maksimi\u00admäärä" :leveys "8%"}]
             ))

      
      (keep identity
            (mapcat (fn [[{:keys [urakka materiaali]} kuukaudet]]
                      
                      (let [maksimi (:maara (first (filter #(nil? (:kk %)) kuukaudet)))
                            luokitellut (filter :luokka kuukaudet)
                            kuukaudet (filter (comp not :luokka) kuukaudet)
                            yhteensa (reduce + (keep  #(when (:kk %) (:maara %)) kuukaudet))
                            kk-arvot (sort (keep :kk kuukaudet))]
                        (concat
                         ;; Normaali materiaalikohtainen rivi
                         [(into []
                                (concat

                                 ;; Urakan nimi, jos urakoittain jaottelu päällä
                                 (when urakoittain?
                                   [(:nimi urakka)])
                                 
                                 ;; Materiaalin nimi
                                 [(:nimi materiaali)]

                                 ;; Kuukausittaiset määrät
                                 (->> kuukaudet
                                      (filter :kk)
                                      (sort-by :kk)
                                      (map #(or (:maara %) 0)))

                                 ;; Yhteensä, toteumaprosentti ja maksimimäärä
                                 [yhteensa
                                  (if maksimi (format "%.2f%%" (/ (* 100.0 yhteensa) maksimi)) "-")
                                  (or maksimi "-")]))]

                         ;; Mahdolliset hoitoluokkakohtaiset rivit
                         (map (fn [[luokka kuukaudet]]
                                (let [arvot (group-by :kk kuukaudet)]
                                  (into []
                                        (concat 
                                         (when urakoittain?
                                           [(:nimi urakka)])
                                         [(str " - "
                                               (case luokka
                                                 1 "Is"
                                                 2 "I"
                                                 3 "Ib"
                                                 4 "TIb"
                                                 5 "II"
                                                 6 "III"
                                                 7 "K1"
                                                 8 "K2"))]
                                         
                                         (for [kk kk-arvot
                                               :let [arvo (first (get arvot kk))]]
                                           (or (:maara arvo) 0))

                                         [(reduce + (map (comp :maara first) (vals arvot))) "-" "-"]))))
                              (group-by :luokka luokitellut)))))
                    
                                              
                    materiaalit))
      ]]))
