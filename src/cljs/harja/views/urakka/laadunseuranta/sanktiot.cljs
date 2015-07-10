(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom] :as r]

            [harja.views.urakka.valinnat :as urakka-valinnat]

            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.sanktiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [harja.atom :refer [reaction<!]]))

(defn sanktion-tiedot
  []
  (let [muokattu (atom @tiedot/valittu-sanktio)
        voi-tallentaa? (atom false)]
    [:div
     [:button.nappi-ensisijainen
      {:on-click #(reset! tiedot/valittu-sanktio nil)}
      "Palaa"]

     [lomake/lomake
      {:luokka :horizontal
       :muokkaa! #(reset! muokattu %)
       :footer [napit/palvelinkutsu-nappi
                "Tallenna sanktio"
                #(tiedot/tallenna-sanktio @muokattu)
                {:luokka       "nappi-ensisijainen"
                 :ikoni        (ikonit/envelope)
                 :kun-onnistuu #(do
                                 (tiedot/sanktion-tallennus-onnistui % @muokattu)
                                 (reset! tiedot/valittu-sanktio nil))
                 :disabled     (not @voi-tallentaa?)}]}
      [{:otsikko "Perintäpäivämäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm}
       {:otsikko "Havainnon aika" :nimi :havaintoaika
        :hae (comp :aika :havainto)
        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm}
       {:otsikko "Käsittelyn aika" :nimi :kasittelyaika :hae (comp :kasittelyaika :paatos :havainto)
        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm}
       {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :havainto) :leveys 1 :tyyppi :string}
       {:otsikko "Kuvaus" :nimi :kuvaus :hae (comp :kuvaus :havainto) :leveys 3 :tyyppi :string}
       {:otsikko "Tekijätyyppi" :nimi :tekija :hae (comp :tekija :havainto) :leveys 1 :tyyppi :string}
       {:otsikko "Tekijä" :nimi :tekijanimi :hae (comp :tekijanimi :havainto) :leveys 1 :tyyppi :string}
       {:otsikko "Käsittelytapa" :nimi :kasittelytapa :hae (comp :kasittelytapa :paatos :havainto)
        :leveys 2 :tyyppi :string}
       {:otsikko "Summa" :nimi :maara :hae (comp :summa) :leveys 2 :tyyppi :string}
       {:otsikko "Indeksi" :nimi :indeksi :leveys 2 :tyyppi :string}
       {:otsikko "Suora sanktio?" :nimi :suorasanktio :leveys 2 :tyyppi :string}
       {:otsikko "Indeksi" :nimi :indeksi :leveys 2 :tyyppi :string}]
      @muokattu]]))

(defn sanktiolistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi-ja-toimenpide @nav/valittu-urakka]
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-sanktio {})}
    (ikonit/plus-sign) " Lisää sanktio"]
   [grid/grid
    {:otsikko       "Sanktiot"
     :tyhja         (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
    [{:otsikko "Päivämäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
     {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :havainto) :leveys 1}
     {:otsikko "Kuvaus" :nimi :kuvaus :hae (comp :kuvaus :havainto) :leveys 3}
     {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :havainto) :leveys 1}
     {:otsikko "Päätös" :nimi :paatos :hae (comp :paatos :paatos :havainto) :leveys 2}]
    @tiedot/haetut-sanktiot
    ]])


(defn sanktiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)

    (fn []
      (if @tiedot/valittu-sanktio
        [sanktion-tiedot]
        [sanktiolistaus]))))


  

  
