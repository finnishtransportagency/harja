(ns harja.palvelin.raportointi.raportit.ymparisto
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/ymparisto.sql"
  {:positional? true})


(defn hae-raportti [db alkupvm loppupvm urakka-id hallintayksikko-id urakkatyyppi]
  (sort-by (comp :nimi :materiaali first)
           (group-by #(select-keys % [:materiaali])
                     (into []
                           (map konv/alaviiva->rakenne)
                           (hae-ymparistoraportti db alkupvm loppupvm
                                                  (some? urakka-id) urakka-id
                                                  (some? urakkatyyppi) (when urakkatyyppi (name urakkatyyppi))
                                                  (some? hallintayksikko-id) hallintayksikko-id)))))


(defn hae-raportti-urakoittain [db alkupvm loppupvm hallintayksikko-id urakkatyyppi]
  (sort-by (comp :nimi :urakka first)
           (seq (group-by #(select-keys % [:materiaali :urakka])
                          (into []
                                (map konv/alaviiva->rakenne)
                                (hae-ymparistoraportti-urakoittain db alkupvm loppupvm
                                                                   (some? hallintayksikko-id) hallintayksikko-id
                                                                   (some? urakkatyyppi) (when urakkatyyppi (name urakkatyyppi))))))))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakoittain? urakkatyyppi] :as parametrit}]
  (let [urakoittain? (if urakka-id false urakoittain?)
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        materiaalit (if urakoittain?
                      (hae-raportti-urakoittain db alkupvm loppupvm hallintayksikko-id urakkatyyppi)
                      (hae-raportti db alkupvm loppupvm urakka-id hallintayksikko-id urakkatyyppi))
        kk-lev (if urakoittain?
                 "4%" ; tehdään yksittäisestä kk:sta pienempi, jotta urakan nimi mahtuu
                 "5%")
        raportin-nimi "Ympäristöraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        materiaalit (sort-by #(materiaalidomain/materiaalien-jarjestys
                               (get-in (first %) [:materiaali :nimi]))
                             materiaalit)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko}
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
                  (map (comp (fn [o] {:otsikko o :leveys kk-lev :otsikkorivi-luokka "grid-kk-sarake"}) pvm/kuukausi-ja-vuosi-valilyonnilla)))

             [{:otsikko "Määrä yhteensä" :leveys "8%"}
              {:otsikko "Tot-%" :leveys "8%"}
              {:otsikko "Maksimi\u00admäärä" :leveys "8%"}]))

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
                                  (if maksimi (fmt/desimaaliluku (/ (* 100.0 yhteensa) maksimi) 1) "-")
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

                    materiaalit))]]))
