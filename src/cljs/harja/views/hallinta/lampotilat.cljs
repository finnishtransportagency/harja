(ns harja.views.hallinta.lampotilat
  "Lämpötilojen näkymä. Täällä jvh pitää yllä hoidon alueurakoiden lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :refer [tee-kentta]]

            [harja.tiedot.urakka.suola :as tiedot]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce lampotilarivit (reaction @tiedot/hoitourakoiden-lampotilat))

(defn lampotilat
  "Lämpötilojen pääkomponentti"
  []
    (komp/luo
     (komp/lippu tiedot/lampotilojen-hallinnassa?)
     (fn []
         (let [valitun-kauden-alkuvuosi (pvm/vuosi (first @tiedot/valittu-hoitokausi))
              valittu-talvikausi (str valitun-kauden-alkuvuosi
                                      "-"
                                      (pvm/vuosi (second @tiedot/valittu-hoitokausi)))
              tiedot-muuttuneet? (not= @lampotilarivit @tiedot/hoitourakoiden-lampotilat)]
          [:span.lampotilat
           [valinnat/kontekstin-hoitokaudet tiedot/hoitokaudet tiedot/valittu-hoitokausi tiedot/valitse-hoitokausi!]
           [grid/muokkaus-grid
            {:otsikko           "Teiden hoitourakoiden sydäntalven keskilämpötilat"
             :voi-muokata?      (constantly true)
             :voi-poistaa?      (constantly false)
             :piilota-toiminnot true
             :voi-lisata?       false
             :tyhja             "Ei lämpötiloja"
             :jarjesta          :nimi
             :tunniste          :urakka}

            [{:otsikko     "Urakka" :nimi :nimi :leveys "40%"
              :muokattava? (constantly false)
              :tyyppi      :string}
             {:otsikko           (str "Talvikausi " valittu-talvikausi " (\u2103)") :nimi :keskilampotila :leveys "30%"
              :desimaalien-maara 1
              :validoi [[:lampotila]]
              :muokattava?       (constantly true)
              :tyyppi            :numero}
             {:otsikko           "Vertailujakso 1981-2010 (\u2103)" :nimi :pitkakeskilampotila :leveys "30%"
              :desimaalien-maara 1
              :validoi [[:lampotila]]
              :muokattava?       (constantly true)
              :tyyppi            :numero}]

            lampotilarivit]

           [napit/palvelinkutsu-nappi
            "Hae ilmatieteenlaitokselta"
            #(tiedot/hae-lampotilat-ilmatieteenlaitokselta valitun-kauden-alkuvuosi)
            {:luokka       "nappi-toissijainen"
             :title "Tuodut lämpötilat lisätään taulukkoon, tarkastettuasi arvot voit tallentaa ne Harjaan."
             :disabled     (< valitun-kauden-alkuvuosi 2011)
             :ikoni        (ikonit/download)
             :virheviesti  "Lämpötilojen haku epäonnistui. Yritä myöhemmin uudelleen."
             :kun-onnistuu (fn [urakat]
                             (reset! lampotilarivit (merge-with merge @lampotilarivit urakat))
                             (viesti/nayta! "Lämpötilat haettu ja päivitetty taulukkoon - tarkista tiedot ja tallenna." :success 5000))}]

           [napit/palvelinkutsu-nappi
            "Tallenna"
            #(tiedot/tallenna-teiden-hoitourakoiden-lampotilat @tiedot/valittu-hoitokausi @lampotilarivit)
            {:luokka       "nappi-ensisijainen pull-right"
             :disabled     (not tiedot-muuttuneet?)
             :ikoni        (ikonit/tallenna)
             :kun-onnistuu (fn [vastaus]
                             (viesti/nayta! "Lämpötilat tallennettu." :success)
                             (reset! tiedot/hoitourakoiden-lampotilat vastaus)
                             (log "Lämpötilat tallennettu, vastaus: " (pr-str vastaus)))}]

           ; tieindeksi2-tilastoinnin-alkuvuosi 2006 mutta API palauttaa lämpötiloja vasta 2011 alkaen
           (when (< valitun-kauden-alkuvuosi 2011)
             (yleiset/vihje "Ilmatieteenlaitokselta saa tietoja hoitokaudesta 2011-2012 eteenpäin"))
            [:div.ilmatieteenlaitos-linkki
             [:span "Voit myös katsella lämpötiloja "]
             [:a {:href "http://weather.weatherproof.fi/tieindeksi2/index.php?"}
              "Ilmatieteenlaitoksen palvelussa"]]]))))

