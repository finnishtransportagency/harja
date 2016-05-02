(ns harja.palvelin.raportointi.raportit.ymparisto
  (:require [harja.domain.materiaali :as materiaalidomain]
            [harja.kyselyt
             [hallintayksikot :as hallintayksikot-q]
             [konversio :as konv]
             [urakat :as urakat-q]]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko talvihoitoluokka]]
            [jeesql.core :refer [defqueries]]))

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


(defn hae-raportti [db alkupvm loppupvm urakka-id hallintayksikko-id urakkatyyppi]
  (let [kaikki-materiaalit (into {}
                                 ;; hae tyhjät rivit kaikille materiaaleille
                                 (map (juxt (fn [m]
                                              {:materiaali m})
                                            (constantly [])))
                                 (hae-materiaalit db))]
    (sort-by (comp :nimi :materiaali first)
             (merge kaikki-materiaalit
                    (group-by #(select-keys % [:materiaali])
                              (hae-raportin-tiedot db {:alkupvm alkupvm
                                                       :loppupvm loppupvm
                                                       :urakka urakka-id
                                                       :urakkatyyppi (some-> urakkatyyppi name)
                                                       :hallintayksikko hallintayksikko-id}))))))

(defn hae-raportti-urakoittain [db alkupvm loppupvm hallintayksikko-id urakkatyyppi]
  (let [rivit (hae-raportin-tiedot db {:alkupvm alkupvm
                                       :loppupvm loppupvm
                                       :urakka nil
                                       :urakkatyyppi (some-> urakkatyyppi name)
                                       :hallintayksikko hallintayksikko-id})
        urakat (into #{} (map :urakka rivit))
        kaikki-materiaalit (hae-materiaalit db)

        ;; luodaan tyhjä rivilista kaikille materiaali/urakka kombinaatioille
        kaikki-urakka-materiaalit (into {}
                                        (for [m kaikki-materiaalit
                                              u urakat]
                                          [{:materiaali m :urakka u} []]))]
    (sort-by (comp :nimi :materiaali first)
             (merge kaikki-urakka-materiaalit
                    (group-by #(select-keys % [:materiaali :urakka])
                              rivit)))))

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
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        materiaalit (sort-by #(materiaalidomain/materiaalien-jarjestys
                               (get-in (first %) [:materiaali :nimi]))
                             materiaalit)
        kuukaudet (yleinen/kuukaudet alkupvm loppupvm yleinen/kk-ja-vv-fmt)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
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
              {:otsikko "Maksimi\u00admäärä" :leveys "8%" :fmt :numero :jos-tyhja "-"}]))

      (mapcat
       (fn [[{:keys [urakka materiaali]} rivit]]
         (let [suunnitellut (keep :maara (filter #(nil? (:kk %)) rivit))
               maksimi (when-not (empty? suunnitellut)
                         (reduce + suunnitellut))
               luokitellut (filter :luokka rivit)
               materiaalirivit (filter #(not (:luokka %)) rivit)
               kk-rivit (group-by :kk materiaalirivit)
               kk-arvot (reduce-kv (fn [kk-arvot kk rivit]
                                     (assoc kk-arvot kk (reduce + 0 (map :maara rivit))))
                                   {} kk-rivit)
               yhteensa (reduce + 0 (vals kk-arvot))]
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

                           ;; Yhteensä, toteumaprosentti ja maksimimäärä
                           [yhteensa
                            (when maksimi (/ (* 100.0 yhteensa) maksimi))
                            maksimi]))}]

            ;; Mahdolliset hoitoluokkakohtaiset rivit
            (map (fn [[luokka rivit]]
                   (let [kk-arvot (into {}
                                        (map (juxt :kk :maara))
                                        rivit)]
                     (into []
                           (concat
                            (when urakoittain?
                              [(:nimi urakka)])
                            [(str " - "
                                  (talvihoitoluokka luokka))]

                            (map kk-arvot kuukaudet)

                            [(reduce + (remove nil? (vals kk-arvot)))
                             nil nil]))))
                 (sort-by first (group-by :luokka luokitellut))))))

       materiaalit)]]))
