(ns harja.views.hallinta.lampotilat
  "Lämpötilojen näkymä. Täällä jvh pitää yllä hoidon alueurakoiden lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.grid :as grid]
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

(defn lampotilat
  "Lämpötilojen pääkomponentti"
  []
    (komp/luo
      (komp/lippu tiedot/lampotilojen-hallinnassa?)
      (fn []
        (let [lampotilarivit tiedot/hoitourakoiden-lampotilat
              valitun-kauden-alkuvuosi (pvm/vuosi (first @tiedot/valittu-hoitokausi))
              valittu-talvikausi (str (pvm/vuosi (first @tiedot/valittu-hoitokausi))
                                      "-"
                                      (pvm/vuosi (second @tiedot/valittu-hoitokausi)))
              voi-tallentaa? (atom true)] ;fixme: voi tallentaa jos on muutoksia eikä ole virheitä

          [:span.lampotilat
           [valinnat/kontekstin-hoitokaudet tiedot/hoitokaudet tiedot/valittu-hoitokausi tiedot/valitse-hoitokausi!]
          [grid/muokkaus-grid
           {:otsikko      "Teiden hoitourakoiden sydäntalven keskilämpötilat"
            :voi-muokata? (constantly true)
            :voi-poistaa? (constantly false)
            :piilota-toiminnot true
            :voi-lisata?  false
            :tyhja        "Ei lämpötiloja"
            :jarjesta     :nimi
            :tunniste     :urakka}

           [{:otsikko     "Urakka" :nimi :nimi :leveys "40%"
             :muokattava? (constantly false)
             :tyyppi      :string}
            {:otsikko     (str "Talvikausi " valittu-talvikausi " (\u2103)") :nimi :keskilampotila :leveys "30%"
             :desimaalien-maara 1
             :muokattava? (constantly true)
             :tyyppi      :numero}
            {:otsikko     "Vertailujakso 1981-2010 (\u2103)" :nimi :pitkakeskilampotila :leveys "30%"
             :desimaalien-maara 1
             :muokattava? (constantly true)
             :tyyppi      :numero}]

           lampotilarivit]

           [harja.ui.napit/palvelinkutsu-nappi
            "Hae ilmatieteenlaitokselta"
            #(tiedot/hae-lampotilat-ilmatieteenlaitokselta valitun-kauden-alkuvuosi)
            {:luokka       "nappi-toissijainen"
             :disabled      (< valitun-kauden-alkuvuosi 2011)
             :ikoni        (ikonit/download)
             :virheviesti   "Lämpötilojen haku epäonnistui. Yritä myöhemmin uudelleen."
             :kun-onnistuu (fn [urakat]
                             (reset! lampotilarivit (merge-with merge @lampotilarivit urakat))
                             (viesti/nayta! "Lämpötilat haettu ja päivitetty taulukkoon - tarkista tiedot ja tallenna." :success 5000))}]

           [harja.ui.napit/palvelinkutsu-nappi
            "Tallenna"
            #(let [rivit @lampotilarivit
                   _ (log (pr-str rivit))]
              (tiedot/tallenna-lampotilat (pvm/vuosi (first @tiedot/valittu-hoitokausi)) rivit))
            {:luokka       "nappi-ensisijainen pull-right"
             :disabled     (false? @voi-tallentaa?)
             :ikoni (ikonit/tallenna)
             :kun-onnistuu (fn [vastaus]
                             (viesti/nayta! "Lämpötilat tallennettu." :success)
                             (log "Lämpötilat tallennettu, vastaus: " (pr-str vastaus)))}]

           ; tieindeksi2-tilastoinnin-alkuvuosi 2006 mutta API palauttaa lämpötiloja vasta 2011 alkaen
           (when (< valitun-kauden-alkuvuosi 2011)
             (yleiset/vihje "Ilmatieteenlaitokselta saa tietoja hoitokaudesta 2011-2012 eteenpäin"))]))))

