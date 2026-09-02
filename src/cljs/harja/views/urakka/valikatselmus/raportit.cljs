(ns harja.views.urakka.valikatselmus.raportit
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defn raportit [e! {:keys [urakkaid virhe id] :as paatos} tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
  (let [paatos-avain :valikatselmuspoytakirjaan-liitettavat-raportit
        paatos-tehty? (some? id)
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        hallintayksikko-id (-> @tila/yleiset :urakka :hallintayksikko :id)
        voi-muokata? (not (:virhe paatos))]

    ^{:key (str "kattohinnan-ylitys-" (gensym))}
    [:div.paatos-komponentti-reunuksella
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Raportit" paatos-tehty? paatos-avain avatut-paatokset
      (partial valikatselmus-tiedot/avaa-tai-sulje-haitari) (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]

     (when (not (contains? avatut-paatokset paatos-avain))
       [:div

        [:div.flex-row.raportti-teksti
         [:p "Tarkista, että seuraavien raporttien luvut ovat oikein ja liitä raportit välikatselmuspöytäkirjaan. Hoitovuoden raportointi lukitaan 31.12." (inc hoitokauden-alkuvuosi)]]

        [:div.flex-row.ilmoitus
         [:div
          [ikonit/livicon-document-full]
          [harja.ui.yleiset/linkki "Ympäristöraportti"
           #(siirtymat/avaa-raportti :ymparistoraportti hallintayksikko-id urakkaid hoitokauden-alkuvuosi)
           {:luokka "klikattava alleviivaa"}]]]

        [:div.flex-row.ilmoitus
         [:div [ikonit/livicon-document-full]
          [harja.ui.yleiset/linkki "Laskutusyhteenveto"
           #(siirtymat/avaa-raportti :laskutusyhteenveto-tyomaa hallintayksikko-id urakkaid hoitokauden-alkuvuosi)
           {:luokka "klikattava alleviivaa"}]]]

        [:div.flex-row.ilmoitus-matala
         [:div [ikonit/livicon-document-full]
          [harja.ui.yleiset/linkki "Tehtävämääräraportti"
           #(siirtymat/avaa-raportti :tehtavamaarat hallintayksikko-id urakkaid hoitokauden-alkuvuosi)
           {:luokka "klikattava alleviivaa"}]]]

        [:hr.paatos-hr-matalin]

        [:div.muokkaustoiminnot
         [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatos tallennus-kesken? (and voi-muokata? (not virhe))
          ;; Vahvista
          #(e! (valikatselmus-tiedot/->TallennaPoytakirjanRaporttiPaatos paatos))
          ;; Peru päätös 
          #(e! (valikatselmus-tiedot/->HaeKetjutetustiKumoutuvatPaatokset
                 paatos
                 (fn [] (e! (valikatselmus-tiedot/->PeruValikatselmusPaatos paatos)))))]]])]))
