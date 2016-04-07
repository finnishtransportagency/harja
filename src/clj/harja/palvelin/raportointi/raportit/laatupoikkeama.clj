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
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clj-time.coerce :as c]))

(defn hae-laatupoikkeamat-urakalle [db {:keys [urakka-id alkupvm loppupvm laatupoikkeamatekija]}]
  (laatupoikkeamat-q/hae-urakan-laatupoikkeamat-liitteineen-raportille db
                                                                       urakka-id
                                                                       alkupvm
                                                                       loppupvm
                                                                       (not (nil? laatupoikkeamatekija))
                                                                       laatupoikkeamatekija))

(defn hae-laatupoikkeamat-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm laatupoikkeamatekija urakkatyyppi]}]
  (laatupoikkeamat-q/hae-hallintayksikon-laatupoikkeamat-liitteineen-raportille db
                                                                                hallintayksikko-id
                                                                                (when urakkatyyppi (name urakkatyyppi))
                                                                                alkupvm
                                                                                loppupvm
                                                                                (not (nil? laatupoikkeamatekija))
                                                                                laatupoikkeamatekija))

(defn hae-laatupoikkeamat-koko-maalle [db {:keys [alkupvm loppupvm laatupoikkeamatekija urakkatyyppi]}]
  (laatupoikkeamat-q/hae-koko-maan-laatupoikkeamat-liitteineen-raportille db
                                                                          (when urakkatyyppi (name urakkatyyppi))
                                                                          alkupvm
                                                                          loppupvm
                                                                          (not (nil? laatupoikkeamatekija))
                                                                          laatupoikkeamatekija))

(defn hae-laatupoikkeamat [db {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm laatupoikkeamatekija urakkatyyppi]}]
  (case konteksti
    :urakka
    (hae-laatupoikkeamat-urakalle db
                                  {:urakka-id urakka-id
                                   :alkupvm   alkupvm
                                   :loppupvm  loppupvm
                                   :laatupoikkeamatekija laatupoikkeamatekija})
    :hallintayksikko
    (hae-laatupoikkeamat-hallintayksikolle db
                                           {:hallintayksikko-id   hallintayksikko-id
                                            :alkupvm              alkupvm
                                            :loppupvm             loppupvm
                                            :laatupoikkeamatekija laatupoikkeamatekija
                                            :urakkatyyppi urakkatyyppi})
    :koko-maa
    (hae-laatupoikkeamat-koko-maalle db
                                     {:alkupvm              alkupvm
                                      :loppupvm             loppupvm
                                      :laatupoikkeamatekija laatupoikkeamatekija
                                      :urakkatyyppi urakkatyyppi})))



(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm laatupoikkeamatekija urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        laatupoikkeamarivit (map konv/alaviiva->rakenne
                                 (hae-laatupoikkeamat db {:konteksti konteksti
                                                          :urakka-id urakka-id
                                                          :hallintayksikko-id hallintayksikko-id
                                                          :alkupvm alkupvm
                                                          :loppupvm loppupvm
                                                          :laatupoikkeamatekija (when (not= laatupoikkeamatekija :kaikki)
                                                                                  (name laatupoikkeamatekija))
                                                          :urakkatyyppi urakkatyyppi}))
        laatupoikkeamarivit (konv/sarakkeet-vektoriin
                            laatupoikkeamarivit
                            {:liite :liitteet})
        nayta-pylvaat? (= laatupoikkeamatekija :kaikki)
        laatupoikkeamat-kuukausittain (when nayta-pylvaat?
                                        (yleinen/rivit-kuukausipylvaille-kentan-eri-arvojen-maaraa-laskien
                                        laatupoikkeamarivit
                                        :aika
                                        :tekija
                                        ["urakoitsija" "tilaaja" "konsultti"]))
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
                 :tyhja   (if (empty? laatupoikkeamarivit) "Ei raportoitavia laatupoikkeamia.")}
      [{:leveys 15 :otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
       {:leveys 20 :otsikko "Koh\u00ADde"}
       {:leveys 10 :otsikko "Te\u00ADki\u00ADjä"}
       {:leveys 35 :otsikko "Ku\u00ADvaus"}
       {:leveys 25 :otsikko "Liit\u00ADtei\u00ADtä"}]
      (yleinen/ryhmittele-tulokset-raportin-taulukolle
        (reverse (sort-by :aika laatupoikkeamarivit))
        :urakka
        (fn [rivi]
          [(pvm/pvm (:aika rivi))
           (:kohde rivi)
           (:tekija rivi)
           (:kuvaus rivi)
           (count (:liitteet rivi))]))]

     (when nayta-pylvaat?
       (if-not (empty? laatupoikkeamat-kuukausittain)
         (yleinen/pylvaat-kuukausittain {:otsikko              "Laatupoikkeamat kuukausittain"
                                         :alkupvm              alkupvm
                                         :loppupvm             loppupvm
                                         :kuukausittainen-data laatupoikkeamat-kuukausittain
                                         :piilota-arvo?        #{0}
                                         :legend               ["Urakoitsija" "Tilaaja" "Konsultti"]})
         (yleinen/ei-osumia-aikavalilla-teksti "laatupoikkeamia"
                                               alkupvm
                                               loppupvm)))]))