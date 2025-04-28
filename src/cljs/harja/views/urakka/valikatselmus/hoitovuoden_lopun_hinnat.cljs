(ns harja.views.urakka.valikatselmus.hoitovuoden-lopun-hinnat
  (:require [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn paatos [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :hoitovuoden-lopun-tavoite-ja-kattohinta
        paatos-tehty? (or (:id paatos) false)

        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-border
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Hoitovuoden lopun tavoite- ja kattohinta" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        (if-not (:virhe paatos)
          [:div
           [:div
            [:div.flex-row.lista_rivi_korkea
             [:div "Hoitovuoden alun indeksikorjattu tavoitehinta"]
             [:div [:strong (fmt/euro-opt false (:tavoitehinta_ennen paatos))]]]
            [:div.flex-row.lista_rivi_korkea
             [:div "Tavoitehinnan muutokset"]
             [:div [:strong (fmt/euro-opt false true (:tavoitehinnan_muutokset paatos))]]]
            ;; Jos urakalle on asetettu parametriksi, että tavoitehintaan vaikuttaa myös hoitovuoden lopun indeksikorjaukset
            (when (:lisaa_tavoitehintaan_lopunindeksikorjaus paatos)
              [:div.flex-row.lista_rivi_korkea
               [:div "Hoitovuoden lopun indeksikorjaus"]
               [:div [:strong (fmt/euro-opt false (:hoitokauden_lopun_indeksikorjaus paatos))]]])

            [:div.flex-row
             [:h3.ennen-painiketta "Hoitovuoden lopun tavoitehinta"]
             [:div.otsikko_lukema (fmt/euro-opt false (:tavoitehinta_jalkeen paatos))]]
            [:div.flex-row
             [:div
              [:h3.alempi-otsikko "Hoitovuoden lopun kattohinta"]
              [:div.small-text (str (:kattohintakerroin paatos) " x hoitovuoden lopun tavoitehinta")]]
             [:div.otsikko_lukema (fmt/euro-opt false (:kattohinta paatos))]]]
           [:hr.paatos-hr]
           [:div
            [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? voi-muokata?
             #(e! (valikatselmus-tiedot/->TallennaHoitokaudenlopunHintapaatos paatos))
             #(e! (valikatselmus-tiedot/->PoistaHoitokaudenlopunHintapaatos paatos))]]]

          [:div.ilmoitus
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos) nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])])]))
