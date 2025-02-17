(ns harja.views.urakka.valikatselmus.hintapaatokset
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

(defn tavoitehinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :tavoitehinta-ylitys
        paatos-tehty? (or (:id paatos) false)
        paatoksen-tiedot {:id (:id paatos)
                          :urakkaid (-> @tila/yleiset :urakka :id)
                          :versio (:versio paatos)
                          :paatostyyppi paatos-avain
                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                          :tavoitehinta (:tavoitehinta paatos)
                          :toteutuneet_kustannukset (:toteutuneet_kustannukset paatos)
                          :ylityksen_maara (:ylityksen_maara paatos)
                          :tilaajan_prosentti (:tilaajan_prosentti paatos)
                          :urakoitsijan_prosentti (:urakoitsijan_prosentti paatos)
                          :tilaaja_maksaa (:tilaaja_maksaa paatos)
                          :urakoitsija_maksaa (:urakoitsija_maksaa paatos)}
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan ylitys" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div [:h4 "Tavoitehinnan ylitys"]]
         [:div.bold (fmt/euro-opt (:ylityksen_maara paatos))]]
       [:div.flex-row
        [:div (str "Tilaaja maksaa (" (:tilaajan_prosentti paatos) "%)")]
        [:div (fmt/euro-opt (:tilaaja_maksaa paatos))]]
       [:div.flex-row
        [:div (str "Urakoitsija maksaa (" (:urakoitsijan_prosentti paatos) "%)")]
        [:div (fmt/euro-opt (:urakoitsija_maksaa paatos))]]

       ;; Päätöksenteko napit
       (if (not paatos-tehty?)
         [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
          (when on-oikeudet?
            [napit/yleinen-ensisijainen "Tallenna päätös"
             #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanYlitysPaatos paatoksen-tiedot))
             {:disabled (or
                          tallennus-kesken?
                          (not voi-muokata?))}])]
         [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
          (when on-oikeudet?
            [napit/nappi
             "Kumoa päätös"
             #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanYlitysPaatos paatos))
             {:luokka "nappi-toissijainen napiton-nappi"
              :ikoni [ikonit/harja-icon-action-undo]
              :disabled (or
                          tallennus-kesken?
                          (not voi-muokata?))}])])])]))

(defn tavoitehinnan-alitus [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :tavoitehinta-alitus
        paatos-tehty? (boolean (:id paatos))
        paatoksen-tiedot (merge paatos
                           {:urakkaid (-> @tila/yleiset :urakka :id)})
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-alitus-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan alitus" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div [:h4 "Tavoitehinnan alitus"]]
         [:div.bold (fmt/euro-opt (:alituksen_maara paatos))]]
        [:div.flex-row
         [:div "Tavoitepalkkio"]
         [:div (fmt/euro-opt (:tavoitepalkkio paatos))]]
        [:div.flex-row
         [:div "Siirretään seuraavan vuoden hankintakustannuksiin alennukseksi"]
         [:div (fmt/euro-opt (:siirron_maara paatos))]]

        ;; Päätöksenteko napit
        (if (not paatos-tehty?)
          [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
           (when on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanAlitusPaatos paatoksen-tiedot))
              {:disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])]
          [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
           (when on-oikeudet?
             [napit/nappi
              "Kumoa päätös"
              #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanAlitusPaatos paatoksen-tiedot))
              {:luokka "nappi-toissijainen napiton-nappi"
               :ikoni [ikonit/harja-icon-action-undo]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])])])]))

(defn kattohinnan-ylitys [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :kattohinta-ylitys
        paatos-tehty? (or (:id paatos) false)
        siirra? (:siirra? paatos)
        paatoksen-tiedot {:id (:id paatos)
                          :urakkaid (-> @tila/yleiset :urakka :id)
                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                          :kattohinta (:kattohinta paatos)
                          :toteutuneet_kustannukset (:toteutuneet_kustannukset paatos)
                          :ylityksen_maara (:ylityksen_maara paatos)
                          :siirrettava_maara (:siirrettava_maara paatos)
                          :urakoitsija_maksaa (:urakoitsija_maksaa paatos)}
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Kattohinnan ylitys" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [:div.flex-row
         [:div [:h4 "Kattohinnan ylitys"]]
         [:div.bold (fmt/euro-opt (:ylityksen_maara paatos))]]
        [:div.flex-row
         [:div [kentat/tee-kentta
                {:tyyppi :checkbox
                 :disabled? (or paatos-tehty? false)
                 :teksti "Aseta siirrettävä määrä (€)"
                 :vayla-tyyli? true
                 :nayta-rivina? true
                 :iso-clickalue? true}
                (r/wrap
                  siirra?
                  (fn [uusi-arvo]
                    (e! (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoCheckbox uusi-arvo))))]]
         [:div.harmaa-tausta
          (when siirra?

            [kentat/tee-kentta {:tyyppi :numero
                                :disabled? (or paatos-tehty? false)
                                :elementin-id "kattohinta-ylitys-siirto"
                                ;;TODO: Korjaa nämä. Focuksen kanssa ongelmia
                                #_#_:on-key-down #(when (or (= 13 (-> % .-keyCode)) (= 13 (-> % .-which)))
                                                (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoMaara %))
                                #_#_:on-blur #(e! (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoMaara %))}
             (r/wrap
               (:siirrettava_maara paatos)
               (fn [uusi-arvo]
                 (js/console.log "Ei tehdä mitään, uusi arvo" (pr-str uusi-arvo))
                 (e! (valikatselmus-tiedot/->PaivitaKattohinnanSiirtoMaara uusi-arvo))))])]]
        [:div.flex-row
         [:div "Urakoitsija maksaa"]
         [:div (fmt/euro-opt (:urakoitsija_maksaa paatos))]]
        (when (and (:siirrettava_maara paatos) (> (:siirrettava_maara paatos) 0))
          [:div.flex-row
           [:div "Siirrettävä määrä"]
           [:div (fmt/euro-opt (:siirrettava_maara paatos))]])

        ;; Päätöksenteko napit - TODO: Tsekkaappa, että voisko nämä kaikki napit komponentisoida, kun tekevät kuitenkin kaikissa päätöksissä ihan sammaa asiaa.
        (if (not paatos-tehty?)
          [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
           (when on-oikeudet?
             [napit/yleinen-ensisijainen "Tallenna päätös"
              #(e! (valikatselmus-tiedot/->TallennaKattohinnanYlitysPaatos paatoksen-tiedot))
              {:disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])]
          [:div {:style {:flex-grow 1 :padding-top "1rem" :padding-bottom "1rem"}}
           (when on-oikeudet?
             [napit/nappi
              "Kumoa päätös"
              #(e! (valikatselmus-tiedot/->PoistaKattohinnanYlitysPaatos paatos))
              {:luokka "nappi-toissijainen napiton-nappi"
               :ikoni [ikonit/harja-icon-action-undo]
               :disabled (or
                           tallennus-kesken?
                           (not voi-muokata?))}])])])]))
