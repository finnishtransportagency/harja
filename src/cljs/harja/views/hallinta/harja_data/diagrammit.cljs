(ns harja.views.hallinta.harja-data.diagrammit
  ""
  (:require [reagent.core :refer [atom wrap] :as r]
            [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.harja-data.diagrammit :as tiedot]
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
  [e! yhteyskatkokset-jarjestys-data yhteyskatkokset-data-avain]
  (let [w (int (* 0.85 @dom/leveys))
        h (int (/ w 3))
        str-pred (fn [txt] (fn [arvo] (= txt arvo)))
        yhteyskatkosten-lkm-max (apply max (map #(apply max (map :value
                                                                 (:yhteyskatkokset %)))
                                                yhteyskatkokset-jarjestys-data))
        tikit (if yhteyskatkosten-lkm-max
                [0
                 (js/Math.round (* .25 yhteyskatkosten-lkm-max))
                 (js/Math.round (* .5 yhteyskatkosten-lkm-max))
                 (js/Math.round (* .75 yhteyskatkosten-lkm-max))
                 yhteyskatkosten-lkm-max]
                nil)
        legend (apply hash-set (mapcat #(map (fn [x] (:category x))
                                             (:yhteyskatkokset %))
                                       yhteyskatkokset-jarjestys-data))
        colors (zipmap (mapv keyword legend) (take (count legend) colors))]
    (if (empty? yhteyskatkokset-jarjestys-data)
      [:p "Annetuilla hakuasetuksilla ei löytynyt yhteyskatkoksia."]
      [vis/bars {:width w
                 :height (max 1000 h)
                 :value-fn :yhteyskatkokset
                 :label-fn :jarjestys-avain
                 :colors colors
                 :bar-padding 1
                 :format-amount str
                 :ticks tikit
                 :legend legend}
                ; TODO on-legend-click ei tällä hetkellä toimi.
                ;  :on-legend-click (fn [nakyma]
                ;                     (e! (tiedot/->PaivitaArvo (dissoc yhteyskatkokset-jarjestys-data nakyma) yhteyskatkokset-data-avain)))}
                yhteyskatkokset-jarjestys-data])))

(defn hae-kaikki-yhteyskatkosdatat
  [e!]
  (e! (tiedot/->HaeYhteyskatkosData :pvm :palvelut))
  (e! (tiedot/->HaeYhteyskatkosData :palvelut :pvm))
  (e! (tiedot/->HaeYhteyskatkosryhmaData :pvm :palvelut))
  (e! (tiedot/->HaeYhteyskatkosryhmaData :palvelut :pvm)))
(defn hakuasetukset-leijuke
  [e! hakuasetukset]
  (fn [e! hakuasetukset]
    [leijuke/leijuke
      {:otsikko "Hakuasetukset"
       :sulje! #(e! (tiedot/->PaivitaArvoFunktio not :hakuasetukset-nakyvilla?))}
      [lomake/lomake
        {:muokkaa! #(e! (tiedot/->PaivitaArvo % :hakuasetukset))
         :footer [napit/yleinen-ensisijainen "Päivitä" #(hae-kaikki-yhteyskatkosdatat e!)]}
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
        hakuasetukset]]))

(defn diagrammit-paanakyma [e! app]
  (fn [e! {:keys [analyysit valittu-analyysi yhteyskatkokset-pvm-data yhteyskatkokset-palvelut-data
                  valittu-yhteyskatkokset-jarjestys yhteyskatkokset-jarjestykset haku-kaynnissa valittu-yhteyskatkokset-arvo
                  yhteyskatkokset-arvot yhteyskatkosryhma-pvm-data yhteyskatkosryhma-palvelut-data
                  hakuasetukset-nakyvilla? hakuasetukset analyysi-tehty? analyysi]}]
    (if (empty? haku-kaynnissa)
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
              [y/livi-pudotusvalikko {:valinta valittu-yhteyskatkokset-jarjestys
                                      :format-fn #(if % % "Kaikki järjestykset")
                                      :valitse-fn #(e! (tiedot/->PaivitaArvo % :valittu-yhteyskatkokset-jarjestys))}
                                    (cons nil yhteyskatkokset-jarjestykset)]]
            ^{:key "arvo"}
            [:div.label-ja-alasveto
              [:span.alasvedon-otsikko "Arvo"]
              [y/livi-pudotusvalikko {:valinta valittu-yhteyskatkokset-arvo
                                      :format-fn #(identity %)
                                      :valitse-fn #(e! (tiedot/->PaivitaArvo % :valittu-yhteyskatkokset-arvo))}
                                    yhteyskatkokset-arvot]]
            ^{:key "hakuasetukset"}
            [:div.inline-block
              [:button.nappi-ensisijainen {:on-click #(e! (tiedot/->PaivitaArvoFunktio not :hakuasetukset-nakyvilla?))} "Hakuasetukset"]
              (when hakuasetukset-nakyvilla? [hakuasetukset-leijuke e! hakuasetukset])]))


        (when (or (nil? valittu-analyysi) (= valittu-analyysi "yhteyskatkokset"))
          (list
            ^{:key "line-plot"}
            ;[vis/plot {:plot-type :line-plot :x [1 2 3 4 5] :y [4 9 16 25 36] :width (int (* 0.85 @dom/leveys)) :height (int (/ (* 0.85 @dom/leveys) 3))}]
            (when (or (nil? valittu-yhteyskatkokset-jarjestys) (= valittu-yhteyskatkokset-jarjestys "pvm"))
              ^{:key "jarjestys-pvm-mukaan"}
              [:div
                [:h3 "Järjestys päivämäärän mukaan"]
                (case valittu-yhteyskatkokset-arvo
                  "katkokset" ^{:key "yhteyskatkokset-pvm"}[yhteyskatkokset-bars e! yhteyskatkokset-pvm-data :yhteyskatkokset-pvm-data]
                  "katkosryhmät" ^{:key "yhteyskatkosryhma-pvm"}[yhteyskatkokset-bars e! yhteyskatkosryhma-pvm-data :yhteyskatkosryhma-pvm-data])])
            (when (or (nil? valittu-yhteyskatkokset-jarjestys) (= valittu-yhteyskatkokset-jarjestys "palvelut"))
              ^{:key "jarjestys-palvelukutsujen-mukaan"}
              [:div
                [:h3 "Järjestys palvelukutsujen mukaan"]
                (case valittu-yhteyskatkokset-arvo
                  "katkokset" ^{:key "yhteyskatkokset-palvelut"}[yhteyskatkokset-bars e! yhteyskatkokset-palvelut-data :yhteyskatkokset-palvelut-data]
                  "katkosryhmät" ^{:key "yhteyskatkosryhma-palvelut"}[yhteyskatkokset-bars e! yhteyskatkosryhma-palvelut-data :yhteyskatkosryhma-palvelut-data])])))]]
      [y/ajax-loader])))

(defn diagrammit* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (when (empty? (:yhteyskatkokset-pvm-data app)) ;;FIXME tee tarkempi
                            (hae-kaikki-yhteyskatkosdatat e!)))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
        [debug/debug app]
        [diagrammit-paanakyma e! app]])))
(defn diagrammit []
  [tuck/tuck tiedot/app diagrammit*])
