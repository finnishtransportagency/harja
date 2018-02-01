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
            [harja.ui.ikonit :as ikonit])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defn nayta-toteumat-kartalla [toteumat]
  (let [idt (map :tid toteumat)]
    (nav/vaihda-kartan-koko! :L)
    (reset! tiedot/valitut-toteumat
            (into #{} (concat @tiedot/valitut-toteumat idt))))
  (reset! tiedot/valittu-suolatoteuma (first toteumat)))

(defn piilota-toteumat-kartalla [toteumat]
  (let [idt (map :tid toteumat)]
    (reset! tiedot/valitut-toteumat
            (into #{} (remove (into #{} idt) @tiedot/valitut-toteumat)))))

(defn suolankayton-paivan-erittely [suolan-kaytto]
  [grid/grid
   {:otsikko "Päivän toteumat"
    :tyhja "Ei päivän toteumia"
    :tunniste :id}
   [{:otsikko "Alkanut" :nimi :alkanut :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10}
    {:otsikko "Päättynyt" :nimi :paattynyt :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10}
    {:otsikko "Määrä" :nimi :maara :tyyppi :positiivinen-numero :leveys 10}
    {:otsikko ""
     :nimi :nayta-kartalla
     :tyyppi :komponentti
     :leveys 7
     :komponentti (fn [toteuma]
                    [:div
                     [(if (valittu?)
                        :button.nappi-toissijainen.nappi-grid
                        :button.nappi-ensisijainen.nappi-grid)
                      {:on-click #(if (contains? @tiedot/valitut-toteumat (:tid toteuma))
                                    (piilota-toteumat-kartalla [toteuma])
                                    (nayta-toteumat-kartalla [toteuma]))}
                      (ikonit/ikoni-ja-teksti
                        (ikonit/map-marker)
                        (if (contains? @tiedot/valitut-toteumat (:tid toteuma))
                          "Piilota kartalta"
                          "Näytä kartalla"))]])}]
   (map-indexed (fn [i toteuma]
                  (assoc toteuma :id i))
                (:toteumat suolan-kaytto))])

(defn suolatoteumat-taulukko [muokattava? urakka sopimus-id listaus materiaali-nimet kaytetty-yhteensa]
  [:div.suolatoteumat
   [kartta/kartan-paikka]
   [:span.valinnat
    [urakka-valinnat/urakan-sopimus urakka]
    [urakka-valinnat/urakan-hoitokausi-ja-kuukausi urakka]
    [ui-valinnat/materiaali-valikko {:valittu-materiaali (:suola @tiedot/suodatin-valinnat)
                                     :otsikko "Suola"
                                     :valitse-fn #(swap! tiedot/suodatin-valinnat assoc :suola %)
                                     :lisaa-kaikki? true
                                     :materiaalit materiaali-nimet}]]

   [grid/grid {:otsikko "Talvisuolan käyttö"
               :tallenna (if (oikeudet/voi-kirjoittaa?
                               oikeudet/urakat-toteumat-suola
                               (:id @nav/valittu-urakka))
                           #(go (if-let [tulos (<! (suola/tallenna-toteumat (:id urakka) sopimus-id %))]
                                  (paivita! toteumat)))
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
                                   (map (juxt :id (fn [rivi] [suolankayton-paivan-erittely rivi])))
                                   @tiedot/toteumat)}
    [{:tyyppi :vetolaatikon-tila :leveys "1%"}
     {:otsikko "Suola\u00ADtyyppi" :nimi :materiaali :fmt :nimi :leveys "15%" :muokattava? muokattava?
      :tyyppi :valinta
      :validoi [[:ei-tyhja "Valitse materiaali"]]
      :valinta-nayta #(or (:nimi %) "- valitse -")
      :valinnat @tiedot/materiaalit}
     {:otsikko "Pvm" :nimi :pvm :fmt pvm/pvm-opt :tyyppi :pvm :leveys "15%" :muokattava? muokattava?
      :validoi [[:ei-tyhja "Anna päivämäärä"]]
      :huomauta [[:valitun-kkn-aikana-urakan-hoitokaudella]]}
     {:otsikko "Käytetty määrä (t)" :nimi :maara :fmt #(fmt/desimaaliluku % 3)
      :tyyppi :positiivinen-numero :desimaalien-maara 3 :leveys "15%" :muokattava? muokattava?
      :validoi [[:ei-tyhja "Anna määrä"]] :tasaa :oikea}
     {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "40%" :muokattava? muokattava?
      :hae #(if (muokattava? %)
              (:lisatieto %)
              (str (:lisatieto %) " (Koneellisesti raportoitu, toteumia: "
                   (count (map :toteumat @tiedot/toteumat)) ")"))}
     {:otsikko ""
      :nimi :nayta-kartalla
      :tyyppi :komponentti
      :leveys "10%"
      :komponentti (fn [rivi]
                     (let [toteumat (:toteumat rivi)
                           valittu? #(some (fn [toteuma]
                                             (contains? @tiedot/valitut-toteumat (:tid toteuma)))
                                           toteumat)]
                       (when (not (empty? toteumat))
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

   (when-not (empty? @tiedot/toteumat)
     [:div.bold kaytetty-yhteensa])])

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
            listaus (reverse (sort-by :pvm
                                      ;; Näytetään vain valittu suola
                                      (filter (fn [{{nimi :nimi} :materiaali}]
                                                (or (= (:suola @tiedot/suodatin-valinnat) "Kaikki")
                                                    (= (:suola @tiedot/suodatin-valinnat) nimi)))
                                              @tiedot/toteumat)))
            materiaali-nimet (distinct (map #(let [{{nimi :nimi} :materiaali} %]
                                               nimi)
                                            @tiedot/toteumat))
            kaytetty-yhteensa (str "Käytetty yhteensä: " (fmt/desimaaliluku (reduce + (keep :maara listaus))) "t")]
        (suolatoteumat-taulukko muokattava?
                                urakka
                                sopimus-id
                                listaus
                                materiaali-nimet
                                kaytetty-yhteensa)))))
