(ns harja.palvelin.raportointi.kanavien-hairio-raportti-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(deftest kanavat-hairiotilanteet-raportti-toimii
  (let [urakka-id (hae-urakan-id-nimella "Saimaan kanava")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi       :kanavien-hairiotilanteet
                   :konteksti  "urakka"
                   :urakka-id  urakka-id
                   ;; Tämä generoidaan normaalisti suoraan frontin gridin kautta joten parametrit passataan hieman erillä lailla verrattuna normaaliin, mutta tämä ei testaamista estä 
                   :parametrit {:parametrit {:hallintayksikko 1
                                             :urakka {:id urakka-id}
                                             :aikavali [(c/to-date (t/local-date 2023 10 1)) (c/to-date (t/local-date 2024 9 30))]
                                             :urakkatyyppi :vesivayla-kanavien-hoito}
                                ;; Manuaalisesti generoidut gridin rivit 
                                :rivit [(list "19.2.2024 6:38" "Kansola" "Liikennevaurio" "Test" 23 2 nil nil nil nil nil 48 "Valmis" "Ei")
                                        (list "19.2.2024 6:13" "Pälli" "Liikennevaurio" "Test" 4 3 5 6 2 nil 0 "Valmis" "Ei")
                                        (list "19.2.2024 4:07" "Soskua" "Sähkötekninen vika" "Jotain meni vikaan" 60 1 2 1 2 "Vika korjattiin" 92 "Valmis" "Kyllä")
                                        (list "19.2.2024 4:03" "Soskua" "Konetekninen vika" "Syy ei tiedossa" nil nil nil nil nil nil 0 "Kesken" "Ei")
                                        (list "19.2.2024 3:48" "Pälli" "Sähkötekninen vika" "Edellinen korjaus tehtiin huonosti, korjattu nyt uudestaan." 70 5 6 5 6 "Vika korjattiin" 20 "Valmis" "Kyllä")]}})
        raportin-nimi (-> vastaus second :nimi)]

    ;; Nimeksi tulee aina urakka, raportti ja passattu aikaväli
    (is (= raportin-nimi "Saimaan kanava, Häiriötilanteet ajalta 01.10.2023 - 30.09.2024"))
    ;; Raportilla pitäisi näkyä passatut tiedot 
    (is (= vastaus
           [:raportti
            {:nimi "Saimaan kanava, Häiriötilanteet ajalta 01.10.2023 - 30.09.2024", :piilota-otsikko? true}
            [:taulukko {:otsikko "Häiriötilanteet", :tyhja nil, :sheet-nimi "Häiriötilanteet"}
             [{:otsikko "Havainto", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "vaalen-tumma-tausta", :leveys 0.75, :tyyppi :varillinen-teksti}
              {:otsikko "Kohde", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}
              {:otsikko "Luokka", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.9, :tyyppi :varillinen-teksti}
              {:otsikko "Syy", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.9, :tyyppi :varillinen-teksti}
              {:otsikko "Vesi odotus (h)", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.5, :tyyppi :varillinen-teksti}
              {:otsikko "Ammatti", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
              {:otsikko "Huvi", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.4, :tyyppi :varillinen-teksti}
              {:otsikko "Tie odotus (h)", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.5, :tyyppi :varillinen-teksti}
              {:otsikko "Ajoneuvot", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.7, :tyyppi :varillinen-teksti}
              {:otsikko "Toimenpide", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 1, :tyyppi :varillinen-teksti}
              {:otsikko "Korjaus aika", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
              {:otsikko "Tila", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.6, :tyyppi :varillinen-teksti}
              {:otsikko "Paikallinen", :otsikkorivi-luokka "nakyma-otsikko", :sarakkeen-luokka "nakyma-valkoinen-solu", :leveys 0.8, :tyyppi :varillinen-teksti}]
             [(list "19.2.2024 6:38" "Kansola" "Liikennevaurio" "Test" 23 2 nil nil nil nil nil 48 "Valmis" "Ei")
              (list "19.2.2024 6:13" "Pälli" "Liikennevaurio" "Test" 4 3 5 6 2 nil 0 "Valmis" "Ei")
              (list "19.2.2024 4:07" "Soskua" "Sähkötekninen vika" "Jotain meni vikaan" 60 1 2 1 2 "Vika korjattiin" 92 "Valmis" "Kyllä")
              (list "19.2.2024 4:03" "Soskua" "Konetekninen vika" "Syy ei tiedossa" nil nil nil nil nil nil 0 "Kesken" "Ei")
              (list "19.2.2024 3:48" "Pälli" "Sähkötekninen vika" "Edellinen korjaus tehtiin huonosti, korjattu nyt uudestaan." 70 5 6 5 6 "Vika korjattiin" 20 "Valmis" "Kyllä")]]]))))
