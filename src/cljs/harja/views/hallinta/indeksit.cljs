(ns harja.views.hallinta.indeksit
  "Indeksien hallinta."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.tiedot.hallinta.indeksit :as i]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn indeksi-grid [{:keys [indeksin-nimi platts? koodi]}]

  (let [indeksit @i/indeksit
        rivit (reverse (sort-by :vuosi
                                (map #(assoc (second %) :kannassa? true)
                                     (filter (fn [[[nimi _] _]]
                                               (= nimi indeksin-nimi)
                                               ) indeksit))))
        varatut-vuodet (into #{} (map :vuosi rivit))
        tarkkuus (if platts? 2 1)
        formatter #(fmt/desimaaliluku-opt % tarkkuus)
        vihje (when platts? "Plattsin indeksit syötettävä muodossa euroa / tonni.")]
    [:span.indeksi-grid
     [grid/grid
      {:otsikko (str indeksin-nimi (when koodi (str " (koodi: " koodi ")")))
       :tyhja (if (nil? indeksit) [yleiset/ajax-loader "Indeksejä haetaan..."] "Ei indeksitietoja")
       :tallenna #(i/tallenna-indeksi indeksin-nimi %)
       :tunniste :vuosi
       :piilota-toiminnot? true
       :voi-poistaa? #(not (:kannassa? %))}
      [{:otsikko "Vuosi" :nimi :vuosi :tyyppi :valinta :leveys "17%"
        :valinta-arvo identity
        :valinta-nayta #(if (nil? %) "- valitse -" %)

        :valinnat (vec (filter #(not (varatut-vuodet %)) (range 2009 (inc (t/year (pvm/nyt))))))

        :validoi [[:ei-tyhja "Anna indeksin vuosi"] [:uniikki "Sama vuosi vain kerran per indeksi."]]
        :muokattava? #(not (:kannassa? %))}

       {:otsikko "tammi" :nimi 1 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "helmi" :nimi 2 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "maalis" :nimi 3 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "huhti" :nimi 4 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "touko" :nimi 5 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "kesä" :nimi 6 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "heinä" :nimi 7 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "elo" :nimi 8 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "syys" :nimi 9 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "loka" :nimi 10 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "marras" :nimi 11 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}
       {:otsikko "joulu" :nimi 12 :tyyppi :positiivinen-numero :desimaalien-maara tarkkuus :fmt formatter :leveys "7%"}]
      rivit]
     (when vihje
       [yleiset/vihje vihje])]))

  (defn- indeksitaulukot [urakkatyyppi indeksit]
    (if-not (empty? indeksit)
      [:span
       [:h3 "Indeksit urakkatyypille " (:nimi urakkatyyppi)]
       (for [i indeksit]
         (let [platts? (str/includes? (:indeksinimi i) "Platts")]
           ^{:key (:id i)}
           [:span
            [indeksi-grid {:indeksin-nimi (:indeksinimi i)
                           :koodi (:koodi i) :platts? platts?}]
            [:hr]]))]
      [:div "Ei indeksejä valitulle urakkatyypille"]))

(defn indeksit-elementti []
  (i/hae-indeksit)
  (let [urakkatyyppi @yhteiset/valittu-urakkatyyppi
        urakkatyyppien-indeksit @i/urakkatyypin-indeksit
        indeksit (filter #(= (:arvo urakkatyyppi) (:urakkatyyppi %)) urakkatyyppien-indeksit)]
    [:span.indeksit
     [valinnat/urakkatyyppi
      yhteiset/valittu-urakkatyyppi
      nav/+urakkatyypit+
      #(reset! yhteiset/valittu-urakkatyyppi %)]

     [indeksitaulukot urakkatyyppi indeksit]]))