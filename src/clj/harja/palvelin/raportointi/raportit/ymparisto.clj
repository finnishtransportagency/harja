(ns harja.palvelin.raportointi.raportit.ymparisto
  (:require [harja.domain.materiaali :as materiaalidomain]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.kyselyt
             [hallintayksikot :as hallintayksikot-q]
             [lampotilat :as suolasakko-q]
             [konversio :as konv]
             [urakat :as urakat-q]]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko]]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/ymparisto.sql"
  {:positional? true})


(defn- hae-raportin-tiedot
  [db parametrit]
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(update-in % [:kk]
                               (fn [pvm]
                                 (when pvm
                                   (yleinen/kk-ja-vv pvm))))))
        (hae-ymparistoraportti-tiedot db parametrit)))

(defn hae-raportti* [db hakuasetukset]
  (let [urakoittain? (:urakoittain? hakuasetukset)
        rivit (hae-raportin-tiedot db hakuasetukset)
        materiaali-rivit (hae-materiaalit db)
        urakat (into #{} (map :urakka rivit))
        materiaali-avaimet (if urakoittain?
                             [:materiaali :urakka]
                             [:materiaali])
        materiaalit (into {}
                          (for [m materiaali-rivit
                                u urakat]
                            (if urakoittain?
                              [{:materiaali m :urakka u} []]
                              [{:materiaali m} []])))]
    (sort-by (comp :nimi :materiaali first)
             (merge materiaalit
                    (group-by
                      #(select-keys % materiaali-avaimet)
                      rivit)))))


(defn hae-raportti [db alkupvm loppupvm urakka-id hallintayksikko-id
                    urakkatyyppi urakoittain?]
  (hae-raportti* db {:alkupvm alkupvm
                     :loppupvm loppupvm
                     :urakka urakka-id
                     :urakkatyyppi (some-> urakkatyyppi name)
                     :hallintayksikko hallintayksikko-id
                     :urakoittain? urakoittain?}))

(defn hae-raportti-urakoittain [db alkupvm loppupvm hallintayksikko-id
                                urakkatyyppi urakoittain?]
  (hae-raportti* db {:alkupvm alkupvm
                     :loppupvm loppupvm
                     :urakka nil
                     :urakkatyyppi (some-> urakkatyyppi name)
                     :hallintayksikko hallintayksikko-id
                     :urakoittain? urakoittain?}))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakoittain? urakkatyyppi] :as parametrit}]
  (let [urakoittain? (if urakka-id false urakoittain?)
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        materiaalit (if urakoittain?
                      (hae-raportti-urakoittain db alkupvm loppupvm hallintayksikko-id urakkatyyppi urakoittain?)
                      (hae-raportti db alkupvm loppupvm urakka-id hallintayksikko-id urakkatyyppi urakoittain?))
        kk-lev (if urakoittain?
                 "4%" ; tehdään yksittäisestä kk:sta pienempi, jotta urakan nimi mahtuu
                 "5%")
        raportin-nimi "Ympäristöraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        materiaalit (sort-by #(materiaalidomain/materiaalien-jarjestys
                               (get-in (first %) [:materiaali :nimi]))
                             materiaalit)
        kuukaudet (yleinen/kuukaudet alkupvm loppupvm yleinen/kk-ja-vv-fmt)
        talvisuolan-maxmaaratieto (when (= :urakka konteksti)
                                    (:talvisuolaraja (first (suolasakko-q/hae-urakan-suolasakot db {:urakka urakka-id}))))
        talvisuolan-toteutunut-maara (when (= :urakka konteksti)
                                       (some->> materiaalit
                                                (filter (fn [[materiaali _]]
                                                          (= "talvisuola" (get-in materiaali [:materiaali :tyyppi])))) ;; vain talvisuolat
                                                (mapcat second)
                                                (filter #(nil? (:luokka %))) ;; luokka on nil toteumariveillä (lihavoidut raportissa)
                                                (map :maara)
                                                (reduce +)))]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     (when (= konteksti :urakka)
       [:teksti (str "Hoitokauden talvisuolan maksimimäärä urakassa: "
                     talvisuolan-maxmaaratieto "t")])
     (when (= konteksti :urakka)
       [:teksti (str "Erilaisia talvisuoloja käytetty valitulla aikavälillä: "
                     (fmt/desimaaliluku-opt talvisuolan-toteutunut-maara 2)
                     "t")])
     (when (= konteksti :urakka)
       [:teksti (str
                  "Toteumaprosentti suhteessa hoitokauden maksimimäärään: "
                  (fmt/desimaaliluku-opt
                    (* 100 (with-precision 3 (/ talvisuolan-toteutunut-maara
                                                talvisuolan-maxmaaratieto)))
                    1) "%")])
     [:taulukko {:otsikko otsikko
                 :oikealle-tasattavat-kentat (into #{} (range 1 (+ 4 (count kuukaudet))))
                 :sheet-nimi raportin-nimi}
      (into []

            (concat
             (when urakoittain?
               [{:otsikko "Urakka" :leveys "10%"}])

             ;; Materiaalin nimi
             [{:otsikko "Materiaali" :leveys "16%"}]
             ;; Kaikki kuukaudet
             (map (fn [kk]
                    {:otsikko kk
                     :leveys kk-lev
                     :fmt :numero}) kuukaudet)

             [{:otsikko "Määrä yhteensä" :leveys "8%" :fmt :numero :jos-tyhja "-"
               :excel [:summa-vasen (if urakoittain? 2 1)]}
              {:otsikko "Tot-%" :leveys "8%" :fmt :prosentti :jos-tyhja "-"}
              {:otsikko "Suunniteltu määrä" :leveys "8%" :fmt :numero :jos-tyhja "-"}]))

      (mapcat
       (fn [[{:keys [urakka materiaali]} rivit]]
         (let [suunnitellut (keep :maara (filter #(nil? (:kk %)) rivit))
               suunniteltu (when-not (empty? suunnitellut)
                         (reduce + suunnitellut))
               luokitellut (filter :luokka rivit)
               materiaalirivit (remove #(nil? (:kk %)) rivit)
               kk-rivit (group-by :kk (filter (comp not :luokka) materiaalirivit))
               kk-arvot (reduce-kv (fn [kk-arvot kk rivit]
                                     (assoc kk-arvot kk [:arvo-ja-yksikko {:arvo (reduce + (keep :maara rivit))
                                                                           :yksikko (:yksikko materiaali)}]))
                                   {} kk-rivit)
               yhteensa-arvo #(reduce + (remove nil? (map (comp :arvo second) %)))
               yhteensa-kentta (fn [arvot nayta-aina?]
                                 (let [yht (yhteensa-arvo arvot)]
                                   (when (or (> yht 0) nayta-aina?)
                                     [:arvo-ja-yksikko {:arvo yht
                                                        :yksikko (:yksikko materiaali)}])))]
           ;(log/info "KK-ARVOT: " kk-arvot "; KUUKAUDET: " kuukaudet)
           (concat
            ;; Normaali materiaalikohtainen rivi
            [{:lihavoi? true
              :rivi (into []
                          (concat

                            ;; Urakan nimi, jos urakoittain jaottelu päällä
                            (when urakoittain?
                              [(:nimi urakka)])

                            ;; Materiaalin nimi
                            [(:nimi materiaali)]

                            ;; Kuukausittaiset määrät
                            (map kk-arvot kuukaudet)

                            ;; Yhteensä, toteumaprosentti ja suunniteltumäärä
                            [(yhteensa-kentta (vals kk-arvot) false)
                             (when suunniteltu (/ (* 100.0 (yhteensa-arvo (vals kk-arvot))) suunniteltu))
                             (when suunniteltu [:arvo-ja-yksikko {:arvo suunniteltu
                                                                  :yksikko (:yksikko materiaali)}])]))}]

            ;; Mahdolliset hoitoluokkakohtaiset rivit
            (map (fn [[luokka rivit]]
                   (let [kk-arvot (into {}
                                        (map (juxt :kk #(do [:arvo-ja-yksikko {:arvo (:maara %)
                                                                               :yksikko (:yksikko materiaali)}])))
                                        rivit)]
                     (into []
                           (concat
                            (when urakoittain?
                              [(:nimi urakka)])
                            [(str " - "
                                  (hoitoluokat/talvihoitoluokan-nimi luokka))]

                            (map kk-arvot kuukaudet)

                            [(yhteensa-kentta (vals kk-arvot) false)
                             nil nil]))))
                 (sort-by first (group-by :luokka luokitellut))))))

       materiaalit)]]))
