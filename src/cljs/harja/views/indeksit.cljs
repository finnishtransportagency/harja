(ns harja.views.indeksit
  "Indeksien hallinta."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.tiedot.indeksit :as i]
            [harja.ui.yleiset :as yleiset]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn tallenna-indeksi [nimi uudet-indeksivuodet]
  (go (let [tallennettavat
            (into []
                  (comp (filter #(not (:poistettu %))))
                  uudet-indeksivuodet)
            res (<! (i/tallenna-indeksi nimi tallennettavat))]
        (reset! i/indeksit res)
        true)))

(defn indeksi-grid [indeksin-nimi tarkkuus]
  (let [indeksit @i/indeksit
        rivit (reverse (sort-by :vuosi
                                (map #(assoc (second %) :kannassa? true)
                                     (filter (fn [[[nimi _] _]]
                                               (= nimi indeksin-nimi)
                                               ) indeksit))))
        varatut-vuodet (into #{} (map :vuosi rivit))
        formatter #(fmt/desimaaliluku-opt % tarkkuus)]
    [grid/grid
     {:otsikko      indeksin-nimi
      :tyhja        (if (nil? indeksit) [yleiset/ajax-loader "Indeksejä haetaan..."] "Ei indeksitietoja")
      :tallenna     #(tallenna-indeksi indeksin-nimi %)
      :tunniste     :vuosi
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
     rivit]))

(defn- hoidon-indeksit []
  [:span
   [:h3 "Hoidon indeksit"]
   [indeksi-grid "MAKU 2005" 1]
   [:hr]
   [indeksi-grid "MAKU 2010" 1]])

(defn- paallystyksen-indeksit []
  [:span
   [:h3 "Päällystyksen indeksit"]
   [indeksi-grid "Platts: FO 3,5%S CIF NWE Cargo" 2]
   [:hr]
   [indeksi-grid "Platts: Propane CIF NWE 7kt+" 2]
   [:hr]
   [indeksi-grid "Platts: ULSD 10ppmS CIF NWE Cargo" 2]])

(defn- tiemerkinnan-indeksit []
  [:span
   [:h3 "Tiemerkinnän indeksit"]
   [indeksi-grid "MAKU 2010" 1]
   [:hr]
   [indeksi-grid "Platts: FO 3,5%S CIF NWE Cargo" 2]
   [:hr]
   [indeksi-grid "Platts: Propane CIF NWE 7kt+" 2]
   [:hr]
   [indeksi-grid "Platts: ULSD 10ppmS CIF NWE Cargo" 2]])


(defn indeksit-elementti []
  (i/hae-indeksit)
  (let [ut (:arvo @yhteiset/valittu-urakkatyyppi)]
    [:span.indeksit

     [valinnat/urakkatyyppi
      yhteiset/valittu-urakkatyyppi
      nav/+urakkatyypit+
      #(reset! yhteiset/valittu-urakkatyyppi %)]

     (case ut
       (:hoito :tekniset-laitteet :siltakorjaus)
       [hoidon-indeksit]

       :tiemerkinta
       [tiemerkinnan-indeksit]

       (:paallystys :paikkaus)
       [paallystyksen-indeksit]

       ;; default jos ei yksikään natsaa
       [:div "Ei indeksejä ko. urakkatyypille. Ota yhteys pääkäyttäjään."])]))