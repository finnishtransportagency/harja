(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma
  "UI-komponenttien tarkastelusivu.

  Komponentit kuvataan rekisterissä (`harja.ui.komponentit.rekisteri`) ja niiden esikatselut
  ovat ryhmäkohtaisissa tiedostoissa. Uusi komponentti lisätään rekisteriin ja sen esikatselu
  samalla `:id`:llä esikatselut-karttaan."
  (:require [reagent.core :as r]
            [harja.ui.komponentit.rekisteri :as rekisteri]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot :as alasvedot]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.lomakkeet :as lomakkeet]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.toiminnot :as toiminnot]))

(def ^:private esikatselut
  (merge alasvedot/esikatselut
         toiminnot/esikatselut
         lomakkeet/esikatselut))

(defn ui-komponenttien-tarkastelu []
  (r/with-let [valittu (r/atom (-> rekisteri/komponentit first :id))]
    (let [ryhmat (rekisteri/ryhmittele)
          komponentti (rekisteri/hae @valittu)
          ryhman-nimi (some #(when (= (:id %) (:ryhma komponentti)) (:nimi %)) rekisteri/ryhmat)
          esikatselu (get esikatselut @valittu kehys/esikatselua-ei-ole)]
      [kehys/kehys
       {:otsikko "UI-komponenttien tarkastelu"
        :kuvaus "Shared-komponenttien API:n, tilojen ja Bootstrap-riippuvuuden tarkistussivu."}
       [kehys/navigaatio
        {:otsikko "Komponentit"
         :ryhmat ryhmat
         :valittu @valittu
         :valitse-fn #(reset! valittu %)}]
       [kehys/sisalto komponentti ryhman-nimi [esikatselu komponentti]]
       [kehys/sopimus komponentti]])))
