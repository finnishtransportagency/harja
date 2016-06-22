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
    [clj-time.coerce :as c]))

(defqueries "harja/palvelin/raportointi/raportit/siltatarkastus.sql")

(defn muodosta-sillan-datarivit []
  )

(defn muodosta-siltojen-datarivit [db urakka-id vuosi]
  (let [tarkastukset (into []
                           (map konv/alaviiva->rakenne)
                           (hae-urakan-siltatarkastukset db {:urakka urakka-id
                                                       :vuosi vuosi}))
        _ (log/debug "Datarivit kannasta: " (pr-str tarkastukset))
        tarkastukset (konv/sarakkeet-vektoriin
                       tarkastukset
                       {:liite :liitteet})
        rivit (mapv
                (fn [tarkastus]
                  [(:siltanro tarkastus)
                   (:siltanimi tarkastus)
                   (if (:tarkastusaika tarkastus)
                     (fmt/pvm-opt (:tarkastusaika tarkastus))
                     "Tarkastamatta")
                   (or (:tarkastaja tarkastus)
                       "-")
                   (or (:a tarkastus) "-")
                   (or (:b tarkastus) "-")
                   (or (:c tarkastus) "-")
                   (or (:d tarkastus) "-")
                   [:liitteet (:liitteet tarkastus)]])
                tarkastukset)]
    rivit))

(defn muodosta-urakan-datarivit [db urakka-id silta vuosi]
  (if (= silta :kaikki)
    (muodosta-siltojen-datarivit db urakka-id vuosi)
    (muodosta-sillan-datarivit)))

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
               {:leveys 5 :otsikko "A summa"}
               {:leveys 5 :otsikko "B summa"}
               {:leveys 5 :otsikko "C summa"}
               {:leveys 5 :otsikko "D summa"}
               {:leveys 5 :otsikko "Liitteet" :tyyppi :liite}]
              [{:leveys 2 :otsikko "#"}
               {:leveys 15 :otsikko "Kohde"}
               {:leveys 2 :otsikko "Tulos"}
               {:leveys 10 :otsikko "Lisätieto"}
               {:leveys 5 :otsikko "Liitteet"}])
    :hallintayksikko []
    :koko-maa []))

(defn muodosta-raportin-datarivit [db urakka-id konteksti silta vuosi]
  (case konteksti
    :urakka (muodosta-urakan-datarivit db urakka-id silta vuosi)
    :hallintayksikko (muodosta-hallintayksikon-datarivit)
    :koko-maa (muodosta-koko-maan-datarivit)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id silta vuosi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        otsikkorivit (muodosta-raportin-otsikkorivit db konteksti silta)
        datarivit (muodosta-raportin-datarivit db urakka-id konteksti silta vuosi)
        raportin-nimi "Siltatarkastusraportti"
        otsikko (raportin-otsikko-vuodella
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi vuosi)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? datarivit) "Ei raportoitavia siltatarkastuksia.")
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))
