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
  (let [_ (js/console.log "lupauspaatos")
        paatos-avain :lupaukset
        tyyppi (:tyyppi paatos)
        paatos-tehty? (or (not (nil? (:paatos-id paatos))) false)
        alitetut-pisteet (- (:luvatut_pisteet paatos) (:toteutuneet_pisteet paatos))
        ylitetyt-pisteet (- (:toteutuneet_pisteet paatos) (:luvatut_pisteet paatos))
        paatoksen-tiedot {:paatos-id (:paatos-id paatos)
                          :urakkaid (-> @tila/yleiset :urakka :id)
                          :tyyppi tyyppi
                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                          :luvatut_pisteet (:luvatut_pisteet paatos)
                          :toteutuneet_pisteet (:toteutuneet_pisteet paatos)
                          :lupausbonus (:lupausbonus paatos)
                          :lupaussanktio (:lupaussanktio paatos)
                          :tavoitehinta (:tavoitehinta paatos)}

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
        (when (:tavoitehinta paatos)
          [:div
           [:div.flex-row
            [:div "Toteumat"]
            [:div (:toteutuneet_pisteet paatos)]]
           [:div.flex-row
            [:div "Luvattu pistemäärä"]
            [:div (:luvatut_pisteet paatos)]]
           [:div.flex-row
            [:div "Tulos"]
            [:div alitetut-pisteet]]])

        [:div {:style {:flex-grow 1
                       :padding "1rem 0 1rem 0"
                       :text-align "left"}}
         [harja.ui.yleiset/linkki "Siirry lupauksiin"
          #(siirtymat/avaa-lupaukset hoitokauden-alkuvuosi)]]

        ;; Laskentoja ei näytetä, mikäli tarjouksen tavoitehinta puuttuu
        (if (:tavoitehinta paatos)
          [:div
           [:div
            (cond
              (= :sanktio tyyppi)
              [:div
               [:p "Luvatun yhteispistemäärän alittaminen johtaa kutakin alittuvaa pistetä kohden 0,18%
           sanktioon kyseisen hoitokauden tarjouksen mukaisesta tavoitehinnasta."]
               [:p (str "Lupaussanktio = " alitetut-pisteet " * 0,0018 * " (:tavoitehinta paatos) " = " (:lupaussanktio paatos) " €")]]
              (= :bonus tyyppi)
              ^{:key (str "lupaus-" (gensym))}
              [:div
               [:p "Luvatun yhteispistemäärän ylittäminen kutakin ylittävää pistettä kohden tuottaa 0,08%
           bonuksen kyseisen hoitokauden tarjouksen mukaisesta tavoitehinnasta."]
               [:p (str "Lupausbonus = " ylitetyt-pisteet " * 0,008% * " (:tavoitehinta paatos) " = " (:lupausbonus paatos) " €")]]
              :else
              [:div ""])]

           [:div {:style {:padding-top "22px"}}
            (cond
              (= :bonus tyyppi)
              [:<>
               [:div
                [:div "Lupausbonus:"]
                [:div [:strong (fmt/euro-opt (:lupausbonus paatos))]]]]
              (= :sanktio tyyppi)
              [:<>
               [:div
                [:div "Lupaussanktio:"]
                [:div [:strong (fmt/euro-opt (:lupaussanktio paatos))]]]]
              (= :taytetty tyyppi)
              [:<>
               [:h2 "Ei bonusta eikä sanktiota"]])]]
          [:div "Päätöstä ei voi tallentaa. Tarjouksen tavoitehinta puuuttuu."])


        ;; Muokkaa, eli poista päätös, tai jos sitä ei ole tehty, niin tee päätös
        (if (not (:paatos-id paatos))
          [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
           (if on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaPaatos
                     ;; Lupaus-päätös tallennetaan aina uutena tai poistetaan - ei muokata
                     (dissoc paatoksen-tiedot ::valikatselmus/paatoksen-id)))
              {:disabled (or (not (:tavoitehinta paatos)) tallennus-kesken? (not voi-muokata?))}]
             (if (:lupaussanktio paatos)
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))]
          ^{:key (str "lupaus-" (gensym))}
          [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
           (if on-oikeudet?
             [napit/nappi
              "Kumoa päätös"
              #(e! (valikatselmus-tiedot/->PoistaLupausPaatos (:paatos-id paatos)))
              {:luokka "nappi-toissijainen napiton-nappi"
               :ikoni [ikonit/harja-icon-action-undo]
               :disabled (or (not (:tavoitehinta paatos)) tallennus-kesken? (not voi-muokata?))}]
             (if (:lupaussanktio paatos)
               [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
               [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."]))])])]))
