(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.lampotilat :as lampotilat]
            [cljs.core.async :refer [<!]]
            [harja.ui.komponentti :as komp]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as oikeudet]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.roolit :as roolit]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.indeksit :as i]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta :as kartta]
            [harja.fmt :as fmt])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(defonce suolasakot-nakyvissa? (atom false))

(defonce suolasakot-ja-lampotilat
         (reaction<! [ur @nav/valittu-urakka
                      nakymassa? @suolasakot-nakyvissa?]
                     (when (and ur nakymassa?)
                       (lampotilat/hae-urakan-suolasakot-ja-lampotilat (:id ur)))))

(defn tallenna-suolasakko-ja-lampotilat
  [tiedot]
  (log "tallenna-suolasakko-ja-lampotilat" (pr-str tiedot))
  (let [ehostettu-data (assoc tiedot
                         :hoitokauden_alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
                         :lt_alkupvm (first @u/valittu-hoitokausi)
                         :lt_loppupvm (second @u/valittu-hoitokausi)
                         :urakka (:id @nav/valittu-urakka))]
    (k/post! :tallenna-suolasakko-ja-lampotilat ehostettu-data)))

(defn lampotila-lomake
  []
  (let [urakka @nav/valittu-urakka
        saa-muokata? (roolit/rooli-urakassa? roolit/urakanvalvoja
                                             (:id urakka))
        valitun-hoitokauden-tiedot (reaction (when @suolasakot-ja-lampotilat
                                               (first (filter #(or (= (:hoitokauden_alkuvuosi %) (pvm/vuosi (first @u/valittu-hoitokausi)))
                                                                   (= (:lt_alkupvm %) (first @u/valittu-hoitokausi)))
                                                              @suolasakot-ja-lampotilat))))
        lampotilaerotus (reaction )]

    (fn []
      (let [hoitokausi @u/valittu-hoitokausi]
        [:span.suolasakkolomake
         [lomake {:luokka    :horizontal
                  :muokkaa!  (fn [uusi]
                               (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                               (reset! valitun-hoitokauden-tiedot (assoc uusi :muokattu true)))
                  :footer-fn (fn [virheet _]
                               (log "virheet: " (pr-str virheet) ", muokattu? " (:muokattu @valitun-hoitokauden-tiedot))
                               [:span.lampotilalomake-footer
                                (if saa-muokata?
                                  [:div.form-group
                                   [:div.col-md-4
                                    [napit/palvelinkutsu-nappi
                                     "Tallenna"
                                     #(tallenna-suolasakko-ja-lampotilat
                                       @valitun-hoitokauden-tiedot)
                                     {:luokka       "nappi-ensisijainen"
                                      :disabled     (or (not= true (:muokattu @valitun-hoitokauden-tiedot))
                                                        (not (empty? virheet)))
                                      :ikoni        (ikonit/tallenna)
                                      :kun-onnistuu #(do
                                                      (viesti/nayta! "Tallentaminen onnistui" :success 1500)
                                                      (reset! suolasakot-ja-lampotilat %))}]]])])}
          [{:otsikko "Suolasakko" :pakollinen? true :nimi :maara :tyyppi :positiivinen-numero :leveys-col 2 :yksikko "€ / ylittävä tonni"}
           {:otsikko       "Maksukuukausi" :nimi :maksukuukausi :tyyppi :valinta :leveys-col 2
            :valinta-arvo  first
            :valinta-nayta #(if (nil? %) yleiset/+valitse-kuukausi+ (second %))
            :valinnat      [[5 "Toukokuu"] [6 "Kesäkuu"] [7 "Heinäkuu"]
                            [8 "Elokuu"] [9 "Syyskuu"]]}
           {:otsikko       "Indeksi" :nimi :indeksi :tyyppi :valinta
            :valinta-nayta #(if (nil? %) yleiset/+valitse-indeksi+ (str %))
            :valinnat      (conj @i/indeksien-nimet yleiset/+ei-sidota-indeksiin+)

            :leveys-col    2}

           {:otsikko "Sydäntalven keskilämpötila" :leveys-col 4
            :nimi :lampotilat
            :komponentti [grid/grid {}
                          [{:otsikko "Tämä talvikausi" :nimi :keskilampotila :fmt #(if % (fmt/asteina %) "-")
                            :tasaa :oikea}
                           {:otsikko "Pitkä aikaväli" :nimi :pitkakeskilampotila :fmt #(if % (fmt/asteina %) "-")
                            :tasaa :oikea}
                           {:otsikko "Erotus" :nimi :erotus :fmt #(if % (fmt/asteina %) "-")
                            :tasaa :oikea}]

                          [(let [{:keys [keskilampotila pitkakeskilampotila] :as hk}
                                 @valitun-hoitokauden-tiedot]
                             (log "HK: " (pr-str hk))
                             (assoc hk
                                    :erotus (and keskilampotila pitkakeskilampotila
                                                 (.toFixed (- keskilampotila pitkakeskilampotila)))))]]}
                 
           ]
          @valitun-hoitokauden-tiedot]
         ]))))

(defn suola []
  (komp/luo
    (komp/lippu suolasakot-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka]
        [:span.suolasakot
         [valinnat/urakan-hoitokausi ur]
         (when @u/valittu-hoitokausi
           [lampotila-lomake])]))))
