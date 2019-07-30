(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "col-xs-12 col-sm-4 col-md-4 col-lg-4"))

(defn luo-taulukon-tehtavat
  [e! tehtavat]
  (let [rivit (map (fn [{:keys [id tehtavaryhmatyyppi maara nimi piillotettu? vanhempi]}]
                     (with-meta (jana/->Rivi id
                                             [(case tehtavaryhmatyyppi
                                                "ylataso" (osa/luo-tilallinen-laajenna (str id "-laajenna") nimi #(e! (t/->LaajennaSoluaKlikattu %1 %2)) {:class (sarakkeiden-leveys :tehtava)})
                                                "valitaso" (osa/luo-tilallinen-laajenna (str id "-laajenna") nimi #(e! (t/->LaajennaSoluaKlikattu %1 %2)) {:class (str (sarakkeiden-leveys :tehtava)
                                                                                                                                                                       " solu-sisenna-1")})
                                                "alitaso" (osa/->Teksti (str id "-tehtava") nimi {:class (str (sarakkeiden-leveys :tehtava)
                                                                                                              " solu-sisenna-2")}))
                                              (osa/->Teksti (str id "-maara") maara {:class (sarakkeiden-leveys :maara)})]
                                             (if piillotettu?
                                               #{"piillotettu"}
                                               #{}))
                                {:vanhempi vanhempi}))
                   tehtavat)
        otsikot [(jana/->Rivi :tehtavataulukon-otsikko
                              [(osa/->Otsikko "tehtava otsikko" "Tehtava" #(println "jarjesta tehtavat") {:class (sarakkeiden-leveys :tehtava)})
                               (osa/->Otsikko "maara otsikko" "Maara" #(println "jarjesta määrät") {:class (sarakkeiden-leveys :maara)})]
                              nil)]]
    (concat otsikot rivit)))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/sisaan (fn [this]
                   (let [taulukon-tehtavat (luo-taulukon-tehtavat e! (get app :tehtava-ja-maaraluettelo))]
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