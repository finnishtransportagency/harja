(ns harja.views.urakka.valikatselmus.hoidonjohtopalkkio
  (:require [reagent.core :as r :refer [atom]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.urakka :as urakka]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.dom :as dom]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as kulut-yhteiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn paatos [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :hoidonjohtopalkkion-muutos
        paatos-tehty? (or (:id paatos) false)

        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Hoidonjohtopalkkion muutos" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:p "Hoidonjohtopalkkioon tehdään muutos, jos hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia muuttuu > 5% tarjouksen mukaisen tavoitehintaan verrattuna."]
         ]])]))
