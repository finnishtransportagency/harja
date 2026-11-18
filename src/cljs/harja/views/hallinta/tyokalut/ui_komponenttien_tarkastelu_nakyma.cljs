(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma
  "UI-komponenttien tarkastelusivu.

  Komponentit kuvataan rekisterissä (`harja.ui.komponentit.rekisteri`) ja niiden esikatselut
  ovat ryhmäkohtaisissa tiedostoissa. Uusi komponentti lisätään rekisteriin ja sen esikatselu
  samalla `:id`:llä esikatselut-karttaan."
  (:require [reagent.core :as r]
            [harja.ui.komponentit.rekisteri :as rekisteri]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot :as alasvedot]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.gridit :as gridit]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.lomakkeet :as lomakkeet]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.portatut :as portatut]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.toiminnot :as toiminnot]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.simulaattori :as simulaattori]))

(def ^:private esikatselut
  (merge alasvedot/esikatselut
         toiminnot/esikatselut
         gridit/esikatselut
         lomakkeet/esikatselut
         portatut/esikatselut))

(defn ui-komponenttien-tarkastelu []
  (r/with-let [valittu (r/atom (-> rekisteri/komponentit first :id))
               valittu-nakyma (r/atom :komponentit)]
    (let [ryhmat (rekisteri/ryhmittele)
          komponentti (rekisteri/hae @valittu)
          ryhman-nimi (some #(when (= (:id %) (:ryhma komponentti)) (:nimi %)) rekisteri/ryhmat)
          esikatselu (get esikatselut @valittu kehys/esikatselua-ei-ole)]
      [kehys/kehys
       {:otsikko "UI-komponenttien tarkastelu"
        :kuvaus "Shared-komponenttien API:n, tilojen ja Bootstrap-riippuvuuden tarkistussivu."}
       [:div
        [kehys/navigaatio
         {:otsikko "Näkymät"
          :ryhmat [{:id :nakyma :nimi "Työkalut"
                    :komponentit [{:id :komponentit :nimi "Komponentit"}
                                  {:id :simulaattori :nimi "Sivusimulaattori"}]}]
          :valittu @valittu-nakyma
          :valitse-fn #(reset! valittu-nakyma %)}]
        [kehys/navigaatio
         {:otsikko "Komponentit"
          :ryhmat ryhmat
          :valittu @valittu
          :valitse-fn #(do (reset! valittu %)
                           (reset! valittu-nakyma :komponentit))}]]
       (if (= :simulaattori @valittu-nakyma)
         [kehys/sisalto {:nimi "Sivusimulaattori"
                         :kuvaus "Rajattu deklaratiivinen sivurakenne rekisteröidyistä komponenteista."}
                        "Työkalut"
                        [simulaattori/sivu]]
         [kehys/sisalto komponentti ryhman-nimi [esikatselu komponentti]])
       (if (= :simulaattori @valittu-nakyma)
         [kehys/sopimus {:api "Deklaratiivinen puu: komponentti + asetukset + lapset"
                         :bootstrap-tila :ei-riippuvuutta
                         :id :simulaattori}
                        [simulaattori/lahde]]
         [kehys/sopimus komponentti])])))
