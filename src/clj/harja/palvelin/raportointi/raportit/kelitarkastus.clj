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
            [harja.kyselyt.konversio :as konv]))

(defn hae-tarkastukset-urakalle [db {:keys [urakka-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-urakan-kelitarkastukset-liitteineen-raportille db
                                                                       urakka-id
                                                                       alkupvm
                                                                       loppupvm
                                                                       (not (nil? tienumero))
                                                                       tienumero))

(defn hae-tarkastukset-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-hallintayksikon-kelitarkastukset-liitteineen-raportille db
                                                                                hallintayksikko-id
                                                                                alkupvm
                                                                                loppupvm
                                                                                (not (nil? tienumero))
                                                                                tienumero))

(defn hae-tarkastukset-koko-maalle [db {:keys [alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-koko-maan-kelitarkastukset-liitteineen-raportille db
                                                                          alkupvm
                                                                          loppupvm
                                                                          (not (nil? tienumero))
                                                                          tienumero))

(defn hae-tarkastukset [db {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm tienumero]}]
  (case konteksti
    :urakka
    (hae-tarkastukset-urakalle db
                               {:urakka-id urakka-id
                                :alkupvm   alkupvm
                                :loppupvm  loppupvm
                                :tienumero tienumero})
    :hallintayksikko
    (hae-tarkastukset-hallintayksikolle db
                                        {:hallintayksikko-id hallintayksikko-id
                                         :alkupvm            alkupvm
                                         :loppupvm           loppupvm
                                         :tienumero          tienumero})
    :koko-maa
    (hae-tarkastukset-koko-maalle db
                                  {:alkupvm   alkupvm
                                   :loppupvm  loppupvm
                                   :tienumero tienumero})))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit (map konv/alaviiva->rakenne
                               (hae-tarkastukset db {:konteksti          konteksti
                                                           :urakka-id          urakka-id
                                                           :hallintayksikko-id hallintayksikko-id
                                                           :alkupvm            alkupvm
                                                           :loppupvm           loppupvm
                                                           :tienumero          tienumero}))
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
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tarkastuksia.")}
      (flatten (keep identity [{:leveys "10%" :otsikko "Päivämäärä"}
                               {:leveys "5%" :otsikko "Klo"}
                               {:leveys "5%" :otsikko "Tie"}
                               {:leveys "5%" :otsikko "Aosa"}
                               {:leveys "5%" :otsikko "Aet"}
                               {:leveys "5%" :otsikko "Losa"}
                               {:leveys "5%" :otsikko "Let"}
                               {:leveys "5%" :otsikko "Ajo\u00ADsuun\u00ADta"}
                               {:leveys "5%" :otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
                               {:leveys "5%" :otsikko "Mit\u00ADtaaja"}
                               {:leveys "5%" :otsikko "Laa\u00ADtu\u00ADvir\u00ADhe"}
                               {:leveys "5%" :otsikko "Lu\u00ADmi\u00ADmää\u00ADrä (cm)"}
                               {:leveys "5%" :otsikko "E\u00ADpä\u00ADta\u00ADsai\u00ADsuus (cm)"}
                               {:leveys "5%" :otsikko "Kit\u00ADka"}
                               {:leveys "5%" :otsikko "Läm\u00ADpö\u00ADti\u00ADla"}
                               {:leveys "5%" :otsikko "Tar\u00ADkas\u00ADtaja"}
                               {:leveys "10%" :otsikko "Ha\u00ADvain\u00ADnot"}
                               {:leveys "5%" :otsikko "Liit\u00ADteet"}]))
      (mapv (fn [rivi]
              [(pvm/pvm (:aika rivi))
               (pvm/aika (:aika rivi))
               (get-in rivi [:tr :numero])
               (get-in rivi [:tr :alkuosa])
               (get-in rivi [:tr :alkuetaisyys])
               (get-in rivi [:tr :loppuosa])
               (get-in rivi [:tr :loppyetaisyys])
               (get-in rivi [:tr :ajosuunta])
               (:hoitoluokka rivi)
               (:mittaaja rivi)
               (:laatuvirhe rivi)
               (:lumimaara rivi)
               (:epatasaisuus rivi)
               (:kitka rivi)
               (:lampotila rivi)
               (:tarkastaja rivi)
               (:havainnot rivi)
               (clojure.string/join " " (map :nimi (:liitteet rivi)))])
            naytettavat-rivit)]]))