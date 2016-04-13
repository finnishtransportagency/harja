(ns harja.palvelin.raportointi.raportit.ymparisto
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

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
  (sort-by (comp :nimi :materiaali first)
           (group-by #(select-keys % [:materiaali])
                     (hae-raportin-tiedot db {:alkupvm alkupvm
                                              :loppupvm loppupvm
                                              :urakka urakka-id
                                              :urakkatyyppi (some-> urakkatyyppi name)
                                              :hallintayksikko hallintayksikko-id}))))

(defn hae-raportti-urakoittain [db alkupvm loppupvm hallintayksikko-id urakkatyyppi]
  (sort-by (comp :nimi :materiaali first)
           (group-by #(select-keys % [:materiaali :urakka])
                     (hae-raportin-tiedot db {:alkupvm alkupvm
                                              :loppupvm loppupvm
                                              :urakka nil
                                              :urakkatyyppi (some-> urakkatyyppi name)
                                              :hallintayksikko hallintayksikko-id}))))

(defn- talvihoitoluokka
  [luokka]
  (case luokka
    1 "Is"
    2 "I"
    3 "Ib"
    4 "TIb"
    5 "II"
    6 "III"
    7 "K1"
    8 "K2"))

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
     [:taulukko {:otsikko otsikko}
      (into []

            (concat
             (when urakoittain?
               [{:otsikko "Urakka" :leveys "10%"}])

             ;; Materiaalin nimi
             [{:otsikko "Materiaali" :leveys "16%"}]
             ;; Kaikki kuukaudet
             (map (fn [kk]
                    {:otsikko kk
                     :leveys kk-lev}) kuukaudet)

             [{:otsikko "Määrä yhteensä" :leveys "8%"}
              {:otsikko "Tot-%" :leveys "8%"}
              {:otsikko "Maksimi\u00admäärä" :leveys "8%"}]))

      (mapcat
       (fn [[{:keys [urakka materiaali]} rivit]]
         (let [suunnitellut (keep :maara (filter #(nil? (:kk %)) rivit))
               maksimi (when-not (empty? suunnitellut)
                         (reduce + suunnitellut))
               luokitellut (filter :luokka rivit)
               yhteensa (reduce + (keep  #(when (and (:kk %) (not (:luokka %)))
                                            (:maara %)) rivit))
               kk-arvot (into {}
                              (comp (filter :kk)
                                    (map (juxt :kk :maara)))
                              rivit)]
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
                    (map #(or (kk-arvot %) 0) kuukaudet)

                    ;; Yhteensä, toteumaprosentti ja maksimimäärä
                    [yhteensa
                     (if maksimi (fmt/desimaaliluku (/ (* 100.0 yhteensa) maksimi) 1) "-")
                     (or maksimi "-")]))]

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

                            (map #(or (kk-arvot %) 0) kuukaudet)

                            [(reduce + (remove nil? (vals kk-arvot))) "-" "-"]))))
                 (sort-by first (group-by :luokka luokitellut))))))

              materiaalit)]]))
