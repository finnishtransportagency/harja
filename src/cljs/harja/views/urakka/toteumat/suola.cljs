(ns harja.views.urakka.toteumat.suola
  "Suolankäytön toteumat hoidon alueurakoissa"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.toteumat.suola :as suola]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.loki :refer [log logt]]
            [harja.atom :refer [paivita!]]
            [cljs.core.async :refer [<! >!]]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce suolatoteumissa? (atom false))

(defonce toteumat
         (reaction<! [hae? @suolatoteumissa?
                      ur @nav/valittu-urakka
                      sopimus @tiedot-urakka/valittu-sopimusnumero
                      hk @tiedot-urakka/valittu-hoitokausi
                      kk @tiedot-urakka/valittu-hoitokauden-kuukausi]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and hae? ur)
                       (go
                         (into []
                               ;; luodaan kaikille id
                               (map-indexed (fn [i rivi]
                                              (assoc rivi :id i)))

                               (<! (suola/hae-toteumat (:id ur) (first sopimus)
                                                       (or kk hk))))))))

(defonce materiaalit
  (reaction<! [hae? @suolatoteumissa?]
              (when hae?
                (suola/hae-materiaalit))))

(defn suolatoteumat []
  (komp/luo
   (komp/lippu suolatoteumissa? pohjavesialueet/karttataso-pohjavesialueet
               tiedot-urakka/aseta-kuluva-kk-jos-hoitokaudella?)
   (fn []
     (let [ur @nav/valittu-urakka
           [sopimus-id _] @tiedot-urakka/valittu-sopimusnumero
           muokattava? (comp not true? :koneellinen)
           kaytetty-yhteensa (str "Käytetty yhteensä: " (fmt/desimaaliluku (reduce + (keep :maara @toteumat))))
           listaus (reverse (sort-by :alkanut @toteumat))]
       [:div.suolatoteumat
        [kartta/kartan-paikka]
        [:span.valinnat
         [urakka-valinnat/urakan-sopimus ur]
         [urakka-valinnat/urakan-hoitokausi-ja-kuukausi ur]]

        [grid/grid {:otsikko "Talvisuolan käyttö"
                    :tallenna (if (oikeudet/voi-kirjoittaa?
                                   oikeudet/urakat-toteumat-suola
                                   (:id @nav/valittu-urakka))
                                #(go (if-let [tulos (<! (suola/tallenna-toteumat (:id ur) sopimus-id %))]
                                       (paivita! toteumat)))
                                :ei-mahdollinen)
                    :tallennus-ei-mahdollinen-tooltip
                    (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                                    oikeudet/urakat-toteumat-suola)
                    :tyhja (if (nil? @toteumat)
                             [yleiset/ajax-loader "Suolatoteumia haetaan..."]
                             "Ei suolatoteumia valitulle aikavälille")
                    :uusi-rivi #(assoc % :alkanut (pvm/nyt))
                    :voi-poistaa? muokattava?
                    :max-rivimaara 500
                    :max-rivimaaran-ylitys-viesti "Yli 500 suolatoteumaa. Rajoita hakuehtoja."}
         [{:otsikko "Suola\u00ADtyyppi" :nimi :materiaali :fmt :nimi :leveys "15%" :muokattava? muokattava?
           :tyyppi :valinta
           :validoi [[:ei-tyhja "Valitse materiaali"]]
           :valinta-nayta #(or (:nimi %) "- valitse -")
           :valinnat @materiaalit}
          {:otsikko "Pvm" :nimi :alkanut :fmt pvm/pvm-opt :tyyppi :pvm :leveys "15%" :muokattava? muokattava?
           :validoi [[:ei-tyhja "Anna päivämäärä"]]
           :huomauta [[:valitun-kkn-aikana-urakan-hoitokaudella]]}
          {:otsikko "Käytetty määrä (t)" :nimi :maara :tyyppi :positiivinen-numero :leveys "15%" :muokattava? muokattava?
           :validoi [[:ei-tyhja "Anna määrä"]] :tasaa :oikea}
          {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "50%" :muokattava? muokattava?
           :hae #(if (muokattava? %)
                   (:lisatieto %)
                   (str (:lisatieto %) " (Koneellisesti raportoitu)"))}]

          listaus]
        (when-not (empty? @toteumat)
          [:div.bold kaytetty-yhteensa])]))))
