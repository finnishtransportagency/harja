(ns harja.views.urakka.toteumat.tiemerkinta-muut-tyot
  (:require [reagent.core :refer [atom] :as r]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki
                                                  livi-pudotusvalikko +korostuksen-kesto+ kuvaus-ja-avainarvopareja]]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot :as tiedot]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-xf]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction-writable]]))

(defn- valinnat [{:keys [valittu-urakka valittu-sopimusnumero
                         valitse-sopimusnumero valitun-urakan-hoitokaudet
                         valittu-hoitokausi valitse-hoitokausi]}]
  (valinnat/urakan-sopimus-ja-hoitokausi
    valittu-urakka
    valittu-sopimusnumero valitse-sopimusnumero
    valitun-urakan-hoitokaudet valittu-hoitokausi valitse-hoitokausi))

(defn muu-tyo-lomake [e! tila riippuvuudet]
  (let [vanha-toteuma? (get-in tila [:valittu-tyo :id])]
    [:div
     [napit/takaisin "Takaisin toteumaluetteloon"
      #(e! (tiedot/->ValitseTyo nil))]
     [lomake {:otsikko (if vanha-toteuma?
                         "Muokkaa toteumaa"
                         "Luo uusi toteuma")
              :luokka :horizontal
              :voi-muokata? true ;; TODO OIKEUSTARKISTUS
              :muokkaa! #(e! (tiedot/->MuokkaaTyota %))
              ;:footer [napit/palvelinkutsu-nappi
              ;:"Tallenna toteuma"
              ;:#(e! (tiedot/->TallennaTyo %))
              ;:{:luokka "nappi-ensisijainen"
              ;: :ikoni (ikonit/tallenna)
              ;: :kun-onnistuu
              ;: #(do
              ;:    (reset! urakan-materiaalin-kaytot %)
              ;:    (reset! valittu-materiaalin-kaytto nil))
              ;: :disabled (or (not voi-tallentaa?)
              ;:              (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-materiaalit (:id @nav/valittu-urakka))))}]
              }

      [{:otsikko "Päivämäärä" :nimi :paivamaara :tyyppi :pvm}
       {:otsikko "Selite" :nimi :selite :tyyppi :string}
       {:otsikko "Hinta" :nimi :hinta :tyyppi :positiivinen-numero}
       {:otsikko "Ylläpitokohde" :nimi :yllapitokohde :tyyppi :positiivinen-numero}
       #_{:otsikko "Laskentakohde" :nimi :selite :tyyppi :selite}]
      (:valittu-tyo tila)]]))

(defn- muut-tyot-lista [e!
                        {:keys [muut-tyot] :as tila}
                        {:keys [valittu-urakka valittu-sopimusnumero
                                valitse-sopimusnumero valitun-urakan-hoitokaudet
                                valittu-hoitokausi valitse-hoitokausi]
                         :as riippuvuudet}]
  [:div
   [valinnat {:valittu-urakka valittu-urakka
              :valittu-sopimusnumero valittu-sopimusnumero
              :valitse-sopimusnumero valitse-sopimusnumero
              :valitun-urakan-hoitokaudet valitun-urakan-hoitokaudet
              :valittu-hoitokausi valittu-hoitokausi
              :valitse-hoitokausi valitse-hoitokausi}]
   [grid/grid
    {:otsikko (str "Muut työt")
     :tyhja (if (nil? muut-tyot)
              [ajax-loader "Toteumia haetaan..."]
              "Ei toteumia.")
     :rivi-klikattu #(e! (tiedot/->HaeTyo {:id (:id %)
                                           :urakka (:id valittu-urakka)}))}
    [{:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm-opt :nimi :pvm :leveys 10}
     {:otsikko "Selite" :tyyppi :string :nimi :selite :leveys 20}
     {:otsikko "Hinta" :tyyppi :numero :nimi :hinta :fmt (partial fmt/euro-opt true) :leveys 10}
     {:otsikko "Ylläpitoluokka" :tyyppi :numero :nimi :yllapitoluokka :leveys 10}
     {:otsikko "Laskentakohde" :tyyppi :string :nimi :laskentakohde :leveys 10}]
    muut-tyot]])

(defn- muut-tyot-paakomponentti [e! tila]
  ;; Kun näkymään tullaan, yhdistetään navigaatiosta tulevat valinnat
  (e! (tiedot/->YhdistaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->YhdistaValinnat uusi))))
    (fn [e! {:keys [valittu-tyo] :as tila}]
      [:span
       (if valittu-tyo
         [muu-tyo-lomake e! tila {}]
         [muut-tyot-lista e! tila
          {:valittu-urakka @nav/valittu-urakka
           :valittu-sopimusnumero u/valittu-sopimusnumero
           :valitse-sopimusnumero u/valitse-sopimusnumero!
           :valitun-urakan-hoitokaudet u/valitun-urakan-hoitokaudet
           :valittu-hoitokausi u/valittu-hoitokausi
           :valitse-hoitokausi u/valitse-hoitokausi!}])])))

(defn muut-tyot []
  (komp/luo
    (fn []
      [tuck tiedot/muut-tyot muut-tyot-paakomponentti])))
