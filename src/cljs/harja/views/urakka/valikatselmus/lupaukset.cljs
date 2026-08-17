(ns harja.views.urakka.valikatselmus.lupaukset
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn lupauspaatos [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :lupaukset
        tyyppi (:tyyppi paatos)
        paatos-tehty? (some? (:id paatos))
        alitetut-pisteet (- (:luvatut_pisteet paatos) (:toteutuneet_pisteet paatos))
        ylitetyt-pisteet (- (:toteutuneet_pisteet paatos) (:luvatut_pisteet paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))]

    [:div.paatos-komponentti-reunuksella
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Lupaukset" paatos-tehty? paatos-avain avatut-paatokset
      (partial valikatselmus-tiedot/avaa-tai-sulje-haitari) (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     
     (when tallennus-kesken?
       [yleiset/ajax-loader-pieni "Tallennetaan tietoja..."])
     
     (when (and (not (:hoitovuosi-kesken? paatos)) (not (contains? avatut-paatokset paatos-avain)))
       [:div
        ;; Tuloksia ei näytetä mikäli tarjouksen tavoitehinta puuttuu
        (when (and (< 0 (:toteutuneet_pisteet paatos)) (:tavoitehinta paatos))
          [:div
           [:div.flex-row.laskenta-avattuna
            [:div "Toteuma"]
            [:div.laskenta-rivi-lukema (:toteutuneet_pisteet paatos)]]
           [:div.flex-row.laskenta-avattuna
            [:div "Luvattu yhteispistemäärä"]
            [:div.laskenta-rivi-lukema (:luvatut_pisteet paatos)]]
           [:div.flex-row.laskenta-avattuna.laskenta-alin
            [:div "Tulos"]
            [:div.laskenta-rivi-lukema (cond
                                         (= "bonus" tyyppi)
                                         (str "+" ylitetyt-pisteet)

                                         (= (:luvatut_pisteet paatos) (:toteutuneet_pisteet paatos))
                                         "0"

                                         :else
                                         (str "-" alitetut-pisteet))]]])

        [:div.lupaukset-linkki
         [harja.ui.yleiset/linkki "Siirry lupauksiin"
          #(siirtymat/avaa-lupaukset hoitokauden-alkuvuosi)
          {:luokka "klikattava alleviivaa"}]]

        ;; Laskentoja ei näytetä, mikäli tarjouksen tavoitehinta puuttuu
        (when (:tarjous_tavoitehinta paatos)
          [:div
           [:div
            (cond
              (= "sanktio" tyyppi)
              [:div
               [:p (str "Luvatun yhteispistemäärän alittaminen johtaa kutakin alittuvaa pistettä kohden " (:sanktioprosentti paatos) " %
           sanktioon kyseisen hoitovuoden tarjouksen mukaisesta tavoitehinnasta.")]
               [:p.paatos-laskelma (str "Lupaussanktio = " alitetut-pisteet
               " * " (/ (:sanktioprosentti paatos) 100)
               " * " (:tarjous_tavoitehinta paatos)
               " = " ) [:span.laskenta-rivi-lukema (fmt/euro-opt (:lupaussanktio paatos))]]]
              (= "bonus" tyyppi)
              ^{:key (str "lupaus-" (gensym))}
              [:div
               [:p (str "Luvatun yhteispistemäärän ylittäminen kutakin ylittävää pistettä kohden tuottaa " (:bonusprosentti paatos) " %
           bonuksen kyseisen hoitovuoden tarjouksen mukaisesta tavoitehinnasta.")]
               [:p.paatos-laskelma (str "Lupausbonus = " ylitetyt-pisteet
               " * " (/ (:bonusprosentti paatos) 100)
               " * " (:tarjous_tavoitehinta paatos)
               " = " ) [:span.laskenta-rivi-lukema (fmt/euro-opt (:lupausbonus paatos))]]])]

           [:div
            (cond
              (= "bonus" tyyppi)
              [:<>
               [:div
                [:div "Lupausbonus:"]
                [:div [:span.otsikko-lukema.laskenta-rivi-lukema (fmt/euro-opt (:lupausbonus paatos))]
                 (when (not (nil? (:indeksikorotus paatos)))
                   (str " (+ indeksi  " (fmt/euro-opt (:indeksikorotus paatos)) " )"))]]]
              (= "sanktio" tyyppi)
              [:<>
               [:div
                [:div "Lupaussanktio:"]
                [:div [:span.otsikko-lukema.laskenta-rivi-lukema (fmt/euro-opt (:lupaussanktio paatos))]
                 (when (not (nil? (:indeksikorotus paatos)))
                   (str " (+ indeksi  " (fmt/euro-opt (:indeksikorotus paatos)) " )"))]]]
              (= (:luvatut_pisteet paatos) (:toteutuneet_pisteet paatos))
              [:<>
               [:div.big-text "Ei bonusta eikä sanktiota"]])]])

        [:hr.paatos-hr]

        ;; Jos päätöksessä on virhe, niin näytetään se
        (when (:virheet paatos)
          [:div [yleiset/info-laatikko :vahva-ilmoitus "Et voi vahvistaa päätöstä, sillä osa pohjatiedoista puuttuu"
                 (:virheet paatos) nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])

        ;; Muokkaa, eli poista päätös, tai jos sitä ei ole tehty, niin tee päätös
        [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? (and (not (:virheet paatos)) voi-muokata?)
         #(e! (valikatselmus-tiedot/->TallennaLupausPaatos paatos))
         (valikatselmus-yhteiset/paatoksen-poistovarmistus-modaali {:peru-paatos-fn #(e! (valikatselmus-tiedot/->PoistaLupausPaatos paatos))
                                                                    :teksti "Automaattisesti kirjattu bonus/sanktio poistetaan."})
         #(if (:lupaussanktio paatos)
            [:p "Aluevastaava tekee päätöksen sanktion maksamisesta."]
            [:p "Aluevastaava tekee päätöksen bonuksen maksamisesta."])]])

     ;; Ei näytetä sisältöä, mikäli hoitovuosi on kesken
     (when (and (:hoitovuosi-kesken? paatos) (not (contains? avatut-paatokset paatos-avain)))
       [:p "Sisältö nähtävillä vasta, kun hiotokausi päättyy."])]))
