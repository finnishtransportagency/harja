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

;; MVP: Tekee tallennuksia liian usein, jokaisen kentän onblurrissa, myös jos vaihtaa saman rivin kenttien välillä.
;; Lisäys/Vähennys ei toimi kunnolla, muuttuu aina lisäykseksi kun summaan kosketaan
(defn tavoitehinnan-oikaisut [e! app]
  (let [virheet (atom nil)
        fokusoitu-rivi (atom nil)]
    (fn [e! {:keys [tavoitehinnan-oikaisut-atom] :as app}]
      [:<>
       [debug/debug @tavoitehinnan-oikaisut-atom]
       [:div
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
                                  (e! (t/->PoistaOikaisu rivi muokkaa!)))
                               {:luokka "napiton-nappi"}])
          :uusi-rivi-nappi-luokka "nappi-ensisijainen"
          ;; TODO: Paranna onblur-toiminnallisuutta. Triggeröityy myös kun klikkaillaan rivin sisällä.
          :on-rivi-blur #(do
                           (println "focus out")
                           (e! (t/->TallennaOikaisu %1 %2)))
          :on-rivi-focus #(println "focus in")
          :virheet virheet}
         [{:otsikko "Luokka"
           :nimi ::valikatselmus/otsikko
           :tyyppi :valinta
           :valinnat valikatselmus/luokat
           :leveys 2}
          {:otsikko "Selite"
           :nimi ::valikatselmus/selite
           :tyyppi :string
           :leveys 4}
          {:otsikko "Lisäys / Vähennys"
           :nimi :lisays-tai-vahennys
           ;; TODO: Paranna toiminnallisuutta. Muuttuu lisäykseksi aina jos summaan koskee.
           :hae #(if (> 0 (::valikatselmus/summa %)) "Vähennys" "Lisäys")
           :tyyppi :valinta
           :valinnat ["Lisäys" "Vähennys"]
           :leveys 2}
          {:otsikko "Summa"
           :nimi ::valikatselmus/summa
           :tyyppi :numero
           :fmt #(if (neg? %) (str (- %)) (str %))
           :leveys 3}]
         tavoitehinnan-oikaisut-atom]]])))

(defn valikatselmus [e! app]
  (let [toteumat-yhteensa (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])]
    [:div.valikatselmus-container
     [napit/takaisin "Takaisin" #(e! (kustannusten-seuranta-tiedot/->SuljeValikatselmusLomake)) {:luokka "napiton-nappi tumma"}]
     [valikatselmus-otsikko-ja-tiedot app]
     [debug/debug app]
     [tavoitehinnan-oikaisut e! app]]))
