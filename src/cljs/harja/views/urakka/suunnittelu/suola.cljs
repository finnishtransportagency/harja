(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka.toteumat.suola :as suola]
            [cljs.core.async :refer [<!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.indeksit :as i]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta :as kartta]
            [harja.fmt :as fmt]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(defonce suolasakot-nakyvissa? (atom false))

(defonce suolasakot-ja-lampotilat
  (reaction<! [ur @nav/valittu-urakka
               nakymassa? @suolasakot-nakyvissa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and ur nakymassa?)
                (suola/hae-urakan-suolasakot-ja-lampotilat (:id ur)))))

(defonce suolasakko-kaytossa?
  (reaction (let [ss (:suolasakot @suolasakot-ja-lampotilat)]
              (or (empty? ss)
                  (some :kaytossa ss)))))

;; Suolasakko on urakkakohtainen ja samat tiedot jokaiselle hoitokaudelle.
;; Käytetään selkeyden vuoksi ensimmäistä hoitokautta tietojen näyttämiseen ja muokkaamiseen
(def urakan-ensimmainen-hoitokausi
  (reaction (first @u/valitun-urakan-hoitokaudet)))

(defn yhden-hoitokauden-rivit [rivit]
  (when-let [eka-hk @urakan-ensimmainen-hoitokausi]
    (filter #(= (:hoitokauden_alkuvuosi %) (pvm/vuosi (first eka-hk))) rivit)))

(defonce syotettavat-tiedot
         (reaction (let [ss @suolasakot-ja-lampotilat]
                     {:suolasakko (first (yhden-hoitokauden-rivit (:suolasakot ss)))
               :pohjavesialue-talvisuola (vec (yhden-hoitokauden-rivit (:pohjavesialue-talvisuola ss)))})))

(defonce pohjavesialueet
  (reaction (let [ss @suolasakot-ja-lampotilat]
              (:pohjavesialueet ss))))

(defonce lampotilat
  (reaction (:lampotilat @suolasakot-ja-lampotilat)))

(defn tallenna-suolasakko
  []
  (k/post! :tallenna-suolasakko-ja-pohjavesialueet
           (assoc @syotettavat-tiedot
             :urakka (:id @nav/valittu-urakka)
             :hoitokaudet @u/valitun-urakan-hoitokaudet)))



(defn pohjavesialueet-muokkausdata []
  (let [pohjavesialueet @pohjavesialueet
        pv-rajat (into {}
                       (map (juxt :pohjavesialue identity))
                       (:pohjavesialue-talvisuola @syotettavat-tiedot))]
      (wrap (into {}
              (map (fn [pohjavesialue]
                     [(:tunnus pohjavesialue)
                      (assoc pohjavesialue
                             :talvisuolaraja (:talvisuolaraja (get pv-rajat (:tunnus pohjavesialue))))]))
              pohjavesialueet)
            #(swap! syotettavat-tiedot update-in [:pohjavesialue-talvisuola]
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
                                          {:hoitokauden_alkuvuosi (pvm/vuosi (first @urakan-ensimmainen-hoitokausi))
                                           :pohjavesialue tunnus
                                           :talvisuolaraja (:talvisuolaraja (get % tunnus))}))))
                              (vec pohjavesialue-talvisuola)
                              (keys %)))))))

(defn hae-pohjavesialueen-nimi [p]
  (let [nimi (:nimi p)]
    (if (empty? nimi)
      "Nimi ei saatavilla aineistosta"
      nimi)))

(defn suolasakko-lomake
  []
  (let [urakka @nav/valittu-urakka
        saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))
        pohjavesialueita-muokattu? (atom false)]

    (fn []
      (let [{:keys [pohjavesialueet]} @suolasakot-ja-lampotilat
            tiedot (:suolasakko @syotettavat-tiedot)
            lampotilat @lampotilat]
        [:span.suolasakkolomake
         [:h5 "Urakan suolasakkotiedot hoitokautta kohden"]
         [lomake {:muokkaa! (fn [uusi]
                              (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                              (swap! syotettavat-tiedot assoc :suolasakko uusi :muokattu true))
                  :footer   [:span.lampotilalomake-footer
                             (if saa-muokata?
                               [napit/palvelinkutsu-nappi
                                "Tallenna"
                                #(tallenna-suolasakko)
                                {:luokka       "nappi-ensisijainen"
                                 :disabled     (and (not (lomake/voi-tallentaa? tiedot))
                                                    (not @pohjavesialueita-muokattu?))
                                 :ikoni        (ikonit/tallenna)
                                 :kun-onnistuu #(do
                                                 (viesti/nayta! "Tallentaminen onnistui" :success 1500)
                                                 (reset! pohjavesialueita-muokattu? false)
                                                 (reset! suolasakot-ja-lampotilat %))}])]}
          [{:otsikko "Talvisuolan käyttöraja" :pakollinen? true
            :muokattava? (constantly saa-muokata?)
            :nimi :talvisuolaraja
            :tyyppi :positiivinen-numero :palstoja 1
            :yksikko "kuivatonnia" :placeholder "Ei rajoitusta"}
           {:otsikko "Suolasakko" :pakollinen? true
            :muokattava? (constantly saa-muokata?) :nimi :maara
            :tyyppi :positiivinen-numero :palstoja 1 :yksikko "€ / ylittävä tonni"}
           {:otsikko       "Maksukuukausi" :nimi :maksukuukausi :tyyppi :valinta :palstoja 1
            :valinta-arvo  first :pakollinen? true
            :muokattava?   (constantly saa-muokata?)
            :valinta-nayta #(if (not saa-muokata?)
                             ""
                             (if (nil? %) yleiset/+valitse-kuukausi+ (second %)))
            :valinnat      [[5 "Toukokuu"] [6 "Kesäkuu"] [7 "Heinäkuu"]
                            [8 "Elokuu"] [9 "Syyskuu"]]}
           {:otsikko       "Indeksi" :nimi :indeksi :tyyppi :valinta
            :muokattava?   (constantly saa-muokata?)
            :valinta-nayta #(if (not saa-muokata?)
                             ""
                             (if (nil? %) "Ei indeksiä" (str %)))
            :valinnat      (conj @i/indeksien-nimet nil)

            :palstoja 1}

           (when-not (empty? pohjavesialueet)
             {:otsikko     "Pohjavesialueiden käyttörajat"
              :nimi        :pohjavesialueet :palstoja 2 :tyyppi :komponentti
              :komponentti [grid/muokkaus-grid {:piilota-toiminnot? true
                                                :voi-poistaa?       (constantly false)
                                                :voi-lisata?        false
                                                :jos-tyhja          "Urakan alueella ei pohjavesialueita"}
                            [{:otsikko "Pohjavesialue" :muokattava? (constantly false) :leveys "40%"
                              :hae #(hae-pohjavesialueen-nimi %)}
                             {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :leveys "23%"}
                             {:otsikko     "Käyttöraja" :nimi :talvisuolaraja :tyyppi :positiivinen-numero
                              :aseta       (fn [rivi arvo]
                                             (reset! pohjavesialueita-muokattu? true)
                                             (assoc rivi :talvisuolaraja arvo))
                              :placeholder "Ei rajoitusta" :leveys "30%" :muokattava? (constantly saa-muokata?)}]
                            (pohjavesialueet-muokkausdata)]})]
          tiedot]

         [grid/grid
          {:otsikko "Sydäntalven keskilämpötilat urakan aikana"}
          [{:otsikko "Hoitokausi" :nimi :hoitokausi
            :hae #(str (pvm/vuosi (:alkupvm %)) "-" (pvm/vuosi (:loppupvm %)))
            :leveys 3}
           {:otsikko "Keski\u00ADlämpötila" :nimi :keskilampotila :fmt #(if % (fmt/asteina %) "-")
            :tasaa   :oikea :leveys 2}
           {:otsikko "Pitkän aikavälin ka" :nimi :pitkakeskilampotila :fmt #(if % (fmt/asteina %) "-")
            :tasaa   :oikea :leveys 2}
           {:otsikko      "Erotus" :nimi :erotus :fmt #(if % (fmt/asteina %) "-")
            :hae          #(.toFixed (- (:keskilampotila %) (:pitkakeskilampotila %)) 1)
            :tyyppi       :string
            :tasaa   :oikea :leveys 2}]
          lampotilat]
         [yleiset/vihje "Järjestelmän vastuuhenkilö tuo lämpötilatiedot Harjaan"]]))))

(defn suola []
  (komp/luo
    (komp/lippu suolasakot-nakyvissa? pohjavesialueet/karttataso-pohjavesialueet)
    (komp/sisaan #(do
                   (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                   (nav/vaihda-kartan-koko! :M)))
    (fn []
      (let [ur @nav/valittu-urakka
            kaytossa? @suolasakko-kaytossa?]
        [:span.suolasakot
         [kartta/kartan-paikka]
         [yleiset/raksiboksi "Suolasakko käytössä" kaytossa?
          #(go (reset! suolasakko-kaytossa?
                       (<! (suola/aseta-suolasakon-kaytto (:id ur)
                                                          (not kaytossa?)))))
          nil false]
         (when @suolasakko-kaytossa?
           [suolasakko-lomake])]))))
