 (ns harja.views.info
  "Infonäkymä mihin siirretty koulutusvideot julkiselta sisäiseen palvelimeen.
   Videot haetaan tietokannasta rajapintaa käyttäen"
   (:require [harja.ui.yleiset :as yleiset]
             [tuck.core :refer [tuck]]
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
                 [:span [ikonit/ikoni-ja-teksti [ikonit/harja-icon-misc-clock] (pvm/pvm (m :pvm))]]
                 [:br]
                 [:div  {:class "video-otsikko"}
                  [:a {:href (m :linkki) :target "_blank" :style {:color "#004D99"}}
                   [ikonit/ikoni-ja-teksti (m :otsikko) [ikonit/livicon-external]]]]]])
          videot)))]])

(defn harja-info [e! _]
  (komp/luo
    (komp/sisaan
      #(do
         (e! (tiedot/->HaeKoulutusvideot))))

    (fn [e! {:keys [videot]}]
      [:div.harja-info
       [:h1 "Harja-info"]
       [:hr]
       [:section.linkit
        [:h2 "Uutiset ja selosteet"]
        [:div.sisalto
         [:span
          [yleiset/staattinen-linkki-uuteen-valilehteen
           [:h2 "Harja-uutiset "
            [ikonit/livicon-external]]
           "https://finnishtransportagency.github.io/harja/"]]
         [:span
          [yleiset/staattinen-linkki-uuteen-valilehteen
           [:h2 "Tietoja henkilötietojesi käsittelystä "
            [ikonit/livicon-external]]
           "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja"]]
         [:span
          [yleiset/staattinen-linkki-uuteen-valilehteen
           [:h2 "Saavutettavuusseloste "
            [ikonit/livicon-external]]
           "https://palju.vaylapilvi.fi/palju/Extranet/Muut/Saavutettavuusselosteet/Saavutettavuusseloste_Harja.pdf"
           {:title "Tietoa Harjan saavutettavuuden tasosta."}]]]]

       [:section.klinikat
        [:h2 "Harja-klinikat"]
        [:p.sisalto "Vuonna 2025 Harjan kehitystiimi järjestää joka neljäs keskiviikko Harja-klinikoita. Niissä kerrotaan ajankohtaisia Harjan käyttöön liittyviä asioita, sekä on mahdollista esittää kysymyksiä. Alla Harja-klinikoiden alustavia päivämääriä (voi tulla muutoksia). Pyrimme ilmoittamaan Harja-klinikoista myös Harjassa näkyvillä mainosbannereilla. Tervetuloa!"]
        (when (< (pvm/nyt) (pvm/->pvm "18.6.2025"))
          [:span
           [:br]
           [:p "Seuraavat Harja-klinikat:"]
           [:ul
            (when (< (pvm/nyt) (pvm/->pvm "29.1.2025")) [:li "Ke 29.1.2025"])
            (when (< (pvm/nyt) (pvm/->pvm "26.2.2025")) [:li "Ke 26.2.2025"])
            (when (< (pvm/nyt) (pvm/->pvm "26.3.2025")) [:li "Ke 26.3.2025"])
            (when (< (pvm/nyt) (pvm/->pvm "23.4.2025")) [:li "Ke 23.4.2025"])
            (when (< (pvm/nyt) (pvm/->pvm "21.5.2025")) [:li "Ke 21.5.2025"])
            (when (< (pvm/nyt) (pvm/->pvm "18.6.2025")) [:li "Ke 18.6.2025"])]])]


        [:section.koulutusvideot
         [:h2 "Harja-koulutusvideot"]
         [:div.sisalto
          [yleiset/vihje "Huomaathan, että osa Harjan koulutusvideoista on melko vanhoja, ja järjestelmä on monilta osin muuttunut videoiden tekemisen jälkeen."]
          [:div [videolistaus e! videot]]]]])))

(defn info
  "Hakee koulutusvideot kun käyttäjä tulee näkymään"
  []
  [tuck tiedot/tila harja-info])
