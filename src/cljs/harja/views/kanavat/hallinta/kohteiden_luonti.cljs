(ns harja.views.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.hallinta.kohteiden-luonti :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.ui.napit :as napit])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn luontilomake [e! app]
  [:div "Moi"])

(defn kohteiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeKohteet)))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [kohderivit kohteiden-haku-kaynnissa? kohdelomake-auki?] :as app}]
      (if-not kohdelomake-auki?
        [:div
         [grid/grid
          {:tunniste ::kohde/id
           :tyhja (if kohteiden-haku-kaynnissa?
                    [ajax-loader "Haetaan kohteita"]
                    "Ei perustettuja kohteita")}
          [{:otsikko "Kanava, kohde, ja kohteen tyyppi" :nimi :rivin-teksti}]
          kohderivit]
         [napit/uusi
          "Lisää uusi kohde"
          #(e! (tiedot/->AvaaKohdeLomake))]]

        [modal/modal
         {:otsikko "Lisää kohde Harjaan"
          :footer [:span
                   [napit/tallenna "Tallenna kohteet" #(println "Tallenna!")]
                   [napit/peruuta "Peruuta tallentamatta" #(println "Peruuta!")]]}
         [luontilomake e! app]]))))

(defc kohteiden-luonti []
  [tuck tiedot/tila kohteiden-luonti*])
