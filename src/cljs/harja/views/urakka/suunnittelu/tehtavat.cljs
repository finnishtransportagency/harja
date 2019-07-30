(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalu :as tyokalu]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "col-xs-12 col-sm-4 col-md-4 col-lg-4"))

(defn luo-taulukon-tehtavat
  [e! tehtavat on-oikeus?]
  (let [rivit (map (fn [{:keys [id tehtavaryhmatyyppi maara nimi piillotettu? vanhempi]}]
                     (with-meta (jana/->Rivi id
                                             [(with-meta
                                                (case tehtavaryhmatyyppi
                                                  "ylataso" (osa/luo-tilallinen-laajenna (str id "-laajenna") nimi #(e! (t/->LaajennaSoluaKlikattu %1 %2)) {:class (sarakkeiden-leveys :tehtava)})
                                                  "valitaso" (osa/luo-tilallinen-laajenna (str id "-laajenna") nimi #(e! (t/->LaajennaSoluaKlikattu %1 %2)) {:class (str (sarakkeiden-leveys :tehtava)
                                                                                                                                                                         " solu-sisenna-1")})
                                                  "alitaso" (osa/->Teksti (str id "-tehtava") nimi {:class (str (sarakkeiden-leveys :tehtava)
                                                                                                                " solu-sisenna-2")}))
                                                {:sarake "Tehtävä"})
                                              (with-meta
                                                (osa/->Syote (str id "-maara")
                                                             {:on-change (fn [arvo]
                                                                           ;; Arvo tulee :positiivinen? kaytos-wrapperiltä, joten jos se on nil, ei syötetty arvo ollut positiivinen.
                                                                           (when arvo
                                                                             (e! (t/->PaivitaMaara id (str id "-maara") arvo))))}
                                                             {:on-change [:positiivinen-numero :eventin-arvo]}
                                                             {:class (sarakkeiden-leveys :maara)
                                                              :type "text"
                                                              :disabled (not on-oikeus?)
                                                              :value maara})
                                                {:sarake "Määrä"})]
                                             (if piillotettu?
                                               #{"piillotettu"}
                                               #{}))
                                {:vanhempi vanhempi
                                 :tehtavaryhmatyyppi tehtavaryhmatyyppi}))
                   tehtavat)
        otsikot [(jana/->Rivi :tehtavataulukon-otsikko
                              [(osa/->Otsikko "tehtava otsikko" "Tehtävä" #(e! (t/->JarjestaTehtavienMukaan)) {:class (sarakkeiden-leveys :tehtava)})
                               (osa/->Otsikko "maara otsikko" "Määrä" #(println "jarjesta määrät") {:class (sarakkeiden-leveys :maara)})]
                              nil)]]
    (into [] (concat otsikot rivit))))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/sisaan (fn [this]
                   (let [taulukon-tehtavat (luo-taulukon-tehtavat e! (get app :tehtava-ja-maaraluettelo) true)]
                     (e! (t/->MuutaTila [:tehtavat-taulukko] taulukon-tehtavat)))))
    (fn [e! app]
      (let [{taulukon-tehtavat :tehtavat-taulukko} app]
        [:div
         [debug/debug app]
         (if taulukon-tehtavat
           [taulukko/taulukko taulukon-tehtavat]
           [yleiset/ajax-loader])]))))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat-tila tehtavat*))