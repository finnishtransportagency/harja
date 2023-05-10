(ns harja.views.urakka.tyomaapaivakirja-nakyma
 "Työmaapäiväkirja näkymä"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]
            [harja.views.urakka.tyomaapaivakirja.vahvuus :as vahvuus]
            [harja.views.urakka.tyomaapaivakirja.saatiedot :as saatiedot]
            [harja.views.urakka.tyomaapaivakirja.keliolosuhteet :as keliolosuhteet]
            [harja.views.urakka.tyomaapaivakirja.kalusto-ja-toimenpiteet :as kalusto]
            [harja.views.urakka.tyomaapaivakirja.muut-toimenpiteet :as muut-toimenpiteet]
            [harja.views.urakka.tyomaapaivakirja.vahingot :as vahingot]
            [harja.views.urakka.tyomaapaivakirja.liikenteenohjaukset :as liikenne]
            [harja.views.urakka.tyomaapaivakirja.maastotoimeksiannot :as maastotoimeksiannot]))

(def toimituksen-tila [{:class "ok" :selitys "Ok"}
                       {:class "myohassa" :selitys "Myöhässä"}
                       {:class "puuttuu" :selitys "Puuttuu"}])

(defn tyomaapaivakirja-nakyma [e! {:keys [valittu-rivi] :as tiedot}]
  (let [toimitus-tiedot (get toimituksen-tila (:tila valittu-rivi))]
    [:span {:class "paivakirja-toimitus"}
     [:div {:class (str "pallura " (:class toimitus-tiedot))}]
     [:span {:class "kohta"} (:selitys toimitus-tiedot)]]

    [:<>
     [napit/takaisin "Takaisin" #(e! (tiedot/->PoistaRiviValinta)) {:luokka "nappi-reunaton"}]

     [:div {:style {:padding "48px 92px 72px"}}
      [:p (str valittu-rivi)]

      [:h3 {:class "header-yhteiset"} "UUD MHU 2022–2027"]
      [:h1 {:class "header-yhteiset"} "Työmaapäiväkirja 9.10.2022"]

      [:div {:class "nakyma-otsikko-tiedot"}

       [:span "Saapunut 11.10.2022 05:45"]
       [:span "Päivitetty 11.10.2022 05:45"]
       [:a {:href "url"} "Näytä muutoshistoria"]

       [:span {:class "paivakirja-toimitus"}
        [:div {:class (str "pallura " (:class toimitus-tiedot))}]
        [:span {:class "kohta"} (:selitys toimitus-tiedot)]]

       [:a
        [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "2 kommenttia"]]]

      [:hr]

      ;; Päivystäjät, Työnjohtajat
      (vahvuus/vahvuus-grid)
      ;; Sääasemien tiedot
      (saatiedot/saatiedot-grid)
      ;; Poikkeukselliset keliolosuhteet
      (keliolosuhteet/poikkeukselliset-keliolosuhteet-grid)
      ;; Kalusto ja tielle tehdyt toimenpiteet
      (kalusto/kalusto-ja-tien-toimenpiteet-grid)
      ;; Muut toimenpiteet
      (muut-toimenpiteet/muut-toimenpiteet-grid)
      ;; Vahingot
      (vahingot/vahingot-ja-onnettomuudet)
      ;; Tilapäiset liikenteenohjaukset
      (liikenne/tilapaiset-liikenteenohjaukset)
      ;; Viranomaispäätöksiin liittyvät maastotoimeksiannot
      (maastotoimeksiannot/maastotoimeksiannot-grid)
      ]]))
