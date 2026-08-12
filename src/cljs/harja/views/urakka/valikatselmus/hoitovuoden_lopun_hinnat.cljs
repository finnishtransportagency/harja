(ns harja.views.urakka.valikatselmus.hoitovuoden-lopun-hinnat
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn paatos [e! paatos oikeudet-muokata? tallennus-kesken? avatut-paatokset]
  (let [paatos-avain :hoitovuoden-lopun-tavoite-ja-kattohinta
        paatos-tehty? (some? (:id paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))]
    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-reunuksella
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Hoitovuoden lopun tavoite- ja kattohinta" paatos-tehty? paatos-avain avatut-paatokset
      (partial valikatselmus-tiedot/avaa-tai-sulje-haitari) (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div

        [:div
         [:div
          [:div.flex-row.lista-rivi-korkea
           [:div "Hoitovuoden alun indeksikorjattu tavoitehinta"]
           [:div [:strong (fmt/euro-opt false (:tavoitehinta_ennen paatos))]]]
          [:div.flex-row.lista-rivi-korkea
           [:div "Tavoitehinnan muutokset"]
           [:div [:strong (fmt/euro-opt false true (:tavoitehinnan_muutokset paatos))]]]
          ;; Jos urakalle on asetettu parametriksi, että tavoitehintaan vaikuttaa myös hoitovuoden lopun indeksikorjaukset
          (when (:lisaa_tavoitehintaan_lopunindeksikorjaus paatos)
            [:div.flex-row.lista-rivi-korkea
             [:div "Hoitovuoden lopun indeksikorjaus"]
              [:div [:strong (fmt/euro-opt false (:hoitokauden_lopun_indeksikorjaus paatos))]]])

           [:div.flex-row
            [:div.big-text "Hoitovuoden lopun tavoitehinta"]
            [:div.big-text.lihavoitu (fmt/euro-opt false (:tavoitehinta_jalkeen paatos))]]
           [:div.flex-row
            [:div
             [:div.big-text "Hoitovuoden lopun kattohinta"]
             [:div.small-text (str (fmt/piste->pilkku (:kattohintakerroin paatos)) " x hoitovuoden lopun tavoitehinta")]]
            [:div.big-text.lihavoitu (fmt/euro-opt false (:kattohinta paatos))]]]
          [:hr.paatos-hr]

         (when (:virheet paatos)
           [:div
            [yleiset/info-laatikko :vahva-ilmoitus "Et voi vahvistaa päätöstä, sillä osa pohjatiedoista puuttuu"
             (:virheet paatos) nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])

          [:div
           [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken?
            (or (not on-oikeudet?) (not (:virheet paatos)))
            #(e! (valikatselmus-tiedot/->TallennaHoitokaudenlopunHintapaatos paatos))
            #(e! (valikatselmus-tiedot/->PoistaHoitokaudenlopunHintapaatos paatos))]]]])]))
