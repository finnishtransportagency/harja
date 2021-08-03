(ns harja.views.urakka.kulut.valikatselmus
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.tiedot.urakka.kulut.valikatselmus :as t]
            [harja.views.urakka.kulut.yhteiset :as yhteiset]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]))

(def debug-atom (atom {}))

(defn valikatselmus-otsikko-ja-tiedot [app]
  (let [urakan-nimi (:nimi @nav/valittu-urakka)
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))
        hoitokausi-str (pvm/paivamaaran-hoitokausi-str (pvm/hoitokauden-alkupvm urakan-alkuvuosi))]
    [:<>
     [:h1 "Välikatselmuksen päätökset"]
     [:div.caption urakan-nimi]
     [:div.caption (str (- valittu-hoitokauden-alkuvuosi urakan-alkuvuosi) ". hoitovuosi (" hoitokausi-str ")")]]))

(defn tavoitehinnan-oikaisut [e! app]
  (let [virheet (atom nil)
        tallennettu-tila (atom @(:tavoitehinnan-oikaisut-atom app))]
    (fn [e! {:keys [tavoitehinnan-oikaisut-atom] :as app}]
      [:div.oikaisut-ja-paatokset
       [debug/debug @tavoitehinnan-oikaisut-atom]
       [grid/muokkaus-grid
        {:otsikko "Tavoitehinnan oikaisut"
         :voi-kumota? false
         :voi-lisata? false
         :custom-toiminto {:teksti "Lisää Oikaisu"
                           :toiminto #(e! (t/->LisaaOikaisu))
                           :opts {:ikoni (ikonit/livicon-plus)
                                  :luokka "nappi-ensisijainen"}}
         :toimintonappi-fn (fn [rivi muokkaa!]
                             [napit/poista ""
                              #(do
                                 (e! (t/->PoistaOikaisu rivi muokkaa!))
                                 (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom))
                              {:luokka "napiton-nappi"}])
         :uusi-rivi-nappi-luokka "nappi-ensisijainen"
         :on-rivi-blur (fn [oikaisu i]
                         (do
                           (when-not (and (= @tallennettu-tila @tavoitehinnan-oikaisut-atom))
                             (let [vanha (get @tallennettu-tila i)
                                   uusi (get @tavoitehinnan-oikaisut-atom i)
                                   ;; Jos lisays-tai-vahennys-saraketta on muutettu (mutta summaa ei), käännetään summan merkkisyys
                                   oikaisu (if (and (not= (:lisays-tai-vahennys vanha)
                                                          (:lisays-tai-vahennys uusi))
                                                    (= (::valikatselmus/summa vanha)
                                                       (::valikatselmus/summa uusi)))
                                             (update oikaisu ::valikatselmus/summa -)
                                             oikaisu)]
                               (swap! tavoitehinnan-oikaisut-atom #(assoc % i oikaisu))
                               (e! (t/->TallennaOikaisu oikaisu i))
                               (reset! tallennettu-tila @tavoitehinnan-oikaisut-atom)))))
         :virheet virheet}
        [{:otsikko "Luokka"
          :nimi ::valikatselmus/otsikko
          :tyyppi :valinta
          :valinnat valikatselmus/luokat
          :leveys 2}
         {:otsikko "Selite"
          :nimi ::valikatselmus/selite
          :tyyppi :string
          :leveys 3}
         {:otsikko "Lisäys / Vähennys"
          :nimi :lisays-tai-vahennys
          :hae #(if (> 0 (::valikatselmus/summa %)) "Vähennys" "Lisäys")
          :tyyppi :valinta
          :valinnat ["Lisäys" "Vähennys"]
          :leveys 2}
         {:otsikko "Summa"
          :nimi ::valikatselmus/summa
          :tyyppi :numero
          :tasaa :oikea
          :leveys 2}]
        tavoitehinnan-oikaisut-atom]])))

(defn valikatselmus [e! app]
  [:div.valikatselmus-container
   [napit/takaisin "Takaisin" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
   [valikatselmus-otsikko-ja-tiedot app]
   [debug/debug app]
   [:div.valikatselmus-ja-yhteenveto
    [tavoitehinnan-oikaisut e! app]
    [:div.yhteenveto-container
     [yhteiset/yhteenveto-laatikko e! app (:kustannukset app) :valikatselmus]]]])
