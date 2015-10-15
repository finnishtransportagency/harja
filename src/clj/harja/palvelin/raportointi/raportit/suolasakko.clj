(ns harja.palvelin.raportointi.raportit.suolasakko
  "Suolasakkoraportti"
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/suolasakkoraportti.sql")

(defn muodosta-suolasakkoraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan tiedot materiaaliraportille: " urakka-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteuma-parametrit [db
                            urakka-id
                            #_(.getYear (konv/sql-timestamp alkupvm)) ; FIXME Kannan pvm:n käsittely bugittaa :(
                            #_(konv/sql-timestamp alkupvm) ; FIXME Kannan pvm:n käsittely bugittaa :(
                            #_(konv/sql-timestamp loppupvm)] ; FIXME Kannan pvm:n käsittely bugittaa :(
        raportin-tiedot (into [] (apply hae-tiedot-urakan-suolasakkoraportille toteuma-parametrit))]
    (log/debug (str "Raporttidata saatu: " (pr-str raportin-tiedot)))
    raportin-tiedot))

; TODO
#_(defn muodosta-materiaaliraportti-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (log/debug "Haetaan hallintayksikon toteutuneet materiaalit raporttia varten: " hallintayksikko-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteutuneet-materiaalit (into []
                                      (materiaalit-q/hae-hallintayksikon-toteutuneet-materiaalit-raportille db
                                                                                                            (konv/sql-timestamp alkupvm)
                                                                                                            (konv/sql-timestamp loppupvm)
                                                                                                            hallintayksikko-id))]
    toteutuneet-materiaalit))

; TODO
#_(defn muodosta-materiaaliraportti-koko-maalle [db user {:keys [alkupvm loppupvm]}]
  (log/debug "Haetaan koko maan toteutuneet materiaalit raporttia varten: " alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteutuneet-materiaalit (into []
                                      (materiaalit-q/hae-koko-maan-toteutuneet-materiaalit-raportille db
                                                                                                      (konv/sql-timestamp alkupvm)
                                                                                                      (konv/sql-timestamp loppupvm)))]
    toteutuneet-materiaalit))



(defn suorita [db user {:keys [urakka-id hk-alkupvm hk-loppupvm
                               hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [[konteksti raportin-data]
        (cond
          (and urakka-id hk-alkupvm hk-loppupvm)
          [:urakka (muodosta-suolasakkoraportti-urakalle db user {:urakka-id urakka-id
                                                                  :alkupvm hk-alkupvm
                                                                  :loppupvm hk-loppupvm})]

          ; TODO
          #_(and hallintayksikko-id alkupvm loppupvm)
          #_[:hallintayksikko (muodosta-materiaaliraportti-hallintayksikolle db user {:hallintayksikko-id hallintayksikko-id
                                                                                    :alkupvm alkupvm
                                                                                    :loppupvm loppupvm})]
          ; TODO
          #_(and alkupvm loppupvm)
          #_[:koko-maa (muodosta-materiaaliraportti-koko-maalle db user {:alkupvm alkupvm :loppupvm loppupvm})]

          :default
          ;; FIXME Pitäisikö tässä heittää jotain, tänne ei pitäisi päästä, jos parametrit ovat oikein?
          nil)
        otsikko (str (case konteksti
                       :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                       :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                       :koko-maa "KOKO MAA")
                     ", Suolabonus/sakkoraportti "
                     (pvm/pvm (or hk-alkupvm alkupvm)) " \u2010 " (pvm/pvm (or hk-loppupvm loppupvm)))]
    (let [view [:raportti {:nimi otsikko}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true}
      [{:leveys "10%" :otsikko "Urakka"}
         {:leveys "10%" :otsikko "Keskiläpötila"}
         {:leveys "10%" :otsikko "Pitkän aikavälin keskilämpötila"}
         {:leveys "10%" :otsikko "Sopimuksen mukainen suolamäärä"}
         {:leveys "10%" :otsikko "Käytetty suolamäärä"}
         {:leveys "10%" :otsikko "Suolaerotus"}
         {:leveys "10%" :otsikko "Sakko/Bonus"}]
        (for [rivi raportin-data]
           [(:urakka_nimi rivi)
            (str (:keskilampotila rivi) " C")
            (str (:pitkakeskilampotila rivi) "C")
            0 ; TODO
            0
            0
            0])]]]
      (log/debug "View: " view)
      view)))