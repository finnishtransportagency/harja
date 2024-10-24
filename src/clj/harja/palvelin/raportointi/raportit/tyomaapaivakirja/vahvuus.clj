(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahvuus
  "Työmaapäiväkirja -näkymän urakan henkilöstö gridit"
  (:require
   [harja.pvm :as pvm]
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- vahvuus-rivi [aikavali nimi]
  (rivi
    [:varillinen-teksti {:arvo aikavali}]
    [:varillinen-teksti {:arvo nimi}]))

(defn vahvuus-taulukkojen-parametrit [paivystajat tyonjohtajat]
  (let [taulukon-optiot {:tyhja "Ei Tietoja."
                         :piilota-border? false
                         :viimeinen-rivi-yhteenveto? false}
        taulukon-otsikot (rivi
                           {:otsikko "Aikaväli"
                            :otsikkorivi-luokka "nakyma-otsikko"
                            :sarakkeen-luokka "vaalen-tumma-tausta"
                            :leveys 0.27
                            :tyyppi :varillinen-teksti}
                           {:otsikko "Nimi"
                            :otsikkorivi-luokka "nakyma-otsikko"
                            :sarakkeen-luokka "nakyma-valkoinen-solu"
                            :leveys 1
                            :tyyppi :varillinen-teksti})
        fn-formatoi-paivystajat (fn [data]
                                  (into []
                                    (mapv #(vahvuus-rivi (if (:lopetus %) ;; Aloitus on aina, mutta lopetus on vapaaehtoinen kenttä
                                                           (pvm/kellonaikavali (:aloitus %) (:lopetus %))
                                                           (pvm/aika (:aloitus %))) (:nimi %)) data)))
        ;; Vasemman taulukon rivit (Päivystäjät)
        rivit-v (fn-formatoi-paivystajat paivystajat)
        ;; Työnjohtajat
        rivit-o (fn-formatoi-paivystajat tyonjohtajat)
        taulukko-1 {:otsikko-vasen "Päivystäjät" :optiot-vasen taulukon-optiot :otsikot-vasen taulukon-otsikot :rivit-vasen rivit-v}
        taulukko-2 {:otsikko-oikea "Työnjohtajat" :optiot-oikea taulukon-optiot :otsikot-oikea taulukon-otsikot :rivit-oikea rivit-o}]
    [taulukko-1 taulukko-2]))

(defn vahvuus-taulukot [paivystajat tyonjohtajat]
  (let [taulukot (vahvuus-taulukkojen-parametrit paivystajat tyonjohtajat)]
    (into ()
      [[:gridit-vastakkain (first taulukot) (second taulukot)]
       (yhteiset/osion-otsikko "Vahvuus")])))
