(ns harja.palvelin.raportointi.raportit.ilmoitus
  "Ilmoitusraportti"
  (:require [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.domain.roolit :as roolit]
            [clj-time.coerce :as tc]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne +ilmoitustilat+]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoituspalvelu]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [harja.pvm :as pvm]))


(defn muodosta-ilmoitusraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan ilmoitukset raporttia varten. Urakka-id: " urakka-id
             " alkupvm: " alkupvm " loppupvm: " loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [;; [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
        ;; haetaan urakan konttekstissa aina kuukauden tiedot. Sopii yhteen työmaakokouskäytännön kanssa.
        ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user nil urakka-id +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)]
    ilmoitukset))

(defn muodosta-ilmoitusraportti-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (log/debug "Haetaan hallintayksikon toteutuneet materiaalit raporttia varten: " hallintayksikko-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user hallintayksikko-id nil +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)]
    ilmoitukset))

(defn muodosta-ilmoitusraportti-koko-maalle [db user {:keys [alkupvm loppupvm]}]
  (log/debug "Haetaan koko maan toteutuneet materiaalit raporttia varten: " alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user nil nil +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)]
    ilmoitukset))



(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user hallintayksikko-id urakka-id +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        alkupvm-ed-kuussa (t/minus (l/to-local-date-time alkupvm) (t/months 1))
        loppupvm-ed-kuussa (t/last-day-of-the-month
                             (t/minus (l/to-local-date-time alkupvm) (t/months 1)))
        ilmoitukset-viime-kuussa (when kyseessa-kk-vali?
                                         (ilmoituspalvelu/hae-ilmoitukset
                                           db user hallintayksikko-id urakka-id +ilmoitustilat+ +ilmoitustyypit+
                                           [(tc/to-date alkupvm-ed-kuussa) (tc/to-date loppupvm-ed-kuussa)] ""))
        raportin-nimi "Ilmoitusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        viime-kuun-otsikko (when kyseessa-kk-vali?
                             (raportin-otsikko
                               (case konteksti
                                 :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                                 :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                                 :koko-maa "KOKO MAA")
                               raportin-nimi (tc/to-date alkupvm-ed-kuussa) (tc/to-date loppupvm-ed-kuussa)))
        ilmoitukset-urakan-mukaan (group-by :urakka ilmoitukset)
        viime-kuun-ilmoitukset-urakan-mukaan (when kyseessa-kk-vali? (group-by :urakka ilmoitukset-viime-kuussa))]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :viimeinen-rivi-yhteenveto? true}
      (into []
            (concat
              [{:otsikko "Urakka"}]
              (map (fn [ilmoitustyyppi]
                     {:otsikko (str (ilmoitustyypin-lyhenne ilmoitustyyppi)
                                    " ("
                                    (ilmoitustyypin-nimi ilmoitustyyppi)
                                    ")")})
                   [:toimenpidepyynto :kysely :tiedoitus])))
      (into
        []
        (concat
          ;; Tehdään rivi jokaiselle urakalle, ja näytetään niiden erityyppistem ilmoitusten määrä
          (for [[urakka ilmoitukset] ilmoitukset-urakan-mukaan]
            (let [urakan-nimi (or (:nimi (first (urakat-q/hae-urakka db urakka))) "Ei urakkaa")
                  tpp (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                  urk (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                  tur (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
              [urakan-nimi tpp urk tur]))

          ;; Tehdään yhteensä rivi, jossa kaikki ilmoitukset lasketaan yhteen materiaalin perusteella
          (let [tpp-yht (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                urk-yht (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                tur-yht (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
            [(concat ["Yhteensä"]
                     [tpp-yht urk-yht tur-yht])])))]

     ;; jos kk-rapsa, näytetään viime kuun tilannekin
     (when kyseessa-kk-vali?
       [:taulukko {:otsikko viime-kuun-otsikko
                   :viimeinen-rivi-yhteenveto? true}
        (into []
              (concat
                [{:otsikko "Urakka"}]
                (map (fn [ilmoitustyyppi]
                       {:otsikko (str (ilmoitustyypin-lyhenne ilmoitustyyppi)
                                      " ("
                                      (ilmoitustyypin-nimi ilmoitustyyppi)
                                      ")")})
                     [:toimenpidepyynto :kysely :tiedoitus])))
        (into
          []
          (concat
            ;; Tehdään rivi jokaiselle urakalle, ja näytetään niiden erityyppistem ilmoitusten määrä
            (for [[urakka ilmoitukset] viime-kuun-ilmoitukset-urakan-mukaan]
              (let [urakan-nimi (or (:nimi (first (urakat-q/hae-urakka db urakka))) "Ei urakkaa")
                    tpp (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                    urk (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                    tur (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
                [urakan-nimi tpp urk tur]))

            ;; Tehdään yhteensä rivi, jossa kaikki ilmoitukset lasketaan yhteen materiaalin perusteella
            (let [tpp-yht (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                  urk-yht (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                  tur-yht (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
              [(concat ["Yhteensä"]
                       [tpp-yht urk-yht tur-yht])])))])
     ]))

    
