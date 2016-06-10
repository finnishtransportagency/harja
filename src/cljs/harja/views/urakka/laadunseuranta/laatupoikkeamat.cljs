(ns harja.views.urakka.laadunseuranta.laatupoikkeamat
  "Listaa urakan laatupoikkeamat."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat-kartalla :as lp-kartalla]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [harja.domain.laadunseuranta :refer [validi-laatupoikkeama?]]
            [harja.views.urakka.laadunseuranta.laatupoikkeama :refer [laatupoikkeamalomake]]
            [cljs.core.async :refer [<!]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn laatupoikkeamalistaus
  "Listaa urakan laatupoikkeamat"
  [optiot]
  (let [poikkeamat (when @laatupoikkeamat/urakan-laatupoikkeamat
                     (reverse (sort-by :aika @laatupoikkeamat/urakan-laatupoikkeamat)))]
    [:div.laatupoikkeamat
     [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
     [yleiset/pudotusvalikko
      "Näytä laatupoikkeamat"
      {:valinta    @laatupoikkeamat/listaus
       :valitse-fn #(reset! laatupoikkeamat/listaus %)
       :format-fn  #(case %
                     :kaikki "Kaikki"
                     :kasitellyt "Käsitellyt (päätös tehty)"
                     :selvitys "Odottaa urakoitsijan selvitystä"
                     :omat "Minun kirjaamat / kommentoimat")}
      [:kaikki :selvitys :kasitellyt :omat]]

     [urakka-valinnat/aikavali]

     (when @laatupoikkeamat/voi-kirjata?
       [napit/uusi "Uusi laatupoikkeama" #(reset! laatupoikkeamat/valittu-laatupoikkeama-id :uusi)])

     [grid/grid
      {:otsikko "Laatu\u00ADpoikkeamat" :rivi-klikattu #(reset! laatupoikkeamat/valittu-laatupoikkeama-id (:id %))
       :tyhja (if (nil? poikkeamat)
                [yleiset/ajax-loader "Laatupoikkeamia ladataan"]
                "Ei laatupoikkeamia")}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}
       (if (or (= :paallystys (:nakyma optiot))
               (= :paikkaus (:nakyma optiot))
               (= :tiemerkinta (:nakyma optiot)))
         {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 1
          :hae (fn [rivi]
                 (tierekisteri/yllapitokohde-tekstina rivi {:osoite rivi
                                                            :nayta-teksti-tie? false
                                                            :nayta-teksti-ei-tr-osoitetta? false}))}
         {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 1})
       {:otsikko "TR-osoite"
        :nimi :tr
        :leveys 2
        :fmt tierekisteri/tierekisteriosoite-tekstina}
       {:otsikko "Kuvaus" :nimi :kuvaus :leveys 3}
       {:otsikko "Tekijä" :nimi :tekija :leveys 1 :fmt laatupoikkeamat/kuvaile-tekija}
       {:otsikko "Päätös" :nimi :paatos :fmt laatupoikkeamat/kuvaile-paatos :leveys 2}] ;; Päätös
      poikkeamat]]))

(defn paatos?
  "Onko annetussa laatupoikkeamassa päätös?"
  [laatupoikkeama]
  (not (nil? (get-in laatupoikkeama [:paatos :paatos]))))

(defn laatupoikkeamat [optiot]
  (komp/luo
    (komp/lippu lp-kartalla/karttataso-laatupoikkeamat)
    (komp/kuuntelija :laatupoikkeama-klikattu #(reset! laatupoikkeamat/valittu-laatupoikkeama-id (:id %2)))
    (komp/ulos (kartta/kuuntele-valittua! laatupoikkeamat/valittu-laatupoikkeama))
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn [optiot]
     [:span.laatupoikkeamat
      [kartta/kartan-paikka]
      (if @laatupoikkeamat/valittu-laatupoikkeama
        [laatupoikkeamalomake laatupoikkeamat/valittu-laatupoikkeama
         (merge optiot
                {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle})]
        [laatupoikkeamalistaus optiot])])))
