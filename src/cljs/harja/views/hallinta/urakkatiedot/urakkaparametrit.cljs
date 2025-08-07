(ns harja.views.hallinta.urakkatiedot.urakkaparametrit
  "Urakoilla on urakan tyyppiin ja alkuvuoteen perustuvia parametreja, kuten indeksit yms. Mahdollistetaan
  täällä näiden parametrien muuttaminen ja asettaminen urakoille"
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.hallinta.urakkatiedot.urakkaparametrit-tiedot :as tiedot]))

(defn nakyma*
  "Muodostetaan urakkaparametrien näkymä"
  [e! app]
  (let []
    [:div
     "Muodosta urakkamuotoiset parametrit tänne."
     #_ [:div.row
      [:span.col-md-12
       [grid/muokkaus-grid
        {:otsikko "Urakkaparametrit MHU urakoille alkuvuoden perusteella"
         ;:voi-muokata? voi-muokata?
         :voi-poistaa? (constantly false)
         :piilota-toiminnot? true
         :voi-lisata? false
         :tyhja "Ei urakkaparametreja"
         :jarjesta :nimi
         ;:virheet taulukon-virheet
         :tunniste :urakka}

        [{:otsikko "Parametri" :nimi :nimi :leveys 3
          :muokattava? (constantly false)
          :tyyppi :string}
         {:otsikko "2019-2020"
          :nimi :arvo20192020 :leveys 1
          ;:desimaalien-maara 1
          ;:validoi [[:lampotila]]
          :muokattava? (constantly true)
          :tyyppi :numero}
         {:otsikko "2021-2024"
          :nimi :arvo201212020 :leveys 1
          :desimaalien-maara 1
          :validoi [[:lampotila]]
          :muokattava? (constantly true)
          :tyyppi :numero}
         {:otsikko "Vertailu\u00ADjakso 1981-2010 (\u2103)"
          :nimi :keskilampotila-1981-2010 :leveys 1
          :desimaalien-maara 1
          :validoi [[:lampotila]]
          :muokattava? (constantly true)
          :tyyppi :numero}
         {:otsikko "Vertailu\u00ADjakso 1971-2000 (\u2103)"
          :nimi :keskilampotila-1971-2000 :leveys 1
          :desimaalien-maara 1
          :validoi [[:lampotila]]
          :muokattava? (constantly true)
          :tyyppi :numero}]

        lampotilarivit]

       [napit/palvelinkutsu-nappi
        "Hae ilmatieteenlaitokselta"
        #(tiedot/hae-lampotilat-ilmatieteenlaitokselta valitun-kauden-alkuvuosi)
        {:luokka "nappi-toissijainen"
         :title "Tuodut lämpötilat lisätään taulukkoon, tarkastettuasi arvot voit tallentaa ne Harjaan."
         :disabled (< valitun-kauden-alkuvuosi 2011)
         :ikoni (ikonit/livicon-download)
         :virheviesti "Lämpötilojen haku epäonnistui. Yritä myöhemmin uudelleen."
         :kun-onnistuu (fn [urakat]
                         (reset! lampotilarivit (merge-with yhdista-lampotilat @lampotilarivit urakat))
                         (viesti/nayta! "Lämpötilat haettu ja päivitetty taulukkoon - tarkista tiedot ja tallenna." :success viesti/viestin-nayttoaika-keskipitka))}]

       [napit/palvelinkutsu-nappi
        "Tallenna"
        #(tiedot/tallenna-teiden-hoitourakoiden-lampotilat @tiedot/valittu-hoitokausi @lampotilarivit)
        {:luokka "nappi-ensisijainen pull-right"
         :disabled (not (and tiedot-muuttuneet? (empty? @taulukon-virheet)))
         :ikoni (ikonit/tallenna)
         :kun-onnistuu (fn [vastaus]
                         (viesti/nayta! "Lämpötilat tallennettu." :success)
                         (reset! tiedot/hoitourakoiden-lampotilat vastaus)
                         (log "Lämpötilat tallennettu, vastaus: " (pr-str vastaus)))}]

       ; tieindeksi2-tilastoinnin-alkuvuosi 2006 mutta API palauttaa lämpötiloja vasta 2011 alkaen
       (when (< valitun-kauden-alkuvuosi 2011)
         (yleiset/vihje "Ilmatieteenlaitokselta saa tietoja hoitokaudesta 2011-2012 eteenpäin"))
       [:div.ilmatieteenlaitos-linkki
        [:span "Voit myös katsella lämpötiloja "]
        [:a {:href "https://tieindeksi.weatherproof.fi/tieindeksi2/tulokset.php"}
         "Ilmatieteenlaitoksen palvelussa"]]]]]))

(defn urakkaparametrit []
  [tuck tiedot/tila nakyma*])
