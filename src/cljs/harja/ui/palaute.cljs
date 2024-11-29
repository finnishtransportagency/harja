(ns harja.ui.palaute
  (:require
    [reagent.core :refer [atom]]
    [harja.tiedot.palaute :as tiedot]
    [harja.loki :refer [log]]
    [harja.ui.modal :as modal]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.ikonit :as ikonit]))

(def palautetyypit
  [{:nimi "Kehitysidea" :tyyppi :kehitysidea}
   {:nimi "Bugi / Tekninen ongelma" :tyyppi :tekninen-ongelma}
   {:nimi "Käyttöoikeusongelma" :tyyppi :kayttooikeus}
   {:nimi "Tehtävälista" :tyyppi :tehtavalista}
   {:nimi "Yleinen palaute" :tyyppi :yleinen}])

(defn- palauteohje-yleinen [palaute-tyyppi] 
   [modal/modal-linkki
    "Lähetä tästä klikkaamalla sähköpostilla palautetta Harjan kehitystiimille."
    (tiedot/mailto-linkki (tiedot/mailto-kehitystiimi) (tiedot/palaute-body-yleinen) palaute-tyyppi)])

(defn- palauteohje-kehitysidea [palaute-tyyppi]
   [modal/modal-linkki
    "Lähetä tästä klikkaamalla sähköpostia Harjan kehitystiimille kertoaksesi kehitysideasi."
    (tiedot/mailto-linkki (tiedot/mailto-kehitystiimi) (tiedot/palaute-body-kehitysidea) palaute-tyyppi)])

(defn- palauteohje-tekninen-ongelma [palaute-tyyppi]
  [:span 
    [modal/modal-linkki
     "Lähetä tästä klikkaamalla sähköpostia Harjan kehitystiimille teknisen ongelman raportointia varten."
     (tiedot/mailto-linkki (tiedot/mailto-kehitystiimi) (tiedot/palaute-body-tekninen-ongelma) palaute-tyyppi)]
   [:p
    [yleiset/vihje-elementti "Huomioithan raportoidessasi ongelmasta seuraavat asiat:"]
    [:ul
     [:li "Jos raportoit ilmoituksiin liittyvästä ongelmasta, lähetäthän ongelmaa koskevien ilmoitusten id:t viestin mukana."]]]])

(defn- palauteohje-kayttooikeus [palaute-tyyppi]
  [:div
   [:p "Jos käyttäjältä puuttuu käyttäjätunnukset Harjaan, ole yhteydessä oman organisaatiosi pääkäyttäjään. Jos et tiedä kuka organisaatiosi pääkäyttäjä on, ole yhteydessä Väyläviraston Käyttövaltuushallintaan osoitteessa jl_kvhtuki@vayla.fi"] 
    [modal/modal-linkki
     "Mikäli et pääse suorittamaan Harjassa jotain tehtävää, johon sinulla tulisi olla oikeus, lähetä tästä klikkaamalla palautetta Harjan kehitystiimille sähköpostitse"
     (tiedot/mailto-linkki (tiedot/mailto-kehitystiimi) (tiedot/palaute-body-tekninen-ongelma) palaute-tyyppi)]])

(defn- palauteohje-tehtavalista [palaute-tyyppi]
  [:p "Harjan pääkäyttäjä vastaa Harjan tehtävälistasta. "
   [modal/modal-linkki
    "Lähetä tästä palautetta tehtävälistaa ylläpitävälle pääkäyttäjälle sähköpostitse."
    (tiedot/mailto-linkki (tiedot/mailto-paakayttaja) (tiedot/palaute-body-tekninen-ongelma) palaute-tyyppi)]])

(defn- palauteohje [palautetyyppi]
  [:div.palauteohje
   (case (:tyyppi palautetyyppi)
     nil [:span ""]
     :tehtavalista [palauteohje-tehtavalista (:nimi palautetyyppi)]
     :kayttooikeus [palauteohje-kayttooikeus (:nimi palautetyyppi)]
     :tekninen-ongelma [palauteohje-tekninen-ongelma (:nimi palautetyyppi)]
     :kehitysidea [palauteohje-kehitysidea (:nimi palautetyyppi)]
     [palauteohje-yleinen (:nimi palautetyyppi)])])

(defn- palautelomake []
  (let [valinta-atom (atom nil)]
    (fn []
      [:div
       [:p "Valitse, mitä palautteesi koskee:"]
       [yleiset/livi-pudotusvalikko
        {:valitse-fn #(reset! valinta-atom %)
         :valinta @valinta-atom
         :class "livi-alasveto-250"
         :format-fn #(if %
                       (:nimi %)
                       "- valitse -")}
        palautetyypit]

       [palauteohje @valinta-atom]
       [:p "Palautteen voit lähettää sähköpostitse osoitteeseen " 
        [:a {:href (tiedot/mailto-kehitystiimi)} tiedot/sahkoposti-kehitystiimi]]
       [yleiset/vihje-elementti [:span
                                 [:span "Olethan tutustunut "]
                                 [modal/modal-linkki
                                  "Harja-projektin sivuihin ja koulutusvideoihin"
                                  tiedot/+linkki-koulutusvideot+
                                  "_blank"]
                                 [:span " ennen palautteen lähettämistä?"]]]])))

(defn palaute-linkki []
  [:a.klikattava.alleviivaa {:href "#"
                  :id "palautelinkki"
                  :on-click #(modal/nayta! {:otsikko "Palautteen lähettäminen"
                                            :luokka "palaute-dialog"}
                               [palautelomake])}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Palautetta!"]])
