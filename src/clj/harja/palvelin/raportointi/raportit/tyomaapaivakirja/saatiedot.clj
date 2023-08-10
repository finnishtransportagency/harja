(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.saatiedot
  "Työmaapäiväkirja -näkymän säätietojen gridit"
  (:require
    [harja.pvm :as pvm]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- saatiedot-rivi [aika aika-tarkka ilma tie s-olom k-tuuli s-sum-str maara]
  (rivi
    [:varillinen-teksti {:arvo aika}]
    [:varillinen-teksti {:arvo ilma}]
    [:varillinen-teksti {:arvo tie}]
    [:saa-ikoni {:olomuoto s-olom :havaintoaika aika-tarkka :maara maara}]
    [:varillinen-teksti {:arvo s-sum-str}]
    [:varillinen-teksti {:arvo k-tuuli}]))

(defn saatiedot-taulukkojen-parametrit [vasen-aseman-tiedot oikea-aseman-tiedot]
  (let [vasen-rivit (second vasen-aseman-tiedot)
        oikea-rivit (second oikea-aseman-tiedot)
        taulukon-optiot {:tyhja "Ei Tietoja."
                         :piilota-border? false
                         :viimeinen-rivi-yhteenveto? false}
        taulukon-otsikot (rivi
                           {:otsikko "Klo" :leveys 1 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :tyyppi :varillinen-teksti}
                           {:otsikko "Ilma" :leveys 0.75 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "Tie" :leveys 0.75 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "S-olom" :leveys 0.75 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "S-Sum" :leveys 0.75 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                           {:otsikko "K-tuuli" :leveys 0.75 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti})
        rivit-vasen (into []
                      (mapv
                        #(saatiedot-rivi
                           (pvm/aika (:havaintoaika %))
                           (pvm/pvm-aika-klo (:havaintoaika %))
                           (str (:ilman_lampotila %) " C°")
                           (str (:tien_lampotila %) " C°")
                           (int (:sateen_olomuoto %))
                           (str (:keskituuli %) " m/s")
                           (str (:sadesumma %) " mm")
                           (int (:sadesumma %)))
                        vasen-rivit))
        rivit-oikea (into []
                      (mapv
                        #(saatiedot-rivi
                           (pvm/aika (:havaintoaika %))
                           (pvm/pvm-aika-klo (:havaintoaika %))
                           (str (:ilman_lampotila %) " C°")
                           (str (:tien_lampotila %) " C°")
                           (int (:sateen_olomuoto %))
                           (str (:keskituuli %) " m/s")
                           (str (:sadesumma %) " mm")
                           (int (:sadesumma %)))
                        oikea-rivit))
        taulukko-vasen {:otsikko-vasen (:aseman_tunniste (first (second vasen-aseman-tiedot)))
                        :optiot-vasen taulukon-optiot
                        :otsikot-vasen taulukon-otsikot
                        :rivit-vasen rivit-vasen}
        taulukko-oikea (when oikea-aseman-tiedot
                         {:otsikko-oikea (:aseman_tunniste (first (second oikea-aseman-tiedot)))
                          :optiot-oikea taulukon-optiot
                          :otsikot-oikea taulukon-otsikot
                          :rivit-oikea rivit-oikea})]
    [taulukko-vasen taulukko-oikea]))

(defn saatietojen-taulukot [saa-asemat]
  (let [paritioidut-saa-asemat (if (>= (count saa-asemat) 2)
                                 (partition 2 saa-asemat)
                                 (partition 1 saa-asemat))
        asematietotaulukot (mapv
                             (fn [rivi]
                               (saatiedot-taulukkojen-parametrit (first rivi) (second rivi)))
                             paritioidut-saa-asemat)
        piirrettavat-taulukot (reduce (fn [piirrettava taulukot]
                                        (conj
                                          piirrettava
                                          [:gridit-vastakkain (first taulukot) (second taulukot)]))
                                [] asematietotaulukot)]
    (into ()
      (conj
        piirrettavat-taulukot
        (yhteiset/osion-otsikko "Sääasemien tiedot")))))
