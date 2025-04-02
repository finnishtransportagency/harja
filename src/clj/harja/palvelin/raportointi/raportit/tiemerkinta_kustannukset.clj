(ns harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset
  "Tiemerkinnän kustannuks raportit"
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.raportointi :as k-raportointi]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]))


(defonce ^{:private true} raportti-sanktiot-otsikko "Tiemerkintä - Sakot ja bonukset")


(defn- taulukko [{:keys [gridin-otsikko rivin-tiedot rivit oikealle-tasattavat]}]
  [:taulukko {:tyhja "Ei Tietoja."
              :otsikko gridin-otsikko
              :piilota-border? false
              :viimeinen-rivi-yhteenveto? false
              :oikealle-tasattavat-kentat (or oikealle-tasattavat #{})}
   rivin-tiedot rivit])


(defn- sanktiot-rivi [klo kohde laji perustelu maara]
  (rivi
    [:varillinen-teksti {:arvo klo}]
    [:varillinen-teksti {:arvo kohde}]
    [:varillinen-teksti {:arvo laji}]
    [:varillinen-teksti {:arvo perustelu}]
    [:varillinen-teksti {:arvo maara :fmt :raha}]))


(defn- osion-otsikko [otsikko]
  [:otsikko-heading otsikko {:padding-top "50px"}])


(defn- koosta-taulukko [data]
  (let [tiedot {:rivin-tiedot (rivi
                                {:otsikko "Käsitelty" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti}
                                {:otsikko "Laji" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.3 :tyyppi :varillinen-teksti}
                                {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Selite" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                                {:otsikko "Määrä" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.4 :tyyppi :varillinen-teksti})
                :rivit (mapv
                         #(sanktiot-rivi
                            (pvm/pvm-aika-klo (:aika %))
                            (:laji %)
                            (:kohde %)
                            (:perustelu %)
                            (:maara %))
                         data)}]
    (into ()
      [(taulukko tiedot)
       (osion-otsikko raportti-sanktiot-otsikko)])))


(defn sakot-ja-bonukset [db user {:keys [urakkatyyppi urakka-id alkupvm loppupvm] :as parametrit}]
  (let [;; Käytetään lyhytnimiä, jos olemassa
        urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
        lyhytnimet (urakat-q/hae-urakoiden-nimet db {:urakkatyyppi urakkatyyppi :vain-puuttuvat false :urakantila "kaikki"})
        ;; Tähän voi passata kokoelman urakka-iditä, jos halutaan suorittaa esim. hallintayksikkö kontekstissa 
        valitut-urakat-nimet (k-raportointi/suodata-urakat lyhytnimet #{urakka-id})
        urakoiden-nimet (k-raportointi/kokoa-lyhytnimet valitut-urakat-nimet)
        raportin-otsikko (raportin-otsikko urakoiden-nimet raportti-sanktiot-otsikko alkupvm loppupvm)

        sankiot-ja-bonukset (laadunseuranta-palvelu/hae-urakan-sanktiot-ja-bonukset db user
                              {:alku alkupvm
                               :loppu loppupvm
                               :urakka-id urakka-id
                               :hae-sanktiot? true
                               :hae-bonukset? true
                               :vain-yllapitokohteettomat? false})

        laji-fmt {:yllapidon_sakko "Sakko"
                  :yllapidon_bonus "Bonus"}

        rivit (mapcat
                (fn [tapahtuma]
                  (let [sarakkeet {:aika (:kasittelyaika tapahtuma)
                                   :kohde (-> tapahtuma :yllapitokohde :nimi)
                                   :laji (-> tapahtuma :laji laji-fmt)
                                   :perustelu (or (-> tapahtuma :lisatieto) (-> tapahtuma :laatupoikkeama :paatos :perustelu))
                                   :maara (-> tapahtuma :summa)}]
                    [sarakkeet]))
                sankiot-ja-bonukset)]

    [:raportti {:nimi raportin-otsikko
                :orientaatio :landscape
                :lyhennetty-tiedostonimi true}
     (koosta-taulukko rivit)]))


(defn muut-kustannukset [db user {:keys [urakkatyyppi parametrit]}]
  (let [{:keys [alkupvm loppupvm]} parametrit]

    [:raportti {:nimi "TODO....."
                :orientaatio :landscape
                :lyhennetty-tiedostonimi true}
     ;;(koosta-taulukko tapahtumarivit)
     ]))
