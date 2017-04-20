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
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.fmt :as fmt]
            [harja.domain.yllapitokohde :as yllapitokohde-domain])
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
      {:valinta @laatupoikkeamat/listaus
       :valitse-fn #(reset! laatupoikkeamat/listaus %)
       :format-fn #(case %
                     :kaikki "Kaikki"
                     :kasitellyt "Käsitellyt (päätös tehty)"
                     :selvitys "Odottaa urakoitsijan selvitystä"
                     :omat "Minun kirjaamat / kommentoimat"
                     :poikkeamaraportilliset "Poikkeamaraportilliset")}
      [:kaikki :selvitys :kasitellyt :omat :poikkeamaraportilliset]]

     [urakka-valinnat/aikavali]

     (let [oikeus? @laatupoikkeamat/voi-kirjata?]
       (yleiset/wrap-if
         (not oikeus?)
         [yleiset/tooltip {} :%
          (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                          oikeudet/urakat-laadunseuranta-laatupoikkeamat)]
         [napit/uusi "Uusi laatupoikkeama"
          #(reset! laatupoikkeamat/valittu-laatupoikkeama-id :uusi)
          {:disabled (not oikeus?)}]))

     [grid/grid
      {:otsikko "Laatu\u00ADpoikkeamat" :rivi-klikattu #(reset! laatupoikkeamat/valittu-laatupoikkeama-id (:id %))
       :tyhja (if (nil? poikkeamat)
                [yleiset/ajax-loader "Laatupoikkeamia ladataan"]
                "Ei laatupoikkeamia")}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}
       (if (or (= :paallystys (:nakyma optiot))
               (= :paikkaus (:nakyma optiot))
               (= :tiemerkinta (:nakyma optiot)))
         {:otsikko "Yllä\u00ADpito\u00ADkoh\u00ADde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                               :nimi (get-in rivi [:yllapitokohde :nimi])}))}
         {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 1})
       {:otsikko "TR-osoite"
        :nimi :tr
        :leveys 2
        :fmt tierekisteri/tierekisteriosoite-tekstina}
       {:otsikko "Kuvaus" :nimi :kuvaus :leveys 3}
       {:otsikko "Tekijä" :nimi :tekija :leveys 1 :fmt laatupoikkeamat/kuvaile-tekija}
       {:otsikko "Päätös" :nimi :paatos :fmt laatupoikkeamat/kuvaile-paatos :leveys 2}
       {:otsikko "Poikkeamaraportti" :nimi :sisaltaa-poikkeamaraportin? :fmt fmt/totuus :tasaa :keskita :leveys 1}]
      poikkeamat]]))

(defn paatos?
  "Onko annetussa laatupoikkeamassa päätös?"
  [laatupoikkeama]
  (not (nil? (get-in laatupoikkeama [:paatos :paatos]))))

(defn- vastaava-laatupoikkeama [lp]
  (some (fn [haettu-lp] (when (= (:id haettu-lp) (:id lp)) haettu-lp)) @laatupoikkeamat/urakan-laatupoikkeamat))

(defn laatupoikkeamat [optiot]
  (komp/luo
    (komp/lippu lp-kartalla/karttataso-laatupoikkeamat)
    (komp/kuuntelija :laatupoikkeama-klikattu #(reset! laatupoikkeamat/valittu-laatupoikkeama-id (:id %2)))
    (komp/ulos (kartta-tiedot/kuuntele-valittua! laatupoikkeamat/valittu-laatupoikkeama))
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:laatupoikkeama {:toiminto (fn [lp]
                                                         (reset! laatupoikkeamat/valittu-laatupoikkeama-id
                                                                 (:id (vastaava-laatupoikkeama lp))))
                                             :teksti "Valitse laatupoikkeama"}})
                         (nav/vaihda-kartan-koko! :M))
                      #(do (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))
    (fn [optiot]
      [:span.laatupoikkeamat
       [kartta/kartan-paikka]
       (if @laatupoikkeamat/valittu-laatupoikkeama
         [laatupoikkeamalomake laatupoikkeamat/valittu-laatupoikkeama
          (merge optiot
                 {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle})]
         [laatupoikkeamalistaus optiot])])))
