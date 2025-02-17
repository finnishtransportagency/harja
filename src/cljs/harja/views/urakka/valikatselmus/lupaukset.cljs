(ns harja.views.urakka.valikatselmus.lupaukset
  (:require [reagent.core :as r :refer [atom]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.urakka :as urakka]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.dom :as dom]
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

(defn lupauspaatos [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :lupaukset
        tyyppi (:tyyppi paatos)
        paatos-tehty? (or (not (nil? (:id paatos))) false)
        alitetut-pisteet (- (:luvatut_pisteet paatos) (:toteutuneet_pisteet paatos))
        ylitetyt-pisteet (- (:toteutuneet_pisteet paatos) (:luvatut_pisteet paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]

    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Lupaukset" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        ;; Tuloksia ei näytetä mikäli tarjouksen tavoitehinta puuttuu
        (when (and (< 0 (:toteutuneet_pisteet paatos)) (:tavoitehinta paatos))
          [:div
           [:div.flex-row {:style {:margin-bottom "3px"}}
            [:div "Toteuma"]
            [:div (:toteutuneet_pisteet paatos)]]
           [:div.flex-row {:style {:margin-bottom "3px"}}
            [:div "Luvattu yhteispistemäärä"]
            [:div (:luvatut_pisteet paatos)]]
           [:div.flex-row {:style {:margin-bottom "3px"}}
            [:div "Tulos"]
            [:div (if (= "bonus" tyyppi) (str "+" ylitetyt-pisteet) (str "-" alitetut-pisteet))]]])

        [:div.lupaukset-linkki
         [harja.ui.yleiset/linkki "Siirry lupauksiin"
          #(siirtymat/avaa-lupaukset hoitokauden-alkuvuosi)
          {:luokka "klikattava alleviivaa"}]]

        ;; Laskentoja ei näytetä, mikäli tarjouksen tavoitehinta puuttuu
        (if (:tarjous_tavoitehinta paatos)
          [:div
           [:div
            (cond
              (= "sanktio" tyyppi)
              [:div
               [:p "Luvatun yhteispistemäärän alittaminen johtaa kutakin alittuvaa pistetä kohden 0,18%
           sanktioon kyseisen hoitokauden tarjouksen mukaisesta tavoitehinnasta."]
               [:p.paatos-laskelma (str "Lupaussanktio = " alitetut-pisteet " * 0,0018 * " (:tarjous_tavoitehinta paatos) " = " ) [:strong (fmt/euro-opt (:lupaussanktio paatos))]]]
              (= "bonus" tyyppi)
              ^{:key (str "lupaus-" (gensym))}
              [:div
               [:p "Luvatun yhteispistemäärän ylittäminen kutakin ylittävää pistettä kohden tuottaa 0,08%
           bonuksen kyseisen hoitokauden tarjouksen mukaisesta tavoitehinnasta."]
               [:p.paatos-laskelma (str "Lupausbonus = " ylitetyt-pisteet " * 0,008 * " (:tarjous_tavoitehinta paatos) " = " ) [:strong (fmt/euro-opt (:lupausbonus paatos))]]]
              :else
              [:div ""])]

           [:div
            (cond
              (= "bonus" tyyppi)
              [:<>
               [:div
                [:div "Lupausbonus:"]
                [:div {:style {:font-size "20px"}} [:strong (fmt/euro-opt (:lupausbonus paatos))]]]]
              (= "sanktio" tyyppi)
              [:<>
               [:div
                [:div "Lupaussanktio:"]
                [:div {:style {:font-size "20px"}} [:strong (fmt/euro-opt (:lupaussanktio paatos))]]]]
              (= :taytetty tyyppi)
              [:<>
               [:h2 "Ei bonusta eikä sanktiota"]])]]
          [:div [yleiset/info-laatikko :vahva-ilmoitus "Päätöstä ei voi tallentaa. Tarjouksen tavoitehinta puuttuu."]])

        ;; Laskentoja ei näytetä, mikäli lupauksen toteumia puuttuu
        (when (> 1 (:toteutuneet_pisteet paatos))
          [:div [yleiset/info-laatikko :vahva-ilmoitus "Päätöstä ei voi tallentaa. Lupausten toteumia puuttuu."]])


        ;; Muokkaa, eli poista päätös, tai jos sitä ei ole tehty, niin tee päätös
        (if (not (:id paatos))
          [:div.paatos-toiminto
           (if on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaLupausPaatos paatos))
              {:ikoni [ikonit/harja-icon-status-selected]
               :disabled (or (not (:tavoitehinta paatos)) (> 1 (:toteutuneet_pisteet paatos)) tallennus-kesken? (not voi-muokata?))}]
             (if (:lupaussanktio paatos)
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))]
          ^{:key (str "lupaus-" (gensym))}
          [:div.paatos-toiminto
           (if on-oikeudet?
             [napit/yleinen-toissijainen
              "Peru päätös"
              #(e! (valikatselmus-tiedot/->PoistaLupausPaatos paatos))
              {:ikoni [ikonit/harja-icon-action-undo]
               :disabled (or (not (:tavoitehinta paatos)) tallennus-kesken? (not voi-muokata?))}]
             (if (:lupaussanktio paatos)
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))])])]))
