(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.suola :as suola]
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
                       (suola/hae-urakan-suolasakot-ja-lampotilat (:id ur)))))

(defonce suolasakko-kaytossa?
  (reaction (let [ss (:suolasakot @suolasakot-ja-lampotilat)]
              (or (empty? ss)
                  (some :kaytossa ss)))))

(defn valitun-hoitokauden-rivit [rivit]
  (let [vuosi (pvm/vuosi (first @u/valittu-hoitokausi))]
    (filter #(= (:hoitokauden_alkuvuosi %) vuosi) rivit)))

(defonce hoitokauden-tiedot
  (reaction (let [ss @suolasakot-ja-lampotilat]
              {:suolasakko (first (valitun-hoitokauden-rivit (:suolasakot ss)))
               :pohjavesialue-talvisuola (vec (valitun-hoitokauden-rivit (:pohjavesialue-talvisuola ss)))})))

(defonce pohjavesialueet
  (reaction (let [ss @suolasakot-ja-lampotilat]
              (:pohjavesialueet ss))))

(defn tallenna-suolasakko
  []
  (k/post! :tallenna-suolasakko-ja-pohjavesialueet
           (assoc @hoitokauden-tiedot
                  :urakka (:id @nav/valittu-urakka)
                  :hoitokauden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi)))))



(defn pohjavesialueet-muokkausdata []
  (let [pohjavesialueet @pohjavesialueet
        pv-rajat (into {}
                       (map (juxt :pohjavesialue identity))
                       (:pohjavesialue-talvisuola @hoitokauden-tiedot))]
      (wrap (into {}
              (map (fn [pohjavesialue]
                     [(:tunnus pohjavesialue)
                      (assoc pohjavesialue
                             :talvisuolaraja (:talvisuolaraja (get pv-rajat (:tunnus pohjavesialue))))]))
              pohjavesialueet)
            #(swap! hoitokauden-tiedot update-in [:pohjavesialue-talvisuola]
                    (fn [pohjavesialue-talvisuola]
                      (reduce (fn [pohjavesialue-talvisuola tunnus]
                                (log "PV " tunnus)
                                (let [paivitettava (first (keep-indexed (fn [i pv-raja]
                                                                          (and (= tunnus (:pohjavesialue pv-raja) )
                                                                               i))
                                                                        pohjavesialue-talvisuola))]
                                  
                                  (log "PV paivitettava " paivitettava)
                                  (if paivitettava
                                    ;; olemassaoleva raja, päivitä sen arvo
                                    (update-in pohjavesialue-talvisuola [paivitettava] 
                                               (fn [pv-raja]
                                                 (assoc pv-raja
                                                        :talvisuolaraja (:talvisuolaraja (get % tunnus)))))
                                    ;; tälle alueelle ei olemassaolevaa rajaa, lisätään uusi rivi
                                    (conj pohjavesialue-talvisuola
                                          {:hoitokauden_alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
                                           :pohjavesialue tunnus
                                           :talvisuolaraja (:talvisuolaraja (get % tunnus))}))))
                              (vec pohjavesialue-talvisuola)
                              (keys %)))))))

(defn lampotila-lomake
  []
  (let [urakka @nav/valittu-urakka
        saa-muokata? (roolit/rooli-urakassa? roolit/urakanvalvoja
                                             (:id urakka))]

    (fn []
      (let [hoitokausi @u/valittu-hoitokausi
            {:keys [pohjavesialueet pohjavesialue-talvisuola]} @suolasakot-ja-lampotilat]
        [:span.suolasakkolomake
         [lomake {:luokka    :horizontal
                  :muokkaa!  (fn [uusi]
                               (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                               (swap! hoitokauden-tiedot assoc :suolasakko uusi :muokattu true))
                  :footer-fn (fn [virheet _]
                               (log "virheet: " (pr-str virheet) ", muokattu? " (:muokattu @hoitokauden-tiedot))
                               [:span.lampotilalomake-footer
                                (if saa-muokata?
                                  [:div.form-group
                                   [:div.col-md-4
                                    [napit/palvelinkutsu-nappi
                                     "Tallenna"
                                     #(tallenna-suolasakko)
                                     {:luokka       "nappi-ensisijainen"
                                      :disabled (not (empty? virheet))
                                      :ikoni        (ikonit/tallenna)
                                      :kun-onnistuu #(do
                                                      (viesti/nayta! "Tallentaminen onnistui" :success 1500)
                                                      (reset! suolasakot-ja-lampotilat %))}]]])])}
          [
           {:otsikko "Talvisuolan käyttöraja" :pakollinen? true :nimi :talvisuolaraja :tyyppi :positiivinen-numero :leveys-col 2
            :yksikko "kuivatonnia" :placeholder "Ei rajoitusta"}

           (when-not (empty? pohjavesialueet)
             {:otsikko "Pohjavesialueiden käyttörajat"
              :nimi :pohjavesialueet :leveys-col 6
              :komponentti [grid/muokkaus-grid {:voi-poistaa? (constantly false)
                                                :voi-lisata? false
                                                :jos-tyhja "Urakan alueella ei pohjavesialueita"}
                            [{:otsikko "Pohjavesialue" :nimi :nimi :muokattava? (constantly false) :leveys "40%"}
                             {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :leveys "23%"}
                             {:otsikko "Käyttöraja" :nimi :talvisuolaraja :tyyppi :positiivinen-numero 
                              :placeholder "Ei rajoitusta" :leveys "30%"}]
                            (pohjavesialueet-muokkausdata)]})
           
           {:otsikko "Suolasakko" :pakollinen? true :nimi :maara :tyyppi :positiivinen-numero :leveys-col 2 :yksikko "€ / ylittävä tonni"}
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
                                 (:suolasakko @hoitokauden-tiedot)]
                             (log "HK: " (pr-str hk))
                             (assoc hk
                                    :id 1
                                    :erotus (and keskilampotila pitkakeskilampotila
                                                 (.toFixed (- keskilampotila pitkakeskilampotila)))))]]}
                 
           ]
          (:suolasakko @hoitokauden-tiedot)]
         ]))))


(defn suola []
  (komp/luo
    (komp/lippu suolasakot-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            kaytossa? @suolasakko-kaytossa?]
        [:span.suolasakot
         [yleiset/raksiboksi "Suolasakko käytössä" kaytossa?
          #(go (reset! suolasakko-kaytossa?
                       (<! (suola/aseta-suolasakon-kaytto (:id ur)
                                                          (not kaytossa?)))))
          nil false]
         (when @suolasakko-kaytossa?
           [:span
            [valinnat/urakan-hoitokausi ur]
            (when @u/valittu-hoitokausi
              [lampotila-lomake])])]))))
