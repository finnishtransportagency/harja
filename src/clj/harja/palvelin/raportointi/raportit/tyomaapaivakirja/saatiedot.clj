(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.saatiedot
  "Työmaapäiväkirja -näkymän säätietojen gridit"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- saatiedot-rivi [aikavali ilma tie s-olom k-tuuli s-sum]
  (rivi
    [:varillinen-teksti {:arvo aikavali}]
    [:varillinen-teksti {:arvo ilma}]
    [:varillinen-teksti {:arvo tie}]
    [:varillinen-teksti {:arvo s-olom}]
    [:varillinen-teksti {:arvo k-tuuli}]
    [:varillinen-teksti {:arvo s-sum}]))

(defn saatiedot-taulukkojen-parametrit []
  (let [taulukon-optiot {:tyhja "Ei Tietoja."
                         :piilota-border? false
                         :viimeinen-rivi-yhteenveto? false}
        taulukon-otsikot (rivi
                           {:otsikko "Klo" :leveys 0.85 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :tyyppi :varillinen-teksti}
                           {:otsikko "Ilma" :leveys 1 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "Tie" :leveys 1 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "S-olom" :leveys 1 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "K-tuuli" :leveys 1 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "S-Sum" :leveys 1 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti})
        rivit (into []
                [(saatiedot-rivi "0:00" "1.2 C°" "0.7 C°" "<icon>" "7 m/s" "4 mm")
                 (saatiedot-rivi "1:00" "1.2 C°" "0.7 C°" "<icon>" "7 m/s" "4 mm")
                 (saatiedot-rivi "2:00" "1.2 C°" "0.7 C°" "<icon>" "7 m/s" "4 mm")
                 (saatiedot-rivi "3:00" "1.2 C°" "0.7 C°" "<icon>" "7 m/s" "4 mm")
                 (saatiedot-rivi "4:00" "1.2 C°" "0.7 C°" "<icon>" "7 m/s" "4 mm")])

        taulukko-1 {:otsikko-vasen "Tie 4, Oulu, Ritaharju"
                    :optiot-vasen taulukon-optiot
                    :otsikot-vasen taulukon-otsikot
                    :rivit-vasen rivit}
        taulukko-2 {:otsikko-oikea "Tie 20, Oulu, Hönttämäki"
                    :optiot-oikea taulukon-optiot
                    :otsikot-oikea taulukon-otsikot
                    :rivit-oikea rivit}]
    [taulukko-1 taulukko-2]))

(defn saatietojen-taulukot []
  (into ()
    [[:gridit-vastakkain (first (saatiedot-taulukkojen-parametrit)) (second (saatiedot-taulukkojen-parametrit))]
     (yhteiset/osion-otsikko "Sääasemien tiedot")]))
