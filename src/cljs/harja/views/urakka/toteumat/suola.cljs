(ns harja.views.urakka.toteumat.suola
  "Suolankäytön toteumat hoidon alueurakoissa"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.toteumat.suola :as suola]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.pvm :as pvm]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.loki :refer [log logt]]
            [harja.atom :refer [paivita!]]
            [cljs.core.async :refer [<! >!]]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.toteumat.suola :as tiedot]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defn nayta-toteumat-kartalla [toteumat]
  (nav/vaihda-kartan-koko! :L)
  (tiedot/valitse-suolatoteumat toteumat))

(defn piilota-toteumat-kartalla [toteumat]
  (tiedot/poista-valituista-suolatoteumista toteumat))

(defn suolankayton-paivan-erittely [suolan-kaytto]
  [grid/grid
   {:otsikko "Päivän toteumat"
    :tyhja "Ei päivän toteumia"
    :tunniste :tid}
   [{:otsikko "Alkanut" :nimi :alkanut :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10}
    {:otsikko "Päättynyt" :nimi :paattynyt :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10}
    {:otsikko "Määrä" :nimi :maara :tyyppi :positiivinen-numero
     :fmt #(fmt/desimaaliluku-opt % 3) :desimaalien-maara 3 :leveys 10}
    {:otsikko ""
     :nimi :nayta-kartalla
     :tyyppi :komponentti
     :leveys 7
     :komponentti (fn [toteuma]
                    [:div
                     [(if (tiedot/valittu-suolatoteuma? toteuma)
                        :button.nappi-toissijainen.nappi-grid
                        :button.nappi-ensisijainen.nappi-grid)
                      {:on-click #(if (tiedot/valittu-suolatoteuma? toteuma)
                                    (piilota-toteumat-kartalla [toteuma])
                                    (nayta-toteumat-kartalla [toteuma]))}
                      (ikonit/ikoni-ja-teksti
                        (ikonit/map-marker)
                        (if (tiedot/valittu-suolatoteuma? toteuma)
                          "Piilota kartalta"
                          "Näytä kartalla"))]])}]
   suolan-kaytto])

(defn vetolaatikon-suolarivit [rivi urakka]
  (let [vetolaatikon-rivit (atom nil)]
    (go
      (reset! vetolaatikon-rivit
              (<! (k/post! :hae-suolatoteumien-tarkat-tiedot {:toteumaidt (:toteumaidt rivi)
                                                              :materiaali-id (get-in rivi [:materiaali :id])
                                                              :urakka-id (:id urakka)}))))
    (fn [rivi urakka]
      [suolankayton-paivan-erittely @vetolaatikon-rivit])))

(defonce tallennetaan (atom false))

(defn suolatoteumat-taulukko [muokattava? urakka sopimus-id listaus materiaali-nimet kaytetty-yhteensa]
  [:div.suolatoteumat
   [kartta/kartan-paikka]
   [:span.valinnat
    [urakka-valinnat/aikavali-nykypvm-taakse urakka
     tiedot/valittu-aikavali
     {:aikavalin-rajoitus [12 :kuukausi]}]
    [ui-valinnat/materiaali-valikko {:valittu-materiaali (:suola @tiedot/suodatin-valinnat)
                                     :otsikko "Suola"
                                     :valitse-fn #(swap! tiedot/suodatin-valinnat assoc :suola %)
                                     :lisaa-kaikki? true
                                     :materiaalit materiaali-nimet}]]

   [lomake/lomake
    {:otsikko "Hae suolatoteumia tieosoiteväliltä"
     :muokkaa! #(reset! tiedot/ui-lomakkeen-tila %)
     :footer-fn (fn [rivi]
                  [:div
                   [napit/yleinen-toissijainen "Hae"
                    (fn []
                                        ; aiheuta tiedot/toteumat -reaktio
                      (reset! tiedot/lomakkeen-tila {:tierekisteriosoite (:tierekisteriosoite @tiedot/ui-lomakkeen-tila)
                                                     :refresh (inc (:refresh @tiedot/lomakkeen-tila))}))
                    {:ikoni (ikonit/livicon-search)}]
                   [napit/yleinen-toissijainen "Tyhjennä tieosoiteväli"
                    (fn []
                      (reset! tiedot/lomakkeen-tila nil)
                      (reset! tiedot/ui-lomakkeen-tila nil))]])
     :ei-borderia? true}
    [{:nimi :tierekisteriosoite
      :otsikko "Tierekisteriosoite"
      :tyyppi :tierekisteriosoite
      :tyyli :rivitetty
      :sijainti (atom nil)
      :vaadi-vali? true}]
    @tiedot/ui-lomakkeen-tila]

   [grid/grid {:otsikko "Talvisuolan käyttö"
               :tunniste :rivinumero
               :tallenna (if (oikeudet/voi-kirjoittaa?
                              oikeudet/urakat-toteumat-suola
                              (:id @nav/valittu-urakka))
                           #(go (if-let [tulos (<! (suola/tallenna-toteumat (:id urakka) sopimus-id %))]
                                  (paivita! tiedot/toteumat)))
                           :ei-mahdollinen)
               :tallennus-ei-mahdollinen-tooltip (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-toteumat-suola)
               :tyhja (if (nil? @tiedot/toteumat)
                        [yleiset/ajax-loader "Suolatoteumia haetaan..."]
                        "Ei suolatoteumia valitulle aikavälille")
               :uusi-rivi #(assoc % :alkanut (pvm/nyt))
               :voi-poistaa? muokattava?
               :max-rivimaara 500
               :max-rivimaaran-ylitys-viesti "Yli 500 suolatoteumaa. Rajoita hakuehtoja."
               :vetolaatikot (into {}
                                   (map (juxt :rivinumero (fn [rivi]
                                                            [vetolaatikon-suolarivit rivi urakka])))
                                   @tiedot/toteumat)
               :piilota-toiminnot? true}
    [{:tyyppi :vetolaatikon-tila :leveys 3}
     {:otsikko "Suola\u00ADtyyppi" :nimi :materiaali :fmt :nimi :leveys 30 :muokattava? muokattava?
      :tyyppi :valinta
      :validoi [[:ei-tyhja "Valitse materiaali"]]
      :valinta-nayta #(or (:nimi %) "- valitse -")
      :valinnat @tiedot/materiaalit}
     {:otsikko "Pvm" :nimi :pvm :fmt pvm/pvm-opt :tyyppi :pvm :leveys 30 :muokattava? muokattava?
      :validoi [[:ei-tyhja "Anna päivämäärä"]]
      :huomauta [[:valitun-kkn-aikana-urakan-hoitokaudella]]}
     {:otsikko "Käytetty määrä (t)" :nimi :maara :fmt #(fmt/desimaaliluku-opt % 3)
      :tyyppi :positiivinen-numero :desimaalien-maara 3 :leveys 30 :muokattava? muokattava?
      :validoi [[:ei-tyhja "Anna määrä"]] :tasaa :oikea}
     {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys 60 :muokattava? muokattava?
      :hae #(if (muokattava? %)
              (:lisatieto %)
              (str (:lisatieto %) " (Koneellisesti raportoitu, toteumia: "
                   (:lukumaara %) ")"))}
     {:otsikko ""
      :nimi :nayta-kartalla
      :tyyppi :komponentti
      :leveys 40
      :komponentti (fn [rivi]
                     (let [toteumat (map (fn [tid]
                                           {:tid tid})
                                         (:toteumaidt rivi))
                           valittu? #(some (fn [toteuma] (tiedot/valittu-suolatoteuma? toteuma)) toteumat)]
                       (when (and (not (empty? toteumat))
                                  (:koneellinen rivi))
                         [:div
                          [(if (valittu?)
                             :button.nappi-toissijainen.nappi-grid
                             :button.nappi-ensisijainen.nappi-grid)
                           {:on-click #(if (valittu?)
                                         (piilota-toteumat-kartalla toteumat)
                                         (nayta-toteumat-kartalla toteumat))}
                           (ikonit/ikoni-ja-teksti
                            (ikonit/map-marker)
                            (if (valittu?)
                              "Piilota kartalta"
                              "Näytä kartalla"))]])))}]
    listaus]
   (if-not (empty? @tiedot/toteumat)
     [:div.bold kaytetty-yhteensa]
     [:div ""])])

(defn pohjavesialueen-suola []
  (komp/luo
   (komp/sisaan
    (fn []
      (let [urakkaid @nav/valittu-urakka-id]
        (go
          (reset! tiedot/pohjavesialueen-toteuma nil)
          (reset! tiedot/urakan-pohjavesialueet (<! (tiedot/hae-urakan-pohjavesialueet urakkaid)))))))
   (fn []
     (let [alueet @tiedot/urakan-pohjavesialueet
           urakka @nav/valittu-urakka]
       [:div
        [urakka-valinnat/aikavali-nykypvm-taakse urakka
         tiedot/valittu-aikavali
         {:aikavalin-rajoitus [12 :kuukausi]}]
        [grid/grid {:otsikko "Urakan pohjavesialueet"
                    :tunniste :tunnus
                    :mahdollista-rivin-valinta? true
                    :rivi-valinta-peruttu (fn [rivi]
                                            (reset! tiedot/pohjavesialueen-toteuma nil))
                    :rivi-klikattu
                    (fn [rivi]
                      (go
                        (reset! tiedot/pohjavesialueen-toteuma
                                (<! (tiedot/hae-pohjavesialueen-suolatoteuma (:tunnus rivi) @tiedot/valittu-aikavali)))))
                         
                    :tyhjä (if (nil? @tiedot/urakan-pohjavesialueet)
                             [yleiset/ajax-loader "Pohjavesialueita haetaan..."]
                             "Ei pohjavesialueita")}
         [{:otsikko "Tunnus" :nimi :tunnus :leveys 10}
          {:otsikko "Nimi" :nimi :nimi}]
         alueet]
        (let [toteuma @tiedot/pohjavesialueen-toteuma]
          (when toteuma
            [grid/grid
             {:otsikko "Pohjavesialueen suolatoteuma"
              :tunniste :maara_t_per_km
              :piilota-toiminnot? true
              :tyhja (if (empty? toteuma)
                       "Ei tietoja")
              }
             [{:otsikko "Pohjavesialueen pituus (km)"
               :nimi :pituus
               :fmt #(fmt/desimaaliluku-opt % 1)
               :leveys 10}
              {:otsikko "Määrä t/km"
               :nimi :maara_t_per_km
               :fmt #(fmt/desimaaliluku-opt % 1)
               :leveys 10}
              {:otsikko "Määrä yhteensä"
               :leveys 10
               :fmt #(fmt/desimaaliluku-opt % 1)
               :nimi :yhteensa}
              {:otsikko "Käyttöraja"
               :leveys 10
               :nimi :kayttoraja}]
             toteuma]))]))))

(defn suolatoteumat []
  (komp/luo
    (komp/lippu tiedot/suolatoteumissa?
                pohjavesialueet/karttataso-pohjavesialueet
                tiedot-urakka/aseta-kuluva-kk-jos-hoitokaudella?
                tiedot/karttataso-suolatoteumat)
    (fn []
      (let [urakka @nav/valittu-urakka
            [sopimus-id _] @tiedot-urakka/valittu-sopimusnumero
            muokattava? (comp not true? :koneellinen)
            listaus (filter (fn [{{nimi :nimi} :materiaali}]
                              (or (= (:suola @tiedot/suodatin-valinnat) "Kaikki")
                                  (= (:suola @tiedot/suodatin-valinnat) nimi)))
                            @tiedot/toteumat)
            materiaali-nimet (distinct (map #(let [{{nimi :nimi} :materiaali} %]
                                               nimi)
                                            @tiedot/toteumat))
            kaytetty-yhteensa (str "Käytetty yhteensä: " (fmt/desimaaliluku-opt (reduce + (keep :maara listaus))) "t")]
        (suolatoteumat-taulukko muokattava?
                                urakka
                                sopimus-id
                                listaus
                                materiaali-nimet
                                kaytetty-yhteensa)))))
