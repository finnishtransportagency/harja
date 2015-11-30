(ns harja.palvelin.raportointi.raportit.ilmoitus
  "Materiaaliraportti"
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne +ilmoitustilat+]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoituspalvelu]
            [harja.pvm :as pvm]))


(defn muodosta-ilmoitusraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan ilmoitukset raporttia varten: " urakka-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [;; [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
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



(defn suorita [db user {:keys [urakka-id hk-alkupvm hk-loppupvm
                               hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [[konteksti ilmoitukset]
        (cond
          (and urakka-id hk-alkupvm hk-loppupvm)
          [:urakka (muodosta-ilmoitusraportti-urakalle db user {:urakka-id urakka-id
                                                                  :alkupvm hk-alkupvm
                                                                  :loppupvm hk-loppupvm})]

          (and hallintayksikko-id alkupvm loppupvm)
          [:hallintayksikko (muodosta-ilmoitusraportti-hallintayksikolle db user {:hallintayksikko-id hallintayksikko-id
                                                                                    :alkupvm alkupvm
                                                                                    :loppupvm loppupvm})]

          (and alkupvm loppupvm)
          [:koko-maa (muodosta-ilmoitusraportti-koko-maalle db user {:alkupvm alkupvm :loppupvm loppupvm})]

          :default
          (throw (Exception. "Tuntematon raportin konteksti")))
        otsikko (str (case konteksti
                       :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                       :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                       :koko-maa "KOKO MAA")
                     ", Ilmoitusraportti "
                     (pvm/pvm (or hk-alkupvm alkupvm)) " \u2010 " (pvm/pvm (or hk-loppupvm loppupvm)))
        ilmoitukset-urakan-mukaan (group-by :urakka ilmoitukset)]
    [:raportti {:nimi otsikko}
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
            (let [urakan-nimi (:nimi (first (urakat-q/hae-urakka db urakka)))
                  tpp (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                  urk (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                  tur (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
              [urakan-nimi tpp urk tur]))

          ;; Tehdään yhteensä rivi, jossa kaikki ilmoitukset lasketaan yhteen materiaalin perusteella
          (let [tpp-yht (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                urk-yht (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                tur-yht (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
            [(concat ["Yhteensä"]
                     [tpp-yht urk-yht tur-yht])])))]]))

    
