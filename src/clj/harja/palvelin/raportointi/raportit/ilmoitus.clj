(ns harja.palvelin.raportointi.raportit.ilmoitus
  "Materiaaliraportti"
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.kyselyt.ilmoitukset :as ilmoitukset-q]
            [harja.kyselyt.materiaalit :as materiaalit-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoituspalvelu]
            [harja.pvm :as pvm]))


(defn muodosta-ilmoitusraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan ilmoitukset raporttia varten: " urakka-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [;; [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
        ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user nil urakka-id +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)

        lopullinen-tulos (mapv
                           (fn [ilmoitus]
                             (if true
                               ilmoitus))
                           ilmoitukset)]
    lopullinen-tulos))

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
        ilmoitukset-urakan-mukaan (group-by :urakka ilmoitukset)
        _ (log/debug "ilmoitukset " ilmoitukset)
        _ (log/debug "ilmoitukset-urakan-mukaan " ilmoitukset-urakan-mukaan)]
    [:raportti {:nimi otsikko}
     [:taulukko {:otsikko otsikko
                 :viimeinen-rivi-yhteenveto? true}
      (into []
            (concat 
             [{:otsikko "Urakka"}]
             (map (fn [ilmoitus]
                    {:ilmoitustyyppi ilmoitus}) ilmoitukset)))
      (into
       []
       (concat
        ;; Tehdään rivi jokaiselle urakalle, jossa sen yhteenlasketut ilmoitukset
        (for [[urakka ilmoitukset] ilmoitukset-urakan-mukaan]

          (do
            (log/debug "ilmoitukset urakan " urakka " mukaan:" ilmoitukset)
            (into []
                 (concat [urakka]
                         (let [ilm (group-by :ilmoitustyyppi ilmoitukset-urakan-mukaan)]
                           [(count ilm)])))))

        ;; Tehdään yhteensä rivi, jossa kaikki ilmoitukset lasketaan yhteen materiaalin perusteella
        [(concat ["Yhteensä"]
                 (let [ilm (group-by :ilmoitustyyppi ilmoitukset-urakan-mukaan)]
                   [(count ilm)]))]))]]))

    
