(ns harja.views.hallinta.harja-data
  ""
  (:require [reagent.core :refer [atom wrap] :as r]
            [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.harja-data :as tiedot]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.visualisointi :as vis]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as y]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.lomake :as lomake]
            [harja.ui.dom :as dom]
            [harja.ui.debug :as debug]))

(def colors (vec (repeatedly 60
                            (fn []
                              (apply str "#" (repeatedly 6 #(rand-int 10)))))))

(defn yhteyskatkokset-bars
  [e! yhteyskatkos-jarjestys-data yhteyskatkos-data-avain]
  (let [w (int (* 0.85 @dom/leveys))
        h (int (/ w 3))
        str-pred (fn [txt] (fn [arvo] (= txt arvo)))
        lisaa-yhteyskatkoksiin (fn [olemassa-olevat ryhma-vec arvo-vec]
                                (let [ryhma-arvo-pari (mapv #(hash-map :category %1 :value %2) ryhma-vec arvo-vec)
                                      lisays-olemassa-oleviin (vec (for [oo olemassa-olevat
                                                                         rap ryhma-arvo-pari
                                                                         :when (= (:category oo) (:category rap))]
                                                                      {:category (:category oo) :value (+ (:value oo) (:value rap))}))
                                      lisays-olemassa-oleviin (mapv #(if-let [paivitetty (some (fn [paivitetty-mappi]
                                                                                                  (if (= (:category paivitetty-mappi) (:category %))
                                                                                                    paivitetty-mappi false))
                                                                                               lisays-olemassa-oleviin)]
                                                                        paivitetty %)
                                                                    olemassa-olevat)
                                      lisattavat (keep #(if (some (fn [{:keys [category value]}]
                                                                    (= (:category %) category))
                                                                  lisays-olemassa-oleviin)
                                                          nil %)
                                                        ryhma-arvo-pari)]
                                    (vec (concat lisays-olemassa-oleviin lisattavat))))

        data (reduce (fn [jarjestetty {:keys [jarjestys-avain ryhma-avain arvo-avain]}]
                       (let [mapin-arvo (if-let [loytynyt-mappi (some #(if (= jarjestys-avain (:jarjestys-avain %)) % false) jarjestetty)]
                                          {:jarjestys-avain jarjestys-avain
                                           :yhteyskatkokset (lisaa-yhteyskatkoksiin (:yhteyskatkokset loytynyt-mappi) ryhma-avain arvo-avain)}
                                          {:jarjestys-avain jarjestys-avain
                                           :yhteyskatkokset (mapv #(hash-map :category %1 :value %2) ryhma-avain arvo-avain)})
                              loytyy-jarjestyksesta? (some #(= (:jarjestys-avain %) (:jarjestys-avain mapin-arvo)) jarjestetty)]
                          (if loytyy-jarjestyksesta?
                            (mapv #(if (= (:jarjestys-avain %) (:jarjestys-avain mapin-arvo))
                                      mapin-arvo %)
                                  jarjestetty)
                            (conj jarjestetty mapin-arvo))))

                    [] yhteyskatkos-jarjestys-data)
        yhteyskatkosten-lkm-max (apply max (map #(apply max (map :value (:yhteyskatkokset %))) data))
        tikit (if yhteyskatkosten-lkm-max
                [0
                 (js/Math.round (* .25 yhteyskatkosten-lkm-max))
                 (js/Math.round (* .5 yhteyskatkosten-lkm-max))
                 (js/Math.round (* .75 yhteyskatkosten-lkm-max))
                 yhteyskatkosten-lkm-max]
                nil)
        legend (apply hash-set (mapcat #(map (fn [x] (:category x)) (:yhteyskatkokset %)) data))
        colors (zipmap (mapv keyword legend) (take (count legend) colors))]
    [vis/bars {:width w
               :height (max 1000 h)
               :value-fn :yhteyskatkokset
               :label-fn :jarjestys-avain
               :colors colors
               :bar-padding 1
               :format-amount str
               :ticks tikit
               :legend legend}
              ;  :on-legend-click (fn [nakyma]
              ;                     (e! (tiedot/->PaivitaArvo (dissoc yhteyskatkos-jarjestys-data nakyma) yhteyskatkos-data-avain)))}
              data]))

(defn hakuasetukset-leijuke
  [e! hakuasetukset]
  (let [setti-ryhmia (atom #{:tallenna :hae :muut})]
    (fn [e! hakuasetukset]
      [leijuke/leijuke
        {:otsikko "Hakuasetukset"
         :sulje! #(e! (tiedot/->PaivitaArvoFunktio not :hakuasetukset-nakyvilla?))}
        [lomake/lomake
          {:muokkaa! #(e! (tiedot/->PaivitaArvo % :hakuasetukset))
           :footer [napit/yleinen-ensisijainen "Päivitä" #(e! (tiedot/->KaytaAsetuksia))]}
          [{:otsikko "Min katkokset"
            :nimi :min-katkokset
            :tyyppi :positiivinen-numero
            ::lomake/col-luokka ""}
           {:otsikko "Naytettavat ryhmät"
            :nimi :naytettavat-ryhmat
            :tyyppi :checkbox-group
            :vaihtoehdot [:hae :tallenna :urakka :muut]
            :nayta-rivina? true
            ::lomake/col-luokka ""}]
          hakuasetukset]])))

(defn harja-datan-paanakyma [e! app]
  (e! (tiedot/->HaeArvotEnsin))
  (fn [e! {:keys [analyysit valittu-analyysi yhteyskatkos-pvm-data yhteyskatkos-palvelut-data
                  valittu-yhteyskatkos-jarjestys yhteyskatkos-jarjestykset aloitus-valmis valittu-yhteyskatkos-arvo
                  yhteyskatkos-arvot yhteyskatkosryhma-pvm-data yhteyskatkosryhma-palvelut-data
                  hakuasetukset-nakyvilla? hakuasetukset]}]
    (if aloitus-valmis
      [:span
       [:div.container
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Data"]
         [y/livi-pudotusvalikko {:valinta valittu-analyysi
                                 :format-fn #(if % % "Kaikki järjestelmät")
                                 :valitse-fn #(e! (tiedot/->PaivitaArvo % :valittu-analyysi))}
                                (cons nil analyysit)]]
        (when (= valittu-analyysi "yhteyskatkokset")
          (list
            ^{:key "jarjestys"}
            [:div.label-ja-alasveto
              [:span.alasvedon-otsikko "Jäjestys"]
              [y/livi-pudotusvalikko {:valinta valittu-yhteyskatkos-jarjestys
                                      :format-fn #(if % % "Kaikki järjestykset")
                                      :valitse-fn #(e! (tiedot/->PaivitaArvo % :valittu-yhteyskatkos-jarjestys))}
                                    (cons nil yhteyskatkos-jarjestykset)]]
            ^{:key "arvo"}
            [:div.label-ja-alasveto
              [:span.alasvedon-otsikko "Arvo"]
              [y/livi-pudotusvalikko {:valinta valittu-yhteyskatkos-arvo
                                      :format-fn #(identity %)
                                      :valitse-fn #(e! (tiedot/->PaivitaArvo % :valittu-yhteyskatkos-arvo))}
                                    yhteyskatkos-arvot]]
            ^{:key "hakuasetukset"}
            [:div.inline-block
              [:button.nappi-ensisijainen {:on-click #(e! (tiedot/->PaivitaArvoFunktio not :hakuasetukset-nakyvilla?))} "Hakuasetukset"]
              (when hakuasetukset-nakyvilla? [hakuasetukset-leijuke e! hakuasetukset])]))


        (when (or (nil? valittu-analyysi) (= valittu-analyysi "yhteyskatkokset"))
          (list
            (when (or (nil? valittu-yhteyskatkos-jarjestys) (= valittu-yhteyskatkos-jarjestys "pvm"))
              (case valittu-yhteyskatkos-arvo
                "katkokset" ^{:key "yhteyskatkos-pvm"}[yhteyskatkokset-bars e! yhteyskatkos-pvm-data :yhteyskatkos-pvm-data]
                "katkosryhmät" ^{:key "yhteyskatkosryhma-pvm"}[yhteyskatkokset-bars e! yhteyskatkosryhma-pvm-data :yhteyskatkosryhma-pvm-data]))
            (when (or (nil? valittu-yhteyskatkos-jarjestys) (= valittu-yhteyskatkos-jarjestys "palvelut"))
              (case valittu-yhteyskatkos-arvo
                "katkokset" ^{:key "yhteyskatkos-palvelut"}[yhteyskatkokset-bars e! yhteyskatkos-palvelut-data :yhteyskatkos-palvelut-data]
                "katkosryhmät" ^{:key "yhteyskatkosryhma-palvelut"}[yhteyskatkokset-bars e! yhteyskatkosryhma-palvelut-data :yhteyskatkosryhma-palvelut-data]))))]]

      [y/ajax-loader])))

(defn harja-data* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
        [debug/debug app]
        [harja-datan-paanakyma e! app]])))
(defn harja-data []
  [tuck/tuck tiedot/app harja-data*])
