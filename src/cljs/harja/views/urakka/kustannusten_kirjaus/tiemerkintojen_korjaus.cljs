(ns harja.views.urakka.kustannusten-kirjaus.tiemerkintojen-korjaus
  (:require
    [harja.tiedot.urakka.urakka :as tila]
    [harja.ui.komponentti :as komp]
    [harja.ui.grid :as grid]
    [harja.fmt :as fmt]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.ui.debug :as debug]
    [tuck.core :refer [tuck]]
    [harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus :as tiedot]))

(defn tiemerkintojen-korjaus* [e! app optiot]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/lippu tiedot/kustannusten-kirjaus-valilehti-nakyvissa?)
      (komp/sisaan #(e! (tiedot/->HaeKustannukset urakka)))
      (fn [e! {:keys [kustannukset haku-kaynnissa?] :as app}]
        [:div
         [:h1 "Tiemerkintöjen korjaus"]
         [grid/grid
          {:otsikko "Kustannukset vuosittain "
           :tyhja "Ei kustannuksia"
           :tunniste :kustannusvuosi
           :voi-lisata? false
           :voi-poistaa? (constantly false)
           :rivi-jalkeen-fn (fn [kustannukset]
                              (let [kustannus-summa (tiedot/kustannusten-summa kustannukset)]
                                [{:teksti "Yhteensä" :luokka "lihavoitu"}
                                 {:teksti (fmt/euro-opt kustannus-summa) :luokka "lihavoitu" :tasaa :oikea}
                                 {:teksti ""}
                                 {:teksti ""}
                                 {:teksti ""}]))
           :tallenna #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaKustannukset % urakka)}
          [{:otsikko "Urakkavuosi" :nimi :kustannusvuosi
            :tyyppi :positiivinen-numero :kokonaisluku? true
            :muokattava? (constantly false)
            :leveys 3}
           {:otsikko "Kustannus (EUR)" :nimi :kustannus
            :tyyppi :positiivinen-numero :fmt fmt/euro-opt
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 1-osuus" :nimi :pk1
            :tyyppi :positiivinen-numero :desimaalien-maara 1 :fmt fmt/prosentti
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 2-osuus" :nimi :pk2
            :tyyppi :positiivinen-numero :desimaalien-maara 1 :fmt fmt/prosentti
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 3-osuus" :nimi :pk3
            :tyyppi :positiivinen-numero :desimaalien-maara 1 :fmt fmt/prosentti
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}]
          kustannukset]]))))

(defn tiemerkintojen-korjaus []
  [tuck tila/tiemerkinta-kustannukset tiemerkintojen-korjaus*])
