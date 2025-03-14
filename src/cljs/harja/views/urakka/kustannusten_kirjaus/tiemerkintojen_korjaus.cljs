(ns harja.views.urakka.kustannusten-kirjaus.tiemerkintojen-korjaus
  (:require
    [harja.math :as math]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.ui.komponentti :as komp]
    [harja.ui.grid :as grid]
    [harja.fmt :as fmt]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.ui.kentat :as kentat]
    [harja.tiedot.urakka.urakka :as tila]

    [harja.ui.debug :as debug]
    [reagent.core :as r]
    [tuck.core :refer [tuck]]
    [harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus :as tiedot]))

(defn prosenttiosuus-kustannuksesta
  [kustannus p-osuus]
  (* (/ p-osuus 100) kustannus))

(defn pk-osuus-komponentti [e! avain rivi app muokkaus-tila?]
  (println "rivi: " rivi)
  (println "app: " app)
  (let [osuus (get rivi avain)
        kustannus (:kustannus rivi)]
    (if @muokkaus-tila?
      [:div
       [kentat/tee-kentta {:tyyppi :positiivinen-numero
                           :nimi avain
                           :placeholder "0.0"

                           :on-blur (fn [uusi] (e! (tiedot/->MuokkaaOsuutta uusi rivi)))}

        (r/wrap osuus
          (fn [uusi]
            (e! (tiedot/->MuokkaaOsuutta uusi rivi))))]
       [:div (prosenttiosuus-kustannuksesta kustannus osuus)]]
      [:div osuus
       [:div  (prosenttiosuus-kustannuksesta kustannus osuus)]]
      )))

(def muokkaus-tila? (atom false))

(defn tiemerkintojen-korjaus* [e! app optiot]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/lippu tiedot/kustannusten-kirjaus-valilehti-nakyvissa?)
      (komp/sisaan #(e! (tiedot/->HaeKustannukset urakka)))
      (fn [e! {:keys [kustannukset haku-kaynnissa?] :as app}]
        [:div
         [:h1 "Tiemerkintöjen korjaus"]
         [debug/debug app]
         [grid/grid
          {:otsikko "Kustannukset vuosittain "
           :tyhja "Ei kustannuksia"
           :tunniste :kustannusvuosi
           :voi-lisata? false
           :voi-poistaa? (constantly false)
           :ennen-muokkausta #(swap! muokkaus-tila? (constantly true))
           :peruuta #(swap! muokkaus-tila? (constantly false))
           :rivi-validointi [{:fn (fn [rivi] (do (println "rivi-validointi! rivi: " rivi)
                                                   (if (> (:pk1 rivi) 5) "virhe 5")))
                                  :sarakkeet {:kustannus :kustannnus :pk1 :pk1 :pk2 :pk2 :pk3 :pk3}}]
           :muokkaa! (fn [rivi]
                       (println "prt rivi " rivi))
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
            ;;:muokattava? (constantly true)
            :leveys 3}
           {:otsikko "Kustannus (EUR)" :nimi :kustannus
            :tyyppi :positiivinen-numero :fmt fmt/euro-opt
            :leveys 3 :tasaa :oikea}
           #_{:otsikko "Pk 1-osuus" :nimi :pk1
              :tyyppi :positiivinen-numero :desimaalien-maara 1 :fmt fmt/prosentti
              ;; :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
              :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 1-osuus" :nimi :pk1
            :tyyppi :komponentti :komponentti #(pk-osuus-komponentti e! :pk1 % app muokkaus-tila?)
            ;;:muokattava? (constantly false)
            :validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 2-osuus" :nimi :pk2
            :tyyppi :komponentti :komponentti #(pk-osuus-komponentti e! :pk2 % app muokkaus-tila?)
            ;;:tyyppi :positiivinen-numero :desimaalien-maara 1 :fmt fmt/prosentti
            ;;:validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 3-osuus" :nimi :pk3
            :tyyppi :komponentti :komponentti #(pk-osuus-komponentti e! :pk3 % app muokkaus-tila?)
            ;;:tyyppi :positiivinen-numero :desimaalien-maara 1 :fmt fmt/prosentti
            ;;:validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
            :leveys 3 :tasaa :oikea}]
          kustannukset]]))))

(defn tiemerkintojen-korjaus []
  [tuck tila/tiemerkinta-kustannukset tiemerkintojen-korjaus*])
