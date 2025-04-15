(ns harja.views.urakka.kustannusten-kirjaus.tiemerkintojen-korjaus
  (:require
   [harja.tiedot.urakka.urakka :as tila]
   [harja.ui.komponentti :as komp]
   [harja.ui.grid :as grid]
   [harja.fmt :as fmt]
   [harja.tyokalut.tuck :as tuck-apurit]
   [tuck.core :refer [tuck]]
   [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
   [harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus :as tiedot]))

(defn tiemerkintojen-korjaus* [e!]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/lippu tiedot/kustannusten-kirjaus-valilehti-nakyvissa?)
      (komp/sisaan #(e! (tiedot/->HaeKustannukset urakka)))
      (fn [e! {:keys [kustannukset haku-kaynnissa?] :as _app}]
        [:div.livi-grid.tiemerkinta-kustannusten-kirjaus
         [:h1 "Tiemerkintöjen korjaus"]
         [grid/grid
          {:otsikko "Kustannukset vuosittain (sis. indeksimuutokset)"
           :tyhja (if haku-kaynnissa?
                    [ajax-loader-pieni "Haku käynnissä..."]
                    "Aikavälille ei löytynyt tuloksia.")
           :tunniste :kustannusvuosi
           :voi-lisata? false
           :piilota-toiminnot? true
           :voi-poistaa? (constantly false)
           :rivi-jalkeen-fn (fn [kustannukset]
                              (let [kustannus-summa (tiedot/kustannusten-summa kustannukset :kustannus)
                                    pk1-summa (tiedot/pk-osuus-totaalista kustannukset :pk1)
                                    pk2-summa (tiedot/pk-osuus-totaalista kustannukset :pk2)
                                    pk3-summa (tiedot/pk-osuus-totaalista kustannukset :pk3)]
                                [{:teksti "Yhteensä" :luokka "yhteensa"}
                                 {:teksti (fmt/euro-opt kustannus-summa) :luokka "yhteensa" :tasaa :oikea}
                                 {:teksti "" :luokka "yhteensa"}
                                 {:teksti (fmt/euro-opt pk1-summa) :luokka "yhteensa"}
                                 {:teksti "" :luokka "yhteensa"}
                                 {:teksti (fmt/euro-opt pk2-summa) :luokka "yhteensa"}
                                 {:teksti "" :luokka "yhteensa"}
                                 {:teksti (fmt/euro-opt pk3-summa) :luokka "yhteensa"}]))
           :tallenna #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaKustannukset % urakka)}
          [{:otsikko "Urakkavuosi" :nimi :kustannusvuosi :muokattava? (constantly false)
            :tyyppi :positiivinen-numero :kokonaisluku? true
            :leveys 3}
           {:otsikko "Kustannus (EUR)" :nimi :kustannus
            :tyyppi :euro
            :leveys 3 :tasaa :oikea}

           {:otsikko "Pk 1-%" :nimi :pk1
            :tyyppi :positiivinen-numero :fmt fmt/prosentti
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 1-osuus" :nimi :pk1-p-osuus :muokattava? (constantly false)
            :tyyppi :positiivinen-numero :desimaalien-maara 2 :fmt fmt/euro-opt
            :hae (fn [kohde] (tiedot/prosenttiosuus-kustannuksesta (:kustannus kohde) (:pk1 kohde)))
            :leveys 3 :tasaa :vasen}

           {:otsikko "Pk 2-%" :nimi :pk2
            :tyyppi :positiivinen-numero :fmt fmt/prosentti
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 2-osuus" :nimi :pk2-p-osuus :muokattava? (constantly false)
            :tyyppi :positiivinen-numero :desimaalien-maara 2 :fmt fmt/euro-opt
            :hae (fn [kohde] (tiedot/prosenttiosuus-kustannuksesta (:kustannus kohde) (:pk2 kohde)))
            :leveys 3 :tasaa :vasen}

           {:otsikko "Pk 3-%" :nimi :pk3
            :tyyppi :positiivinen-numero :fmt fmt/prosentti
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 3-osuus" :nimi :pk3-p-osuus :muokattava? (constantly false)
            :tyyppi :positiivinen-numero :desimaalien-maara 2 :fmt fmt/euro-opt
            :hae (fn [kohde] (tiedot/prosenttiosuus-kustannuksesta (:kustannus kohde) (:pk3 kohde)))
            :leveys 3 :tasaa :vasen}]
          kustannukset]]))))

(defn tiemerkintojen-korjaus []
  [tuck tila/tiemerkinta-korjaukset tiemerkintojen-korjaus*])
