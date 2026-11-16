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
            [clojure.string :as str]
            [harja.ui.yleiset :as y])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- viimeksi-muokattu-teksti [arvo]
  (cond
    (pvm/pvm? arvo) (pvm/pvm-aika-klo arvo)
    (instance? js/Date arvo) (pvm/js-date->pvm arvo)
    (string? arvo) arvo
    :else (str arvo)))

(defn indeksi-grid [{:keys [indeksinimi koodi]} urakkatyyppi]

  (let [indeksit @i/indeksit
        viimeksi-muokattu (get indeksit [indeksinimi :viimeksi-muokattu])
        tallennus-kaynnissa? (contains? @i/indeksin-tallennus-kaynnissa indeksinimi)
        rivit (reverse (sort-by :vuosi
                                (map #(assoc (second %) :kannassa? true)
                                     (filter (fn [[[nimi vuosi] _]]
                                               (and (= nimi indeksinimi)
                                                    (not= vuosi :viimeksi-muokattu)))
                                             indeksit))))
        varatut-vuodet (into #{} (map :vuosi rivit))
        ;; Ylläpidossa käytettävien Platssin indeksien tarkkuus on 2 ja arvot ovat euroa per tonni
        platts? (str/includes? indeksinimi "Platts")
        tarkkuus (if platts? 2 1)
        formatter #(fmt/desimaaliluku-opt % tarkkuus)
        vihje (when platts? "Plattsin indeksit syötettävä muodossa euroa / tonni.")
        hoito-tyyppi (= :hoito (:arvo urakkatyyppi))]
    [y/himmennys {:himmenna?            tallennus-kaynnissa?
                  :himmennyksen-sisalto (when tallennus-kaynnissa?
                                          [y/ajax-loader])}
     [:span.indeksi-grid
      [:div.grid-status-viestit.indeksi-grid-status
       (if viimeksi-muokattu
         [:div.status-viesti.tallennettu
          (str "Viimeksi tallennettu: " (viimeksi-muokattu-teksti viimeksi-muokattu))]
         [:div.status-viesti.ei-muutoksia
          "Ei tallennettuja muutoksia"])]
      [grid/grid
       {:otsikko            (str indeksinimi (when hoito-tyyppi ", teiden ylläpidon osaindeksi") (when koodi (str " (koodi: " koodi ")")))
        :tyhja              (if (nil? indeksit) [yleiset/ajax-loader "Indeksejä haetaan..."] "Ei indeksitietoja")
        :tallenna           #(i/tallenna-indeksi indeksinimi %)
        :tunniste           :vuosi
        :piilota-toiminnot? true
        :voi-poistaa?       #(not (:kannassa? %))}
       [{:otsikko       "Vuosi" :nimi :vuosi :tyyppi :valinta :leveys "17%"
         :valinta-arvo  identity
         :valinta-nayta #(if (nil? %) "- valitse -" %)

         :valinnat      (vec (filter #(not (varatut-vuodet %)) (range 2009 (inc (t/year (pvm/nyt))))))

         :validoi       [[:ei-tyhja "Anna indeksin vuosi"] [:uniikki "Sama vuosi vain kerran per indeksi."]]
         :muokattava?   #(not (:kannassa? %))}

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
        [yleiset/vihje vihje])]]))

  (defn- indeksitaulukot [urakkatyyppi indeksit]
    (if-not (empty? indeksit)
      [:span
       [:h3 "Indeksit urakkatyypille " (:nimi urakkatyyppi)]
       (for [i indeksit]
         ^{:key (:id i)}
         [:span
          [indeksi-grid i urakkatyyppi]
          [:hr]])]
      [:div "Ei indeksejä valitulle urakkatyypille"]))

(defn indeksit-elementti []
  (i/hae-indeksit)
  (let [urakkatyyppi @yhteiset/valittu-urakkatyyppi
        indeksit (i/urakkatyypin-indeksit (:arvo urakkatyyppi))
        urakkatyypit (keep #(if (not= :vesivayla (:arvo %))
                              %
                              {:nimi "Vesiväylät" :arvo :vesivayla-hoito})
                           (conj nav/+urakkatyypit+
                                 {:nimi "Kanavat", :arvo :vesivayla-kanavien-hoito}))] ; Kanavaurakoissa on käytössä eri indeksi kuin vesiväylissä. Tarvitaan oma hallinnointiosuus.
    [:span.indeksit
     [valinnat/urakkatyyppi
      yhteiset/valittu-urakkatyyppi
      urakkatyypit
      #(reset! yhteiset/valittu-urakkatyyppi %)]

     [indeksitaulukot urakkatyyppi indeksit]]))
