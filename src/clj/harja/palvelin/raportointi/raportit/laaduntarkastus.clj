(ns harja.palvelin.raportointi.raportit.laaduntarkastus
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
            [harja.domain.hoitoluokat :as hoitoluokat]
            [clojure.string :as str]))

(defn hae-tarkastukset [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero
                                   laadunalitus]}]
  (tarkastukset-q/hae-laaduntarkastukset db
                                         {:urakka urakka-id
                                          :hallintayksikko hallintayksikko-id
                                          :alku alkupvm
                                          :loppu loppupvm
                                          :tienumero tienumero
                                          :laadunalitus laadunalitus
                                          :kayttaja_on_urakoitsija (roolit/urakoitsija? user)}))


(defn talvihoitomittaus [{:keys [talvihoitoluokka lumimaara tasaisuus kitka lampotila]}]
  (str/join
   " | "
   (remove nil?
           [(when talvihoitoluokka (str "Thlk: " (hoitoluokat/talvihoitoluokan-nimi-str talvihoitoluokka)))
            (when lumimaara (str "Lumi: " (fmt/desimaaliluku lumimaara 1)))
            (when tasaisuus (str "Tasaisuus: " (fmt/desimaaliluku tasaisuus 1)))
            (when kitka (str "Kitka: " (fmt/desimaaliluku kitka)))
            (when-let [lampotila (:tie lampotila)]
              (str "Tie: " (fmt/desimaaliluku lampotila 1) " \u2103"))
            (when-let [lampotila (:ilma lampotila)]
              (str "Ilma: " (fmt/desimaaliluku lampotila 1) " \u2103 "))])))

(defn soratiemittaus [{:keys [hoitoluokka tasaisuus kiinteys polyavyys sivukaltevuus]}]
  (str/join
   " | "
   (remove nil?
           [(when hoitoluokka (str "Slk: " (hoitoluokat/soratieluokan-nimi hoitoluokka)))
            (when tasaisuus (str "Tas: " tasaisuus))
            (when kiinteys (str "Kiint: " kiinteys))
            (when polyavyys (str "Pölyäv: " polyavyys))
            (when sivukaltevuus (str "Sivukalt: "
                                     (fmt/desimaaliluku sivukaltevuus 1) "%"))])))

(defn- formatoi-vakiohavainnot [vakiohavainnot]
  (str (clojure.string/join ", " vakiohavainnot) (when-not (empty? vakiohavainnot) ", ")))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit (map konv/alaviiva->rakenne
                               (hae-tarkastukset
                                db user {:konteksti konteksti
                                    :urakka-id urakka-id
                                    :hallintayksikko-id hallintayksikko-id
                                    :alkupvm alkupvm
                                    :loppupvm loppupvm
                                    :tienumero tienumero
                                    :laadunalitus (if (parametrit "Vain laadun alitukset")
                                                    true
                                                    nil)}))
        naytettavat-rivit (konv/sarakkeet-vektoriin
                            naytettavat-rivit
                            {:liite :liitteet})
        raportin-nimi "Laaduntarkastusraportti"
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
      [{:leveys 4 :otsikko "Päivämäärä"}
       {:leveys 2 :otsikko "Klo"}
       {:leveys 2 :otsikko "Tie"}
       {:leveys 2 :otsikko "Aosa"}
       {:leveys 2 :otsikko "Aet"}
       {:leveys 2 :otsikko "Losa"}
       {:leveys 2 :otsikko "Let"}
       {:leveys 3 :otsikko "Tar\u00ADkas\u00ADtaja"}
       {:leveys 8 :otsikko "Mittaus"}
       {:leveys 10 :otsikko "Ha\u00ADvain\u00ADnot"}
       {:leveys 2 :otsikko "Laadun alitus"}
       {:leveys 3 :otsikko "Liit\u00ADteet" :tyyppi :liite}]
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
           (:tarkastaja rivi)
           (str (when (:id (:talvihoitomittaus rivi))
                  (talvihoitomittaus (:talvihoitomittaus rivi)))
                (when (:id (:soratiemittaus rivi))
                  (soratiemittaus (:soratiemittaus rivi))))
           (let [vakiohavainnot (:vakiohavainnot (konv/array->vec rivi :vakiohavainnot))]
             (str (formatoi-vakiohavainnot vakiohavainnot)
                  (:havainnot rivi)))
           (fmt/totuus (:laadunalitus rivi))
           [:liitteet (:liitteet rivi)]]))]]))
