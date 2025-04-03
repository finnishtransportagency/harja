(ns harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset
  "Tiemerkinnän kustannuks raportit"
  (:require [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.kanavat.raportointi :as k-raportointi]
            [harja.palvelin.palvelut.yllapito-toteumat :as yllapito-palvelu]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]))

(defonce ^{:private true} raportti-yhteenveto-otsikko "Yhteenveto")
(defonce ^{:private true} raportti-sanktiot-otsikko "Sakot ja bonukset")
(defonce ^{:private true} raportti-kustannukset-otsikko "Muut kustannukset")
(defonce tyyppi-valinnat {:lisatyo "Lisätyö"
                          :muu "Muu kustannus"
                          :muutostyo "Muutostyö"
                          :arvonmuutos "Arvonmuutos"
                          :indeksi "Indeksitarkistus"
                          :sopimusalueen-muutos "Sopimusalueen muutos"
                          :yllapidon_sakko "Sakko"
                          :yllapidon_bonus "Bonus"})


(defn- osion-otsikko [otsikko]
  [:otsikko-heading otsikko {:padding-top "50px"}])


(defn- hae-lyhytnimet [db urakkatyyppi urakka-id]
  (let [urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
        lyhytnimet (urakat-q/hae-urakoiden-nimet db {:urakkatyyppi urakkatyyppi :vain-puuttuvat false :urakantila "kaikki"})
        ;; Tähän voi passata kokoelman urakka-iditä, jos halutaan suorittaa esim. hallintayksikkö kontekstissa 
        valitut-urakat-nimet (k-raportointi/suodata-urakat lyhytnimet #{urakka-id})]
    (k-raportointi/kokoa-lyhytnimet valitut-urakat-nimet)))


(defn- taulukko [{:keys [gridin-otsikko rivin-tiedot rivit oikealle-tasattavat]}]
  [:taulukko {:tyhja "Ei Tietoja."
              :otsikko gridin-otsikko
              :piilota-border? false
              :viimeinen-rivi-yhteenveto? true
              :oikealle-tasattavat-kentat (or oikealle-tasattavat #{})}
   rivin-tiedot rivit])


(defn- sanktiot-rivi [klo kohde laji perustelu maara]
  (rivi
    [:varillinen-teksti {:arvo klo}]
    [:varillinen-teksti {:arvo kohde}]
    [:varillinen-teksti {:arvo laji}]
    [:varillinen-teksti {:arvo perustelu}]
    [:varillinen-teksti {:arvo maara :fmt :raha}]))


(defn- koosta-sanktiot-taulukko [data rivien-maara yhteensa-hinta]
  (let [yhteenveto (sanktiot-rivi "Yhteensä" (str rivien-maara " kpl") "" "" yhteensa-hinta)
        tiedot {:rivin-tiedot (rivi
                                {:otsikko "Käsitelty" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti}
                                {:otsikko "Laji" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.3 :tyyppi :varillinen-teksti}
                                {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Selite" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                                {:otsikko "Määrä" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti})
                :rivit (conj
                         (mapv
                           #(sanktiot-rivi
                              (pvm/pvm-aika-klo (:aika %))
                              (:laji %)
                              (:kohde %)
                              (:perustelu %)
                              (:maara %))
                           data)
                         yhteenveto)
                
                :oikealle-tasattavat #{4}}]
    (into ()
      [(taulukko tiedot)
       (osion-otsikko raportti-sanktiot-otsikko)])))


(defn sakot-ja-bonukset
  "Tiemerkintä sanktiot ja bonukset, raportin suoritusfunktio"
  [db user {:keys [urakkatyyppi urakka-id alkupvm loppupvm] :as _parametrit}]
  (let [lyhytnimet (hae-lyhytnimet db urakkatyyppi urakka-id)
        raportin-otsikko (raportin-otsikko lyhytnimet raportti-sanktiot-otsikko alkupvm loppupvm)

        sankiot-ja-bonukset (laadunseuranta-palvelu/hae-urakan-sanktiot-ja-bonukset db user
                              {:alku alkupvm
                               :loppu loppupvm
                               :urakka-id urakka-id
                               :hae-sanktiot? true
                               :hae-bonukset? true
                               :vain-yllapitokohteettomat? false})

        rivien-maara (count sankiot-ja-bonukset)
        yhteensa-hinta (reduce + (map :summa sankiot-ja-bonukset))

        rivit (mapcat
                (fn [tapahtuma]
                  (let [sarakkeet {:aika (:kasittelyaika tapahtuma)
                                   :kohde (-> tapahtuma :yllapitokohde :nimi)
                                   :laji (-> tapahtuma :laji tyyppi-valinnat)
                                   :perustelu (or (-> tapahtuma :lisatieto) (-> tapahtuma :laatupoikkeama :paatos :perustelu))
                                   :maara (-> tapahtuma :summa)}]
                    [sarakkeet]))
                sankiot-ja-bonukset)]

    [:raportti {:nimi raportin-otsikko
                :orientaatio :landscape
                :lyhennetty-tiedostonimi true}
     (koosta-sanktiot-taulukko rivit rivien-maara yhteensa-hinta)]))


(defn- kustannukset-rivi [aika tyyppi selite luokka hinta]
  (rivi
    [:varillinen-teksti {:arvo aika}]
    [:varillinen-teksti {:arvo tyyppi}]
    [:varillinen-teksti {:arvo selite}]
    [:varillinen-teksti {:arvo luokka}]
    [:varillinen-teksti {:arvo hinta :fmt :raha}]))


(defn- koosta-kustannukset-taulukko [data rivien-maara yhteensa-hinta]
  (let [yhteenveto (kustannukset-rivi "Yhteensä" (str rivien-maara " kpl") "" "" yhteensa-hinta)
        tiedot {:rivin-tiedot (rivi
                                {:otsikko "Aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti}
                                {:otsikko "Tyyppi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.3 :tyyppi :varillinen-teksti}
                                {:otsikko "Selite" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "PK-luokka" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.5 :tyyppi :varillinen-teksti}
                                {:otsikko "Hinta" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti})
                :rivit (conj
                         (mapv
                           #(kustannukset-rivi
                              (pvm/pvm-aika-klo (:aika %))
                              (:tyyppi %)
                              (:selite %)
                              (:luokka %)
                              (:hinta %))
                           data)
                         yhteenveto)
                
                :oikealle-tasattavat #{4}}]
    (into ()
      [(taulukko tiedot)
       (osion-otsikko raportti-kustannukset-otsikko)])))


(defn muut-kustannukset
  "Tiemerkintä muut kustannukset raportin suoritusfunktio"
  [db user {:keys [urakkatyyppi urakka-id alkupvm loppupvm sopimus _tyypit] :as _parametrit}]
  (let [lyhytnimet (hae-lyhytnimet db urakkatyyppi urakka-id)
        raportin-otsikko (raportin-otsikko lyhytnimet raportti-kustannukset-otsikko alkupvm loppupvm)

        toteumat (yllapito-palvelu/hae-yllapito-toteumat
                   db user
                   {:urakka  urakka-id
                    :sopimus sopimus
                    :alkupvm  alkupvm
                    :loppupvm loppupvm})

        rivien-maara (count toteumat)
        yhteensa-hinta (reduce + (map :hinta toteumat))

        rivit (mapcat
                (fn [toteuma]
                  (let [sarakkeet {:aika (:pvm toteuma)
                                   :tyyppi (-> toteuma :tyyppi tyyppi-valinnat)
                                   :selite (-> toteuma :selite)
                                   :luokka (-> toteuma :yllapitoluokka :nimi)
                                   :hinta (-> toteuma :hinta)}]
                    [sarakkeet]))
                toteumat)]

    [:raportti {:nimi raportin-otsikko
                :orientaatio :landscape
                :lyhennetty-tiedostonimi true}
     (koosta-kustannukset-taulukko rivit rivien-maara yhteensa-hinta)]))


(defn- yhteenveto-rivi [tyyppi hinta]
  (rivi
    [:varillinen-teksti {:arvo tyyppi}]
    [:varillinen-teksti {:arvo hinta :fmt :raha}]))


(defn- koosta-yhteenveto-taulukko [data yhteensa-hinta]
  (let [yhteenveto (yhteenveto-rivi "Yhteensä" yhteensa-hinta)
        tiedot {:rivin-tiedot (rivi
                                {:otsikko "Kustannuslaji" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Hinta" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti})
                :rivit (conj
                         (mapv
                           #(yhteenveto-rivi
                              (:tyyppi %)
                              (:hinta %))
                           data)
                         yhteenveto)

                :oikealle-tasattavat #{1}}]
    (into ()
      [(taulukko tiedot)
       (osion-otsikko raportti-yhteenveto-otsikko)])))


(defn yhteenveto
  "Tiemerkintä yhteenveto raportin suoritusfunktio"
  [db _user {:keys [urakkatyyppi urakka-id alkupvm loppupvm _sopimus rivit] :as _parametrit}]
  (let [lyhytnimet (hae-lyhytnimet db urakkatyyppi urakka-id)
        raportin-otsikko (raportin-otsikko lyhytnimet raportti-yhteenveto-otsikko alkupvm loppupvm)
        yhteensa-hinta (reduce + (map :hinta rivit))
        raportti-rivit (mapcat
                         (fn [toteuma]
                           (let [sarakkeet {:tyyppi (-> toteuma :tyyppi tyyppi-valinnat)
                                            :hinta (-> toteuma :hinta)}]
                             [sarakkeet]))
                         rivit)]

    [:raportti {:nimi raportin-otsikko
                :orientaatio :landscape
                :lyhennetty-tiedostonimi true}
     (koosta-yhteenveto-taulukko raportti-rivit yhteensa-hinta)]))
