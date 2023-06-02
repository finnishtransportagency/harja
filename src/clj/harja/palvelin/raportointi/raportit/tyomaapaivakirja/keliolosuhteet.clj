(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.keliolosuhteet
  "Työmaapäiväkirja -näkymän poikkeukselliset keliolosuhteet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- keliolosuhteet-rivi [klo paikka havainto]
  (rivi
    [:varillinen-teksti {:arvo klo}]
    [:varillinen-teksti {:arvo paikka}]
    [:varillinen-teksti {:arvo havainto}]))

(defn poikkeukselliset-keliolosuhteet-taulukko [poikkeussaat]
  (let [tiedot {:gridin-otsikko "Omat havainnot"
                :rivin-tiedot (rivi
                                {:otsikko "Klo" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.155 :tyyppi :varillinen-teksti}
                                {:otsikko "Paikka" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.33 :tyyppi :varillinen-teksti}
                                {:otsikko "Havainto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti})
                :rivit (mapv
                         #(keliolosuhteet-rivi
                            (harja.pvm/aika (:havaintoaika %))
                            (:paikka %)
                            (:kuvaus %))
                         poikkeussaat)}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/osion-otsikko "Poikkeukselliset paikalliset keliolosuhteet")])))
