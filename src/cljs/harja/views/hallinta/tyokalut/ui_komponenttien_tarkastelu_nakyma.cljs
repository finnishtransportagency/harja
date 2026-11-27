(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma
  "UI-komponenttien tarkastelusivu.

  Komponentit kuvataan rekisterissä (`harja.ui.perus.rekisteri`) ja niiden esikatselut
  rekisteröidään ryhmäkohtaisissa tiedostoissa `register!`-kutsulla samalla `:id`:llä."
  (:require [reagent.core :as r]
            [harja.ui.perus.esimerkkisivut :as esimerkkisivut]
            [harja.ui.perus.rekisteri :as rekisteri]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.gridit]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.lomakkeet]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.palautteet]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.portatut]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :as rekisterointi]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.toiminnot]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.simulaattori :as simulaattori]))

(defn- simulaattorin-navigaatioryhmat []
  (conj (esimerkkisivut/ryhmittele)
    {:id :oma-syote
     :nimi "Oma sisältö"
     :komponentit [{:id :oma-syote :nimi "Muokkaa omaa EDN:ää"}]}))

(defn ui-komponenttien-tarkastelu []
  (r/with-let [valittu (r/atom (-> rekisteri/komponentit first :id))
               valittu-nakyma (r/atom :komponentit)
               simulaattori-koko-levea? (r/atom false)]
    (let [ryhmat (rekisteri/ryhmittele)
          komponentti (rekisteri/hae @valittu)
          ryhman-nimi (some #(when (= (:id %) (:ryhma komponentti)) (:nimi %)) rekisteri/ryhmat)
          esikatselu (rekisterointi/esikatselu @valittu)
          simulaattori? (= :simulaattori @valittu-nakyma)]
      [kehys/kehys
       {:otsikko "UI-komponenttien tarkastelu"
        :kuvaus "Aloita tästä: säiliö → osio → kortti, ruudukko tai lomake. Tarkista rekisterin suositus ennen käyttöä."
        :simulaattori? simulaattori?
        :koko-levea? @simulaattori-koko-levea?}
       [:div
        [kehys/navigaatio
         {:otsikko "Näkymät"
          :ryhmat [{:id :nakyma :nimi "Työkalut"
                    :komponentit [{:id :komponentit :nimi "Komponentit"}
                                  {:id :simulaattori :nimi "Sivusimulaattori"}]}]
          :valittu @valittu-nakyma
          :nayta-elinkaaripaatos? false
          :valitse-fn #(reset! valittu-nakyma %)}]
        (if simulaattori?
          [kehys/navigaatio
           {:otsikko "Esimerkkisivut"
            :ryhmat (simulaattorin-navigaatioryhmat)
            :valittu (or @simulaattori/valittu-esimerkki :oma-syote)
            :nayta-elinkaaripaatos? false
            :data-cy "ui-komponenttien-tarkastelu-esimerkkiluettelo"
            :valitse-fn simulaattori/valitse-esimerkki!}]
          [kehys/navigaatio
           {:otsikko "Komponentit"
            :ryhmat ryhmat
            :valittu @valittu
            :valitse-fn #(do (reset! valittu %)
                             (reset! valittu-nakyma :komponentit))}])]
       (if simulaattori?
         [kehys/sisalto {:nimi "Sivusimulaattori"
                         :kuvaus "Rajattu deklaratiivinen sivurakenne rekisteröidyistä komponenteista."}
                        "Työkalut"
                        [simulaattori/sivu simulaattori-koko-levea?]]
         [kehys/sisalto komponentti ryhman-nimi [esikatselu komponentti]])
       (if simulaattori?
         nil
         [kehys/sopimus komponentti])])))
