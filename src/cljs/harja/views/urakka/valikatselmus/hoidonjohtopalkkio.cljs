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
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
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

(defn- laskenta-modaali [paatos]
  [:div
   [:div.flex-row
    [:p "Hoidonjohtopalkkioon tehdään muutos, jos hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia muuttuu"
     [:strong " > 5% "] "tarjouksen mukaisen tavoitehintaan verrattuna."]]
   [:div.flex-row
    [:div "Hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia"]
    [:div [:strong (fmt/euro-opt (:tavoitehinta paatos))]]]
   [:div.flex-row
    [:div "Tarjouksen mukainen tavoitehinta"]
    [:div [:strong (fmt/euro-opt (:tarjouksen_tavoitehinta paatos))]]]
   [:div.flex-row
    [:div (str "Muutosprosentti (" (fmt/euro-opt (:tavoitehinta paatos)) " / "
            (fmt/euro-opt (:tarjouksen_tavoitehinta paatos)) " - 1) * 100")]
    [:div [:strong (fmt/euro-opt false (:muutosprosentti paatos)) " %"]]]
   [:div.flex-row
    [:div "Hoitovuoden indeksikorjattu hoidonjohtopalkkio"]
    [:div [:strong (fmt/euro-opt (:hoidonjohtopalkkio paatos))]]]
   [:div.row {:style {:padding-top "1rem"}}
    [:div
     [:strong "Hoidonjohtopalkkion muutos ="]]
    [:div (str "(" (fmt/euro-opt (:tavoitehinta paatos)) " / "
            (fmt/euro-opt (:tarjouksen_tavoitehinta paatos)) " - 1) * "
            (fmt/euro-opt (:hoidonjohtopalkkio paatos))
            " = ")
     [:strong (fmt/euro-opt (:hoidonjohtopalkkio_muutos paatos))]]]])

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
         [:p "Hoidonjohtopalkkioon tehdään muutos, jos hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia muuttuu"
          [:strong " > 5% "] "tarjouksen mukaisen tavoitehintaan verrattuna."]]
        (if-not (:virhe paatos)
          [:div
           [:div.flex-row
            [:div "Muutosprosentti"]
            [:div [:strong (fmt/euro-opt false (:muutosprosentti paatos)) " %"]]]
           [:div.flex-row
            [:div "Hoidonjohtopalkkion muutos"]
            [:div [:strong (fmt/euro-opt (:hoidonjohtopalkkio_muutos paatos))]]]
           [:div.flex-row
            [yleiset/linkki "Näytä laskenta"
             (fn [] (modal/nayta! {:otsikko "Laskenta"
                                   :footer [napit/sulje #(modal/piilota!)]}
                      [laskenta-modaali paatos]))]]
           [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
            #(e! (valikatselmus-tiedot/->TallennaHoidonjohtopalkkionMuutospaatos paatos))
            #(e! (valikatselmus-tiedot/->PoistaHoidonjohtopalkkionMuutospaatos paatos))]]
          [:div {:style {:padding-bottom "1rem"}}
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos)]])])]))
