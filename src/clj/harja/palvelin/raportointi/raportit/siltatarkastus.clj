(ns harja.palvelin.raportointi.raportit.siltatarkastus
  (:require
    [clojure.string :as s]
    [jeesql.core :refer [defqueries]]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
    [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
    [harja.fmt :as fmt]
    [harja.pvm :as pvm]
    [taoensso.timbre :as log]
    [harja.kyselyt.konversio :as konv]
    [harja.domain.siltatarkastus :as siltadomain]
    [harja.domain.raportointi :refer [info-solu]]
    [harja.math :as math]
    [clj-time.coerce :as c]))

(defqueries "harja/palvelin/raportointi/raportit/siltatarkastus.sql")

(def ^{:private true} korosta-kun-arvoa-d-vahintaan 1)
(def tarkastamatta-info (info-solu "Tarkastamatta"))

(defn- muodosta-sillan-datarivit [db urakka-id silta-id vuosi]
  (let [kohderivit (into []
                         (comp
                           (map konv/alaviiva->rakenne)
                           (map #(update % :tulos (fn [tulos]
                                                    (s/join ", "
                                                            (konv/pgarray->vector tulos))))))
                         (hae-sillan-tarkastuskohteet db {:urakka urakka-id
                                                          :vuosi vuosi
                                                          :silta silta-id}))
        kohderivit (sort-by :kohde (konv/sarakkeet-vektoriin
                                     kohderivit
                                     {:liite :liitteet}
                                     :kohde))
        kohdenumerot-valilta (fn [alku loppu]
                               (filter #(and (>= (:kohde %) alku)
                                             (<= (:kohde %) loppu))
                                       kohderivit))
        datarivi (fn [kohde]
                   [(:kohde kohde)
                    (siltadomain/siltatarkastuskohteen-nimi (:kohde kohde))
                    (:tulos kohde)
                    (:lisatieto kohde)
                    [:liitteet (:liitteet kohde)]])
        taulukkorivit (when-not (empty? kohderivit)
                        (into [] (concat [{:otsikko "Aluerakenne"}]
                                         (mapv datarivi (kohdenumerot-valilta 1 3))
                                         [{:otsikko "Päällysrakenne"}]
                                         (mapv datarivi (kohdenumerot-valilta 4 10))
                                         [{:otsikko "Varusteet ja laitteet"}]
                                         (mapv datarivi (kohdenumerot-valilta 11 19))
                                         [{:otsikko "Siltapaikan rakenteet"}]
                                         (mapv datarivi (kohdenumerot-valilta 20 24)))))]
    taulukkorivit))

(defn- siltojen-yhteensa-rivit [tarkastukset]
  (let [a-yhteensa (reduce + 0 (keep :a tarkastukset))
        b-yhteensa (reduce + 0 (keep :b tarkastukset))
        c-yhteensa (reduce + 0 (keep :c tarkastukset))
        d-yhteensa (reduce + 0 (keep :d tarkastukset))
        kaikki-yhteensa (+ a-yhteensa b-yhteensa c-yhteensa d-yhteensa)]
    ["Yhteensä"
     nil
     nil
     nil
     [:arvo-ja-osuus {:arvo a-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           a-yhteensa kaikki-yhteensa))}]
     [:arvo-ja-osuus {:arvo b-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           b-yhteensa kaikki-yhteensa))}]
     [:arvo-ja-osuus {:arvo c-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           c-yhteensa kaikki-yhteensa))}]
     [:arvo-ja-osuus {:arvo d-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           d-yhteensa kaikki-yhteensa))}]
     [:liitteet nil]]))

(defn- muodosta-siltojen-datarivit [db urakka-id vuosi]
  (let [tarkastukset (into []
                           (map konv/alaviiva->rakenne)
                           (hae-urakan-siltatarkastukset db {:urakka urakka-id
                                                             :vuosi vuosi}))
        tarkastukset (konv/sarakkeet-vektoriin
                       tarkastukset
                       {:liite :liitteet})
        rivit (mapv
                (fn [tarkastus]
                  (let [arvioidut-kohteet-yhteensa (reduce + 0 ((juxt :a :b :c :d) tarkastus))]
                    [(:siltanro tarkastus)
                     (:siltanimi tarkastus)
                     (if (:tarkastusaika tarkastus)
                       (:tarkastusaika tarkastus)
                       tarkastamatta-info)
                     (or (:tarkastaja tarkastus)
                         "-")
                     [:arvo-ja-osuus {:arvo (:a tarkastus)
                                      :osuus (Math/round (math/osuus-prosentteina
                                                           (:a tarkastus) arvioidut-kohteet-yhteensa))}]
                     [:arvo-ja-osuus {:arvo (:b tarkastus)
                                      :osuus (Math/round (math/osuus-prosentteina
                                                           (:b tarkastus) arvioidut-kohteet-yhteensa))}]
                     [:arvo-ja-osuus {:arvo (:c tarkastus)
                                      :osuus (Math/round (math/osuus-prosentteina
                                                           (:c tarkastus) arvioidut-kohteet-yhteensa))}]
                     [:arvo-ja-osuus {:arvo (:d tarkastus)
                                      :osuus (Math/round (math/osuus-prosentteina
                                                           (:d tarkastus) arvioidut-kohteet-yhteensa))}]
                     [:liitteet (:liitteet tarkastus)]]))
                tarkastukset)
        rivit+yhteensa (conj rivit (siltojen-yhteensa-rivit tarkastukset))]
    rivit+yhteensa))

(defn- muodosta-urakan-datarivit [db urakka-id silta-id vuosi]
  (if (= silta-id :kaikki)
    (muodosta-siltojen-datarivit db urakka-id vuosi)
    (muodosta-sillan-datarivit db urakka-id silta-id vuosi)))

(defn- hallintayksikko-tai-koko-maa-yhteensa-rivi [rivit]
  (let [a-yhteensa (reduce + 0 (keep :a rivit))
        b-yhteensa (reduce + 0 (keep :b rivit))
        c-yhteensa (reduce + 0 (keep :c rivit))
        d-yhteensa (reduce + 0 (keep :d rivit))
        kaikki-yhteensa (+ a-yhteensa b-yhteensa c-yhteensa d-yhteensa)]
    ["Yhteensä"
     [:arvo-ja-osuus {:arvo a-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           a-yhteensa kaikki-yhteensa))}]
     [:arvo-ja-osuus {:arvo b-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           b-yhteensa kaikki-yhteensa))}]
     [:arvo-ja-osuus {:arvo c-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           c-yhteensa kaikki-yhteensa))}]
     [:arvo-ja-osuus {:arvo d-yhteensa
                      :osuus (Math/round (math/osuus-prosentteina
                                           d-yhteensa kaikki-yhteensa))}]]))

(defn- muodosta-hallintayksikon-datarivit [db hallintayksikko-id vuosi]
  (let [urakkarivit (hae-hallintayksikon-siltatarkastukset db {:hallintayksikko hallintayksikko-id
                                                               :vuosi vuosi})
        taulukkorivit (mapv
                        (fn [rivi]
                          (let [arvioidut-kohteet-yhteensa (reduce + 0 ((juxt :a :b :c :d) rivi))]
                            [(:nimi rivi)
                             [:arvo-ja-osuus {:arvo (:a rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:a rivi) arvioidut-kohteet-yhteensa))}]
                             [:arvo-ja-osuus {:arvo (:b rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:b rivi) arvioidut-kohteet-yhteensa))}]
                             [:arvo-ja-osuus {:arvo (:c rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:c rivi) arvioidut-kohteet-yhteensa))}]
                             [:arvo-ja-osuus {:arvo (:d rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:d rivi) arvioidut-kohteet-yhteensa))}]]))
                        urakkarivit)
        taulukkorivit+yhteensa (conj taulukkorivit (hallintayksikko-tai-koko-maa-yhteensa-rivi urakkarivit))]
    taulukkorivit+yhteensa))

(defn- muodosta-koko-maan-datarivit [db vuosi]
  (let [elyrivit (hae-koko-maan-siltatarkastukset db {:vuosi vuosi})
        taulukkorivit (mapv
                        (fn [rivi]
                          (let [arvioidut-kohteet-yhteensa (reduce + ((juxt :a :b :c :d) rivi))]
                            [(:nimi rivi)
                             [:arvo-ja-osuus {:arvo (:a rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:a rivi) arvioidut-kohteet-yhteensa))}]
                             [:arvo-ja-osuus {:arvo (:b rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:b rivi) arvioidut-kohteet-yhteensa))}]
                             [:arvo-ja-osuus {:arvo (:c rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:c rivi) arvioidut-kohteet-yhteensa))}]
                             [:arvo-ja-osuus {:arvo (:d rivi)
                                              :osuus (Math/round (math/osuus-prosentteina
                                                                   (:d rivi) arvioidut-kohteet-yhteensa))}]]))
                        elyrivit)
        taulukkorivit+yhteensa (conj taulukkorivit (hallintayksikko-tai-koko-maa-yhteensa-rivi elyrivit))]
    taulukkorivit+yhteensa))

(defn- muodosta-raportin-otsikkorivit [db konteksti silta]
  (case konteksti
    :urakka (if (= silta :kaikki)
              [{:leveys 5 :otsikko "Siltanumero"}
               {:leveys 10 :otsikko "Silta"}
               {:leveys 5 :otsikko "Tarkastettu" :fmt :pvm}
               {:leveys 5 :otsikko "Tarkastaja"}
               {:leveys 5 :otsikko "A"}
               {:leveys 5 :otsikko "B"}
               {:leveys 5 :otsikko "C"}
               {:leveys 5 :otsikko "D"}
               {:leveys 5 :otsikko "Liitteet"}]
              [{:leveys 2 :otsikko "#"}
               {:leveys 15 :otsikko "Kohde"}
               {:leveys 2 :otsikko "Tulos"}
               {:leveys 10 :otsikko "Lisätieto"}
               {:leveys 5 :otsikko "Liitteet"}])
    :hallintayksikko [{:leveys 10 :otsikko "Urakka"}
                      {:leveys 5 :otsikko "A"}
                      {:leveys 5 :otsikko "B"}
                      {:leveys 5 :otsikko "C"}
                      {:leveys 5 :otsikko "D"}]
    :koko-maa [{:leveys 10 :otsikko "Hallintayksikkö"}
               {:leveys 5 :otsikko "A"}
               {:leveys 5 :otsikko "B"}
               {:leveys 5 :otsikko "C"}
               {:leveys 5 :otsikko "D"}]))

(defn- muodosta-raportin-datarivit [db urakka-id hallintayksikko-id konteksti silta-id vuosi]
  (case konteksti
    :urakka (muodosta-urakan-datarivit db urakka-id silta-id vuosi)
    :hallintayksikko (muodosta-hallintayksikon-datarivit db hallintayksikko-id vuosi)
    :koko-maa (muodosta-koko-maan-datarivit db vuosi)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id silta-id vuosi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        otsikkorivit (muodosta-raportin-otsikkorivit db konteksti silta-id)
        yksittaisen-sillan-perustiedot (when (and (= konteksti :urakka)
                                                  (not= silta-id :kaikki))
                                         (first (hae-sillan-tarkastus db {:urakka urakka-id
                                                                          :vuosi vuosi
                                                                          :silta silta-id})))
        datarivit (muodosta-raportin-datarivit db urakka-id hallintayksikko-id konteksti silta-id vuosi)
        urakan-vuoden-tarkastusmaarat (when (= konteksti :urakka)
                                        (first (hae-urakan-tarkastettujen-siltojen-lkm db {:urakka urakka-id
                                                                                           :vuosi vuosi})))
        raportin-nimi "Siltatarkastusraportti"
        liita (fn [rivi kentta arvo] (assoc (if (map? rivi) rivi {:rivi rivi}) kentta arvo))
        kentta-indeksilla (fn [rivi indeksi] (nth (if (map? rivi) (:rivi rivi) rivi) indeksi))
        virhe? (fn [rivi]
                 (if (cond
                       (and (= konteksti :urakka) (= silta-id :kaikki))
                       (let [d-osuus (:osuus (second (kentta-indeksilla rivi 7)))]
                         (and d-osuus (>= d-osuus korosta-kun-arvoa-d-vahintaan)))

                       (and (= konteksti :urakka) (not= silta-id :kaikki))
                       (= (kentta-indeksilla rivi 2) "D")

                       (= konteksti :hallintayksikko)
                       (let [d-osuus (:osuus (second (kentta-indeksilla rivi 4)))]
                         (and d-osuus (>= d-osuus korosta-kun-arvoa-d-vahintaan)))

                       (= konteksti :koko-maa)
                       (let [d-osuus (:osuus (second (kentta-indeksilla rivi 4)))]
                         (and d-osuus (>= d-osuus korosta-kun-arvoa-d-vahintaan)))

                       :else
                       false)
                   (liita rivi :virhe? true)
                   (liita rivi :virhe? false)))
        tarkastamaton? (fn [rivi]
                         (if (cond
                               (and (= konteksti :urakka) (= silta-id :kaikki))
                               (let [tarkastettu (kentta-indeksilla rivi 2)]
                                 (= tarkastettu tarkastamatta-info))

                               :else
                               false)
                           (liita rivi :tarkastamaton? true)
                           (liita rivi :tarkastamaton? false)))

        lihavoi (fn [rivi]
                  (if (:tarkastamaton? rivi) (liita rivi :lihavoi? true) (liita rivi :lihavoi? false)))
        korosta (fn [rivi]
                  (if (:virhe? rivi) (liita rivi :korosta? true) (liita rivi :korosta? false)))
        jarjesta (fn [rivit]
                   (let [indeksi (fn [i] #(nth (:rivi %) i))]
                     (vec (sort-by
                            (cond
                              (and (= konteksti :urakka) (= silta-id :kaikki))
                              (indeksi 0)

                              (and (= konteksti :hallintayksikko))
                              (indeksi 0)

                              (and (= konteksti :koko-maa))
                              (indeksi 0))
                            rivit))))
        jarjesta-ryhmien-sisallot (fn [tila-ja-rivit]
                                    (vec (apply concat (mapv (comp jarjesta val) tila-ja-rivit))))
        jarjesta-ryhmiin (fn [rivit]
                           (let [jarjestys (fn [a b] (let [arvo {[true false] 0 ;; kts. alla oleva juxt
                                                                 [false true] 1
                                                                 [false false] 2}]
                                                       (< (arvo a) (arvo b))))]
                             (into (sorted-map-by jarjestys) (group-by (juxt :tarkastamaton? :virhe?) rivit))))
        otsikko (case konteksti
                  :urakka
                  (if (= silta-id :kaikki)
                    (str raportin-nimi ", " (:nimi (first (urakat-q/hae-urakka db urakka-id))) " vuodelta " vuosi)
                    (str raportin-nimi ", " (:nimi (first (urakat-q/hae-urakka db urakka-id))) ", "
                         (str (:siltanimi yksittaisen-sillan-perustiedot)
                              " (" (:siltatunnus yksittaisen-sillan-perustiedot)) "), " vuosi))
                  :hallintayksikko
                  (str raportin-nimi ", " (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id))) " " vuosi)
                  :koko-maa
                  (str raportin-nimi ", KOKO MAA " vuosi))]

    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (if silta-id
                          "Sillalle ei ole tehty tarkastusta valittuna vuonna."
                          "Ei raportoitavia siltatarkastuksia.")
                 :viimeinen-rivi-yhteenveto? (or (and (= konteksti :urakka) (= silta-id :kaikki))
                                                 (= konteksti :hallintayksikko)
                                                 (= konteksti :koko-maa))
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      (cond
        (and (= konteksti :urakka) (= silta-id :kaikki))
        ;; Korostetaan, lihavoidaan, ja ja järjestetään datarivit
        ;; Viimeinen rivi on yhteenlaskurivi
        (conj (vec (->> datarivit
                        butlast
                        (map virhe?)
                        (map tarkastamaton?)
                        (map korosta)
                        (map lihavoi)
                        jarjesta-ryhmiin
                        jarjesta-ryhmien-sisallot))
              (last datarivit))
        (and (= konteksti :urakka) (not= silta-id :kaikki))
        (vec (->> datarivit
                  (map virhe?)
                  (map korosta)))
        (and (= konteksti :hallintayksikko))
        (conj (vec (->> datarivit
                        butlast
                        (map virhe?)
                        (map korosta)
                        jarjesta))
              (last datarivit))
        (and (= konteksti :koko-maa))
        (conj (vec (->> datarivit
                        butlast
                        jarjesta))
              (last datarivit))
        :else datarivit)]

     (when yksittaisen-sillan-perustiedot
       [:yhteenveto [["Tarkastaja" (:tarkastaja yksittaisen-sillan-perustiedot)]
                     ["Tarkastettu" (pvm/pvm-opt (:tarkastusaika yksittaisen-sillan-perustiedot))]]])

     (when (and (= konteksti :urakka)
                (> (:sillat-lkm urakan-vuoden-tarkastusmaarat) 0))
       [:yhteenveto [["Siltoja urakassa" (:sillat-lkm urakan-vuoden-tarkastusmaarat)]
                     [(str "Tarkastettu " vuosi) (str (:tarkastukset-lkm urakan-vuoden-tarkastusmaarat) " "
                                                      "(" (fmt/prosentti (* (/ (:tarkastukset-lkm urakan-vuoden-tarkastusmaarat)
                                                                               (:sillat-lkm urakan-vuoden-tarkastusmaarat))
                                                                            100))
                                                      ")")]
                     [(str "Tarkastamatta " vuosi) (str (- (:sillat-lkm urakan-vuoden-tarkastusmaarat)
                                                           (:tarkastukset-lkm urakan-vuoden-tarkastusmaarat))
                                                        " "
                                                        "(" (fmt/prosentti (* (/ (- (:sillat-lkm urakan-vuoden-tarkastusmaarat)
                                                                                    (:tarkastukset-lkm urakan-vuoden-tarkastusmaarat))
                                                                                 (:sillat-lkm urakan-vuoden-tarkastusmaarat))
                                                                              100))
                                                        ")")]]])]))
