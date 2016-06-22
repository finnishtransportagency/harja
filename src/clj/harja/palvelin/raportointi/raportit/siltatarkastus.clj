(ns harja.palvelin.raportointi.raportit.siltatarkastus
  (:require
    [jeesql.core :refer [defqueries]]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
    [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
    [harja.fmt :as fmt]
    [harja.pvm :as pvm]
    [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko-vuodella]]
    [taoensso.timbre :as log]
    [harja.domain.roolit :as roolit]
    [harja.kyselyt.konversio :as konv]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
    [harja.math :as math]
    [clj-time.coerce :as c]))

(defqueries "harja/palvelin/raportointi/raportit/siltatarkastus.sql")

(defn muodosta-sillan-datarivit [db urakka-id silta-id vuosi]
  (let [kohderivit (into []
                         (map konv/alaviiva->rakenne)
                         (hae-sillan-tarkastus db {:urakka urakka-id
                                                   :vuosi vuosi
                                                   :silta silta-id}))
        _ (log/debug "Raakadata:" (pr-str kohderivit))
        kohderivit (konv/sarakkeet-vektoriin
                     kohderivit
                     {:liite :liitteet}
                     :kohde)
        taulukkorivit (mapv
                        (fn [kohde]
                          [(:kohde kohde)
                           "" ;; TODO Kohteen selitys
                           (:tulos kohde)
                           (:lisatieto kohde)
                           [:liitteet (:liitteet kohde)]])
                        kohderivit)]
    taulukkorivit))

(defn muodosta-siltojen-datarivit [db urakka-id vuosi]
  (let [tarkastukset (into []
                           (map konv/alaviiva->rakenne)
                           (hae-urakan-siltatarkastukset db {:urakka urakka-id
                                                             :vuosi vuosi}))
        tarkastukset (konv/sarakkeet-vektoriin
                       tarkastukset
                       {:liite :liitteet})
        rivit (mapv
                (fn [tarkastus]
                  (let [arvioidut-kohteet-yhteensa (reduce + ((juxt :a :b :c :d) tarkastus))]
                    [(:siltanro tarkastus)
                    (:siltanimi tarkastus)
                    (if (:tarkastusaika tarkastus)
                      (fmt/pvm-opt (:tarkastusaika tarkastus))
                      "Tarkastamatta")
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
                tarkastukset)]
    rivit))

(defn muodosta-urakan-datarivit [db urakka-id silta-id vuosi]
  (if (= silta-id :kaikki)
    (muodosta-siltojen-datarivit db urakka-id vuosi)
    (muodosta-sillan-datarivit db urakka-id silta-id vuosi)))

(defn muodosta-hallintayksikon-datarivit []
  )

(defn muodosta-koko-maan-datarivit []
  )

(defn muodosta-raportin-otsikkorivit [db konteksti silta]
  (case konteksti
    :urakka (if (= silta :kaikki)
              [{:leveys 5 :otsikko "Siltanumero"}
               {:leveys 10 :otsikko "Silta"}
               {:leveys 5 :otsikko "Tarkastettu"}
               {:leveys 5 :otsikko "Tarkastaja"}
               {:leveys 5 :otsikko "A" :tyyppi :arvo-ja-osuus}
               {:leveys 5 :otsikko "B" :tyyppi :arvo-ja-osuus}
               {:leveys 5 :otsikko "C" :tyyppi :arvo-ja-osuus}
               {:leveys 5 :otsikko "D" :tyyppi :arvo-ja-osuus}
               {:leveys 5 :otsikko "Liitteet" :tyyppi :liite}]
              [{:leveys 2 :otsikko "#"}
               {:leveys 15 :otsikko "Kohde"}
               {:leveys 2 :otsikko "Tulos"}
               {:leveys 10 :otsikko "Lisätieto"}
               {:leveys 5 :otsikko "Liitteet"}])
    :hallintayksikko []
    :koko-maa []))

(defn muodosta-raportin-datarivit [db urakka-id konteksti silta-id vuosi]
  (case konteksti
    :urakka (muodosta-urakan-datarivit db urakka-id silta-id vuosi)
    :hallintayksikko (muodosta-hallintayksikon-datarivit)
    :koko-maa (muodosta-koko-maan-datarivit)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id silta-id vuosi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        otsikkorivit (muodosta-raportin-otsikkorivit db konteksti silta-id)
        datarivit (muodosta-raportin-datarivit db urakka-id konteksti silta-id vuosi)
        raportin-nimi "Siltatarkastusraportti"
        arvon-d-sisaltavat-rivi-indeksit (fn [datarivit]
                                           (into #{}
                                                 (keep-indexed
                                                   (fn [index rivi]
                                                     (let [d-osuus (:osuus (second (get rivi 7)))]
                                                       (when (and d-osuus (> d-osuus 0))
                                                         index)))
                                                   datarivit)))
        otsikko (raportin-otsikko-vuodella
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi vuosi)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (if silta-id
                           "Sillalle ei ole tehty tarkastusta valittuna vuonna."
                           "Ei raportoitavia siltatarkastuksia.")
                 :sheet-nimi raportin-nimi
                 :korosta-rivit (arvon-d-sisaltavat-rivi-indeksit datarivit)}
      otsikkorivit
      datarivit]]))
