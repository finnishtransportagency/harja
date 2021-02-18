(ns harja.palvelin.raportointi.raportit.kelitarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.domain.hoitoluokat :as hoitoluokat]))

(defn hae-tarkastukset-urakalle [db user {:keys [urakka-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-urakan-kelitarkastukset-liitteineen-raportille db
                                                                     urakka-id
                                                                     alkupvm
                                                                     loppupvm
                                                                     (not (nil? tienumero))
                                                                     tienumero
                                                                     (roolit/urakoitsija? user)))

(defn hae-tarkastukset-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi]}]
  (tarkastukset-q/hae-hallintayksikon-kelitarkastukset-liitteineen-raportille db
                                                                              hallintayksikko-id
                                                                              (when urakkatyyppi (name urakkatyyppi))
                                                                              alkupvm
                                                                              loppupvm
                                                                              (not (nil? tienumero))
                                                                              tienumero
                                                                              (roolit/urakoitsija? user)))

(defn hae-tarkastukset-koko-maalle [db user {:keys [alkupvm loppupvm tienumero urakkatyyppi]}]
  (tarkastukset-q/hae-koko-maan-kelitarkastukset-liitteineen-raportille db
                                                                        (when urakkatyyppi (name urakkatyyppi))
                                                                        alkupvm
                                                                        loppupvm
                                                                        (not (nil? tienumero))
                                                                        tienumero
                                                                        (roolit/urakoitsija? user)))

(defn hae-tarkastukset [db user {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi]}]
  (case konteksti
    :urakka
    (hae-tarkastukset-urakalle db
                               user
                               {:urakka-id urakka-id
                                :alkupvm   alkupvm
                                :loppupvm  loppupvm
                                :tienumero tienumero})
    :hallintayksikko
    (hae-tarkastukset-hallintayksikolle db
                                        user
                                        {:hallintayksikko-id hallintayksikko-id
                                         :alkupvm alkupvm
                                         :loppupvm loppupvm
                                         :tienumero tienumero
                                         :urakkatyyppi urakkatyyppi})
    :koko-maa
    (hae-tarkastukset-koko-maalle db
                                  user
                                  {:alkupvm alkupvm
                                   :loppupvm loppupvm
                                   :tienumero tienumero
                                   :urakkatyyppi urakkatyyppi})))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit (map konv/alaviiva->rakenne
                               (hae-tarkastukset db user
                                                 {:konteksti konteksti
                                                     :urakka-id urakka-id
                                                     :hallintayksikko-id hallintayksikko-id
                                                     :alkupvm alkupvm
                                                     :loppupvm loppupvm
                                                     :tienumero tienumero
                                                     :urakkatyyppi urakkatyyppi}))
        naytettavat-rivit (konv/sarakkeet-vektoriin
                            naytettavat-rivit
                            {:liite :liitteet})
        raportin-nimi "Kelitarkastusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tarkastuksia.")
                 :sheet-nimi raportin-nimi}
      [{:leveys 10 :otsikko "Päivämäärä" :fmt :pvm}
       {:leveys 5 :otsikko "Klo"}
       {:leveys 5 :otsikko "Tie"}
       {:leveys 5 :otsikko "Aosa"}
       {:leveys 5 :otsikko "Aet"}
       {:leveys 5 :otsikko "Losa"}
       {:leveys 5 :otsikko "Let"}
       {:leveys 5 :otsikko "Ajo\u00ADsuun\u00ADta"}
       {:leveys 5 :otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
       {:leveys 5 :otsikko "Lu\u00ADmi\u00ADmää\u00ADrä (cm)" :fmt :numero}
       {:leveys 5 :otsikko "Ta\u00ADsai\u00ADsuus (cm)" :fmt :numero}
       {:leveys 5 :otsikko "Kit\u00ADka" :fmt :numero}
       {:leveys 5 :otsikko "Ilman läm\u00ADpö\u00ADti\u00ADla" :fmt :numero}
       {:leveys 5 :otsikko "Tien läm\u00ADpö\u00ADti\u00ADla" :fmt :numero}
       {:leveys 5 :otsikko "Tar\u00ADkas\u00ADtaja"}
       {:leveys 10 :otsikko "Ha\u00ADvain\u00ADnot"}
       {:leveys 3 :otsikko "Laadun alitus"}
       {:leveys 5 :otsikko "Liit\u00ADteet" :tyyppi :liite}]
      (yleinen/ryhmittele-tulokset-raportin-taulukolle
        (reverse (sort-by (fn [rivi] [(:aika rivi)
                                      (get-in rivi [:tr :numero])])
                          naytettavat-rivit))
        :urakka
        (fn [rivi]
          [(pvm/pvm (:aika rivi))
           (pvm/aika (:aika rivi))
           (get-in rivi [:tr :numero])
           (get-in rivi [:tr :alkuosa])
           (get-in rivi [:tr :alkuetaisyys])
           (get-in rivi [:tr :loppuosa])
           (get-in rivi [:tr :loppuetaisyys])
           (:ajosuunta rivi)
           (hoitoluokat/talvihoitoluokan-nimi-str (:talvihoitoluokka rivi))
           (:lumimaara rivi)
           (:tasaisuus rivi)
           (:kitka rivi)
           (get-in rivi [:lampotila :ilma])
           (get-in rivi [:lampotila :tie])
           (:tarkastaja rivi)
           (:havainnot rivi)
           (fmt/totuus (:laadunalitus rivi))
           [:liitteet (:liitteet rivi)]]))]]))
