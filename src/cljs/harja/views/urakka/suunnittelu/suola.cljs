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
            [harja.tiedot.hallinta.indeksit :as i]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta :as kartta]
            [harja.fmt :as fmt]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce suolasakot-nakyvissa? (atom false))

(defonce suolasakot-ja-lampotilat
  (reaction<! [ur @nav/valittu-urakka
               nakymassa? @suolasakot-nakyvissa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and ur nakymassa?)
                (suola/hae-urakan-suolasakot-ja-lampotilat (:id ur)))))

(defonce suolasakko-kaytossa?
  (reaction-writable
   (let [ss (:suolasakot @suolasakot-ja-lampotilat)]
     (or (empty? ss)
         (some :kaytossa ss)))))

;; Suolasakko on urakkakohtainen ja samat tiedot pitää tallentaa jokaiselle hoitokaudelle.
;; Näytetään vanhin hoitokauden tiedot
(defn yhden-hoitokauden-rivit [rivit]
  (when-not (empty? rivit)
    [(first (sort-by :hoitokauden-alkuvuosi rivit))]))

(defonce syotettavat-tiedot
  (reaction-writable
   (let [ss @suolasakot-ja-lampotilat]
     {:suolasakko (first (yhden-hoitokauden-rivit (:suolasakot ss)))
      :pohjavesialue-talvisuola (vec (yhden-hoitokauden-rivit (:pohjavesialue-talvisuola ss)))})))

(defonce pohjavesialueet
  (reaction-writable
   (let [ss @suolasakot-ja-lampotilat]
     (:pohjavesialueet ss))))

(defonce lampotilat
  (reaction-writable
   (:lampotilat @suolasakot-ja-lampotilat)))

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
                                                                        (and (= tunnus (:pohjavesialue pv-raja))
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
                                        {:hoitokauden_alkuvuosi (pvm/vuosi (first (first @u/valitun-urakan-hoitokaudet)))
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
        pohjavesialueita-muokattu? (atom false)
        tarkista-sakko-ja-bonus (fn [_ {maara :maara vainsakkomaara :vainsakkomaara}]
                                  (when (and (number? vainsakkomaara)
                                             (number? maara)
                                             (not= vainsakkomaara maara)
                                             (not= 0 vainsakkomaara)
                                             (not= 0 maara))
                                    "Sakko/bonus ja sakko eivät saa olla eri arvoja. Käytetään Sakko/bonus arvoa."))]

    (fn []
      (let [{:keys [pohjavesialueet]} @suolasakot-ja-lampotilat
            tiedot (:suolasakko @syotettavat-tiedot)
            lampotilat @lampotilat
            valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))] ;suolasakot hoitospesifistä
        [:span.suolasakkolomake
         [:h5 "Urakan suolasakkotiedot hoitokautta kohden"]
         [lomake {:muokkaa! (fn [uusi]
                              (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                              (swap! syotettavat-tiedot assoc :suolasakko uusi :muokattu true))
                  :footer [:span.lampotilalomake-footer
                           (if saa-muokata?
                             [napit/palvelinkutsu-nappi
                              "Tallenna"
                              #(tallenna-suolasakko)
                              {:luokka "nappi-ensisijainen"
                               :disabled (and (not (lomake/voi-tallentaa? tiedot))
                                              (not @pohjavesialueita-muokattu?))
                               :ikoni (ikonit/tallenna)
                               :kun-onnistuu #(do
                                               (viesti/nayta! "Tallentaminen onnistui" :success viesti/viestin-nayttoaika-lyhyt)
                                               (reset! pohjavesialueita-muokattu? false)
                                               (reset! suolasakot-ja-lampotilat %))}])]}
          [{:otsikko "Talvisuolan käyttöraja" :pakollinen? true
            :muokattava? (constantly saa-muokata?)
            :nimi :talvisuolaraja
            :tyyppi :positiivinen-numero :palstoja 1
            :yksikko "kuivatonnia" :placeholder "Ei rajoitusta"}
           {:otsikko "Maksukuukausi" :nimi :maksukuukausi :tyyppi :valinta :palstoja 1
            :valinta-arvo first :pakollinen? true
            :muokattava? (constantly saa-muokata?)
            :valinta-nayta #(if (not saa-muokata?)
                             ""
                             (if (nil? %) yleiset/+valitse-kuukausi+ (second %)))
            :valinnat [[5 "Toukokuu"] [6 "Kesäkuu"] [7 "Heinäkuu"]
                       [8 "Elokuu"] [9 "Syyskuu"]]}

           {:otsikko     "Suola\u00ADsakko/bonus"
            :muokattava? (constantly saa-muokata?) :nimi :maara
            :tyyppi      :positiivinen-numero :palstoja 1 :yksikko "€ / ylittävä tonni"
            :varoita     [tarkista-sakko-ja-bonus]
            :vihje       "Jos urakassa käytössä sekä suolasakko että -bonus, täytä vain tämä"}
           {:otsikko     "Vain suola\u00ADsakko"
            :muokattava? (constantly saa-muokata?) :nimi :vainsakkomaara
            :tyyppi      :positiivinen-numero :palstoja 1 :yksikko "€ / ylittävä tonni"
            :varoita     [tarkista-sakko-ja-bonus]
            :vihje       "Jos urakassa käytössä vain suolasakko eikä bonusta, täytä vain tämä"}

           (when (urakka/indeksi-kaytossa?)
             {:otsikko "Indeksi" :nimi :indeksi :tyyppi :valinta
              :muokattava? (constantly saa-muokata?)
              :valinta-nayta #(if (not saa-muokata?)
                                ""
                                (if (nil? %) "Ei indeksiä" (str %)))
              :valinnat (conj valittavat-indeksit nil)

              :palstoja 1})

           (when-not (empty? pohjavesialueet)
             {:otsikko "Pohjavesialueiden käyttörajat"
              :nimi :pohjavesialueet :palstoja 2 :tyyppi :komponentti
              :komponentti (fn [_]
                             [grid/muokkaus-grid {:piilota-toiminnot? true
                                                 :voi-poistaa? (constantly false)
                                                 :voi-lisata? false
                                                 :jos-tyhja "Urakan alueella ei pohjavesialueita"}
                             [{:otsikko "Pohjavesialue" :muokattava? (constantly false) :leveys "40%"
                               :hae #(hae-pohjavesialueen-nimi %)}
                              {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :leveys "23%"}
                              {:otsikko "Käyttöraja" :nimi :talvisuolaraja :tyyppi :positiivinen-numero
                               :aseta (fn [rivi arvo]
                                        (reset! pohjavesialueita-muokattu? true)
                                        (assoc rivi :talvisuolaraja arvo))
                               :placeholder "Ei rajoitusta" :leveys "30%" :muokattava? (constantly saa-muokata?)}]
                             (pohjavesialueet-muokkausdata)])})]
          tiedot]

         [grid/grid
          {:otsikko "Sydäntalven keskilämpötilat urakan aikana"}
          [{:otsikko "Hoitokausi" :nimi :hoitokausi
            :hae #(str (pvm/vuosi (:alkupvm %)) "-" (pvm/vuosi (:loppupvm %)))
            :leveys 3}
           {:otsikko "Keski\u00ADlämpötila" :nimi :keskilampotila :fmt #(if % (fmt/asteina %) "-")
            :tasaa :oikea :leveys 2}
           {:otsikko "Pitkän aikavälin ka" :nimi :pitkakeskilampotila :fmt #(if % (fmt/asteina %) "-")
            :tasaa :oikea :leveys 2}
           {:otsikko "Erotus" :nimi :erotus :fmt #(if % (fmt/asteina %) "-")
            :hae #(.toFixed (- (:keskilampotila %) (:pitkakeskilampotila %)) 1)
            :tyyppi :string
            :tasaa :oikea :leveys 2}]
          lampotilat]
         [yleiset/vihje "Järjestelmän vastuuhenkilö tuo lämpötilatiedot Harjaan"]]))))

(defn suola []
  (komp/luo
    (komp/lippu suolasakot-nakyvissa? pohjavesialueet/karttataso-pohjavesialueet)
    (komp/sisaan #(do
                   (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                   (nav/vaihda-kartan-koko! :M)))
    (fn []
      (let [urakka-id (:id @nav/valittu-urakka)
            kaytossa? @suolasakko-kaytossa?]
        [:span.suolasakot
         (if (nil? @suolasakot-ja-lampotilat)
           [ajax-loader "Ladataan..."]
           [:div
            [kartta/kartan-paikka]
            [yleiset/raksiboksi {:teksti "Suolasakko käytössä"
                                 :toiminto #(go (reset! suolasakko-kaytossa?
                                                        (<! (suola/aseta-suolasakon-kaytto urakka-id
                                                                                           (not kaytossa?)))))
                                 :disabled? (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola urakka-id))}
             kaytossa?]
            (when @suolasakko-kaytossa?
              [suolasakko-lomake])])]))))
