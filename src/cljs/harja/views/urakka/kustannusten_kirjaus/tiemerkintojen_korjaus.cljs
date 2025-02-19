(ns harja.views.urakka.kustannusten-kirjaus.tiemerkintojen-korjaus
  (:require
    [harja.ui.komponentti :as komp]
    [harja.ui.grid :as grid]
    [harja.fmt :as fmt]
    [harja.pvm :as pvm]
    [cljs.core.async :refer [<!]]
    [harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus :as tiedot])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn reverse-range [start end]
  (range start (dec end) -1))

(defn tiemerkintojen-korjaus [urakka tiemerkintojen-kustannukset]
  (println "urakka asd: " (:id urakka))
  (let [urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakka))
        alusta-tyhjat (mapv (fn [n m] {:id m :urakka (:id urakka) :kustannusvuosi n :kustannus 0.00 :pk1 0 :pk2 0 :pk3 0})
                        (range urakan-alkuvuosi urakan-loppuvuosi) (reverse-range -1 (- urakan-alkuvuosi urakan-loppuvuosi)))
        filteroi-vuodet (mapv #(:kustannusvuosi %) tiemerkintojen-kustannukset)
        filteroidut-tyhjat-rivit (filter #(not (contains? (set filteroi-vuodet) (:kustannusvuosi %))) alusta-tyhjat)
        paivitetyt-rivit (concat filteroidut-tyhjat-rivit tiemerkintojen-kustannukset)]
    (komp/luo
      (komp/lippu tiedot/kustannusten-kirjaus-valilehti-nakyvissa?)
      (fn []
        [:div
         [:h1 "Tiemerkintöjen korjaus"]
         [grid/grid
          {:otsikko "Kustannukset vuosittain "
           :tyhja "Ei kustannuksia."
           :voi-lisata? false
           :voi-poistaa? (constantly false)
           :rivi-jalkeen-fn (fn [urakat]
                              [{:teksti "Yhteensä" :luokka "lihavoitu"}
                               {:teksti "kustannukset" :luokka "lihavoitu" :tasaa :oikea}
                               {:teksti "prosentti1" :luokka "lihavoitu" :tasaa :oikea}
                               {:teksti "prosentti2" :luokka "lihavoitu" :tasaa :oikea}
                               {:teksti "prosentti3" :luokka "lihavoitu" :tasaa :oikea}])
           ;;TODO Tarkista oikeus muokata
           :tallenna #(go (let [vastaus (<! (tiedot/tallenna-tiemerkinnan-kustannukset! (:id urakka) %))]))}
          [{:otsikko "Urakkavuosi" :nimi :kustannusvuosi
            :tyyppi :positiivinen-numero :kokonaisluku? true
            :muokattava? (constantly false)
            :leveys 3}
           {:otsikko "Kustannus (EUR)" :nimi :kustannus
            :tyyppi :positiivinen-numero :fmt fmt/euro-opt
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 1-osuus" :nimi :pk1
            :tyyppi :positiivinen-numero
            ;;:validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]])
            :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 2-osuus" :nimi :pk2
            :tyyppi :positiivinen-numero
            ;;:validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
              :leveys 3 :tasaa :oikea}
           {:otsikko "Pk 3-osuus" :nimi :pk3
            :tyyppi :positiivinen-numero
              ;;:validoi [[:validoi-summa-on-100 [:pk1 :pk2 :pk3] "Pk-osuus prosenttien yhteenlasketun summan on oltava 100"]]
              :leveys 3 :tasaa :oikea}]
          paivitetyt-rivit]]))))
