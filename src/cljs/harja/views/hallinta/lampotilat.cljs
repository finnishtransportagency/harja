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

            [harja.tiedot.urakka.toteumat.suola :as tiedot]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(def sivu "Lämpötilat")
(defonce lampotilarivit (reaction-writable @tiedot/hoitourakoiden-lampotilat))

(defonce taulukon-virheet (atom nil))

(defn yhdista-lampotilat [vanha uusi]
  (assoc vanha
         :keskilampotila (or (:keskilampotila uusi) (:keskilampotila vanha))
         :pitkakeskilampotila (or (:pitkakeskilampotila uusi) (:pitkakeskilampotila vanha))
         :pitkakeskilampotila_vanha (or (:pitkakeskilampotila_vanha uusi)
                                        (:pitkakeskilampotila_vanha vanha))))

(defn lampotilat
  "Lämpötilojen pääkomponentti"
  []
  (komp/luo
    (komp/kirjaa-kaytto! sivu)
    (komp/lippu tiedot/lampotilojen-hallinnassa?)
    (fn []
      (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/hallinta-lampotilat)
            valitun-kauden-alkuvuosi (pvm/vuosi (first @tiedot/valittu-hoitokausi))
            valittu-talvikausi (str valitun-kauden-alkuvuosi
                                    "-"
                                    (pvm/vuosi (second @tiedot/valittu-hoitokausi)))
            tiedot-muuttuneet? (not= @lampotilarivit @tiedot/hoitourakoiden-lampotilat)]
        [:div.row
         [:span.lampotilat.col-md-6
          [valinnat/hoitokausi tiedot/hoitokaudet tiedot/valittu-hoitokausi tiedot/valitse-hoitokausi!]
          [grid/muokkaus-grid
           {:otsikko           "Teiden hoitourakoiden sydäntalven keskilämpötilat"
            :voi-muokata?      voi-muokata?
            :voi-poistaa?      (constantly false)
            :piilota-toiminnot? true
            :voi-lisata?       false
            :tyhja             "Ei lämpötiloja"
            :jarjesta          :nimi
            :virheet           taulukon-virheet
            :tunniste          :urakka}

           [{:otsikko     "Urakka" :nimi :nimi :leveys 3
             :muokattava? (constantly false)
             :tyyppi      :string}
            {:otsikko           (str "Talvi\u00ADkausi " valittu-talvikausi " (\u2103)")
             :nimi :keskilampotila :leveys 1
             :desimaalien-maara 1
             :validoi           [[:lampotila]]
             :muokattava?       (constantly true)
             :tyyppi            :numero}
            {:otsikko           "Vertailu\u00ADjakso 1981-2010 (\u2103)"
             :nimi :pitkakeskilampotila :leveys 1
             :desimaalien-maara 1
             :validoi           [[:lampotila]]
             :muokattava?       (constantly true)
             :tyyppi            :numero}
            {:otsikko           "Vertailu\u00ADjakso 1971-2000 (\u2103)"
             :nimi :pitkakeskilampotila_vanha :leveys 1
             :desimaalien-maara 1
             :validoi           [[:lampotila]]
             :muokattava?       (constantly true)
             :tyyppi            :numero}]

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
           {:luokka       "nappi-ensisijainen pull-right"
            :disabled     (not (and tiedot-muuttuneet? (empty? @taulukon-virheet)))
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
            "Ilmatieteenlaitoksen palvelussa"]]]]))))
