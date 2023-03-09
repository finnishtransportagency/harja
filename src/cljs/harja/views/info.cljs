 (ns harja.views.info
   "Infonäkymä mihin siirretty koulutusvideot julkiselta sisäiseen palvelimeen.
   Videot haetaan tietokannasta rajapintaa käyttäen"
  (:require [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.pvm :as pvm]
            [harja.tiedot.info :as tiedot]))

(defn videolistaus [_ videot]
  [:div
   [:ul {:class "info-lista"}
    
    (if (empty? videot)
      [:span "Ei videoita."]

      (doall
       (map (fn [{:as m}]
              ^{:key (m :id)}

              [:li
               [:div {:class "video-wrap"}
                
                ; Päivämäärä
                [:span [ikonit/ikoni-ja-teksti [ikonit/harja-icon-misc-clock] (pvm/pvm (m :pvm))]]
                [:br]

                ;Videon linkki & otsikko
                [:div  {:class "video-otsikko"}
                 [:a {:href (m :linkki) :target "_blank" :style {:color "#004D99"}}
                  [ikonit/ikoni-ja-teksti (m :otsikko) [ikonit/livicon-external]]]]]])

            videot)))]])

(defn videot* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeKoulutusvideot))))

   (fn [e! {:keys [videot]}]
     [:span
      [:div.section
       [:h1 {:class "header-yhteiset"} "Harja Info"
        [:div {:class "otsikko-viiva"}]

        [:ul
         [:h2 {:class "header-yhteiset"} "Harja uutiset"]
         [:ul
          [:h3 {:class "header-yhteiset"} [:a {:href "https://finnishtransportagency.github.io/harja/" :target "_blank" :style {:color "#004D99"}}
                                           [ikonit/ikoni-ja-teksti "https://finnishtransportagency.github.io/harja/ " [ikonit/livicon-external]]]]]]

        [:ul
         [:h2 {:class "header-yhteiset"} "Tietoja henkilötietojesi käsittelystä"]
         [:ul
          [:h3 {:class "header-yhteiset"} [:a {:href "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja" :target "_blank" :style {:color "#004D99"}}
                                           [ikonit/ikoni-ja-teksti "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja " [ikonit/livicon-external]]]]]]

        [:ul {:class "info-heading"} "Harja koulutusvideot"
         [:p {:class "info-heading-pieni main"} "Koulutusvideoita HARJA:n käytön tueksi."]

         [:div {:class "videot"}
          [videolistaus e! videot]]]]]])))

(defn info 
  "Hakee koulutusvideot kun käyttäjä tulee näkymään"
  []
  [tuck tiedot/tila videot*]) 