(ns harja.palvelin.raportointi.raportit.laatupoikkeama
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defn hae-laatupoikkeamat-urakalle [db {:keys [urakka-id alkupvm loppupvm]}]
  (laatupoikkeamat-q/hae-urakan-laatupoikkeamat-liitteineen-raportille db
                                                                       urakka-id
                                                                       alkupvm
                                                                       loppupvm))

(defn hae-laatupoikkeamat-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (laatupoikkeamat-q/hae-hallintayksikon-laatupoikkeamat-liitteineen-raportille db
                                                                                hallintayksikko-id
                                                                                alkupvm
                                                                                loppupvm))

(defn hae-laatupoikkeamat-koko-maalle [db {:keys [alkupvm loppupvm]}]
  (laatupoikkeamat-q/hae-koko-maan-laatupoikkeamat-liitteineen-raportille db
                                                                          alkupvm
                                                                          loppupvm))

(defn hae-laatupoikkeamat [db {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm]}]
  (case konteksti
    :urakka
    (hae-laatupoikkeamat-urakalle db
                                  {:urakka-id urakka-id
                                   :alkupvm   alkupvm
                                   :loppupvm  loppupvm})
    :hallintayksikko
    (hae-laatupoikkeamat-hallintayksikolle db
                                           {:hallintayksikko-id hallintayksikko-id
                                            :alkupvm            alkupvm
                                            :loppupvm           loppupvm})
    :koko-maa
    (hae-laatupoikkeamat-koko-maalle db
                                     {:alkupvm   alkupvm
                                      :loppupvm  loppupvm})))



(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit (map konv/alaviiva->rakenne
                               (hae-laatupoikkeamat db {:konteksti          konteksti
                                                        :urakka-id          urakka-id
                                                        :hallintayksikko-id hallintayksikko-id
                                                        :alkupvm            alkupvm
                                                        :loppupvm           loppupvm}))
        naytettavat-rivit (konv/sarakkeet-vektoriin
                            naytettavat-rivit
                            {:liite :liitteet})
        raportin-nimi "Laatupoikkeamaraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia laatupoikkeamia.")}
      (flatten (keep identity [{:leveys "10%" :otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
                               {:leveys "5%" :otsikko "Koh\u00ADde"}
                               {:leveys "6%" :otsikko "Ku\u00ADvaus"}
                               {:leveys "6%" :otsikko "Liit\u00ADtei\u00ADtä"}]))
      (yleinen/ryhmittele-tulokset-raportin-taulukolle
        naytettavat-rivit
        :urakka
        (fn [rivi]
          [(pvm/pvm (:aika rivi))
           (:kohde rivi)
           (:kuvaus)
           (count (:liitteet rivi))]))]]))