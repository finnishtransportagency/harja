(ns harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat
  (:require [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.sopimus :as s]
            [harja.domain.kanavat.kohde :as k]
            [harja.domain.kanavat.lt-alus :as a]
            [harja.domain.kanavat.lt-toiminto :as t]
            [harja.domain.kayttaja :as kayttaja]

            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]
            [harja.kyselyt.urakat :as urakat-q]))


(defn- taulukko [{:keys [gridin-otsikko rivin-tiedot rivit oikealle-tasattavat]}]
  [:taulukko {:otsikko gridin-otsikko
              :oikealle-tasattavat-kentat (or oikealle-tasattavat #{})
              :tyhja "Ei Tietoja."
              :piilota-border? false
              :viimeinen-rivi-yhteenveto? false}
   rivin-tiedot rivit])

(defn- osion-otsikko [otsikko]
  [:otsikko-heading otsikko {:padding-top "50px"}])

(defn- liikennetapahtuma-rivi [klo kohde tyyppi avaus
                               palvelumuoto suunta alus aluslaji
                               aluksia matkustajia nippuja ylavesi
                               alavesi lisatiedot kuittaaja]
  (rivi
    [:varillinen-teksti {:arvo klo}]
    [:varillinen-teksti {:arvo kohde}]
    [:varillinen-teksti {:arvo tyyppi}]
    [:varillinen-teksti {:arvo avaus}]
    [:varillinen-teksti {:arvo palvelumuoto}]
    [:varillinen-teksti {:arvo suunta}]
    [:varillinen-teksti {:arvo alus}]
    [:varillinen-teksti {:arvo aluslaji}]
    [:varillinen-teksti {:arvo aluksia}]
    [:varillinen-teksti {:arvo matkustajia}]
    [:varillinen-teksti {:arvo nippuja}]
    [:varillinen-teksti {:arvo ylavesi}]
    [:varillinen-teksti {:arvo alavesi}]
    [:varillinen-teksti {:arvo lisatiedot}]
    [:varillinen-teksti {:arvo kuittaaja}]))


(defn- koosta-liikennetapahtuma-taulukko [data]

  (let [tiedot {:rivin-tiedot (rivi
                                {:otsikko "Aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Tyyppi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Sillan avaus" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Palvelumuoto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                                {:otsikko "Suunta" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Alus" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Aluslaji" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Aluksia" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Matkustajia" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Nippuja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Ylävesi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Alavesi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Lisätiedot" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Kuittaaja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti})
                :rivit (mapv
                         #(liikennetapahtuma-rivi
                            (pvm/aika (:aika %))
                            (:kohde %)
                            (:tyyppi %)
                            (:avaus %)
                            (:palvelumuoto %)
                            (:suunta %)
                            (:alus %)
                            (:aluslaji %)
                            (:aluksia %)
                            (:matkustajia %)
                            (:nippuja %)
                            (:ylavesi %)
                            (:alavesi %)
                            (:lisatiedot %)
                            (:kuittaaja %))
                         data)}]
    (into ()
      [(taulukko tiedot)
       (osion-otsikko "Liikennetapahtumat")])))

(defn suorita [db user {:keys [urakoiden-nimet sarakkeet rivit parametrit hakuparametrit yhteenveto]
                        :as kaikki-parametrit}]

  (let [{:keys [alkupvm loppupvm valitut-urakat]} parametrit
        raportin-nimi "Liikennetapahtumat"
        ;; Urakoiden-nimet on nil kun 1 urakka valittuna, haetaan tällöin valitun urakan nimi toisesta muuttujasta
        urakoiden-nimet (or urakoiden-nimet (first valitut-urakat))
        raportin-otsikko (raportin-otsikko urakoiden-nimet raportin-nimi alkupvm loppupvm)
        liikennetapahtumat (q/hae-liikennetapahtumat db user hakuparametrit)
        ;_ (println "\n Kutsutaan liikenneraportti, tapahtumat: " liikennetapahtumat " \n -----------")

        test (first liikennetapahtumat)

        _ (dorun (map (fn [a]
                        (println "\n a: " a))test))
        _ (println "\n test: " test " \n ")]
    
    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     ; klo kohde tyyppi avaus palvelumuoto suunta alus aluslaji  aluksia matkustajia nippuja ylavesi alavesi lisatiedot kuittaaja
     (koosta-liikennetapahtuma-taulukko [{:aika #inst "2024-01-11T07:40:39.000-00:00",
                                          :kohde "Kauhava",
                                          :tyyppi ""
                                          :avaus "Ei avausta"
                                          :kuvaus "Kuvaus tapahtumasta"
                                          :palvelumuoto "Kaukopalvelu"
                                          :suunta "Ylös"
                                          :alus "Alus testi"
                                          :aluslaji "HVV"
                                          :aluksia "52"
                                          :matkustajia "11"
                                          :nippuja "24"
                                          :ylavesi "24.24"
                                          :alavesi "1024.24"
                                          :lisatiedot "The contents of fo:block line 1 exceed the available area in the inline-progression direction by 545 millipoints"
                                          :kuittaaja "Mika Korhonen"}])

     [:liikenneyhteenveto yhteenveto]]))
