(ns harja.ui.palaute
  (:require
    [reagent.core :refer [atom]]
    [harja.ui.ikonit :as ikonit]
    [reagent.core :refer [atom]]
    [harja.tiedot.palaute :as tiedot]
    [harja.loki :refer [log]]
    [harja.ui.modal :as modal]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.ikonit :as ikonit]))

(def +linkki-koulutusvideot+ "http://finnishtransportagency.github.io/harja/")

(def palautetyypit
  [{:nimi "Kehitysidea" :tyyppi :kehitysidea}
   {:nimi "Bugi / Tekninen ongelma" :tyyppi :ongelma}
   {:nimi "Käyttöoikeusongelma" :tyyppi :kayttooikeus}
   {:nimi "Tehtävälista" :tyyppi :tehtavalista}
   {:nimi "Yleinen palaute" :tyyppi :yleinen}])

(defn palauteohje-yleinen [palaute-tyyppi]
  [:p "Klikkaa "
   [modal/modal-linkki
    "tästä"
    (tiedot/mailto-linkki (tiedot/mailto-kehitystiimi) (tiedot/palaute-body) palaute-tyyppi)]
   [:span " lähettääksesi palautetta Harjan kehitystiimille."]])

(defn palauteohje-kayttooikeus [palaute-tyyppi]
  [:div
   [:p "Jos käyttäjältä puuttuu käyttäjätunnukset Harjaan, ole yhteydessä oman organisaatiosi pääkäyttäjään."]
   [:p
    [:span "Mikäli et pääse suorittamaan Harjassa jotain tehtävää, johon sinulla tulisi olla oikeus, klikkaa "]
    [modal/modal-linkki
     "tästä"
     (tiedot/mailto-linkki (tiedot/mailto-kehitystiimi) (tiedot/palaute-body) palaute-tyyppi)]
    [:span " lähettääksesi palautetta Harjan kehitystiimille."]]])

(defn palauteohje-tehtavalista [palaute-tyyppi]
  [:p "Klikkaa "
   [modal/modal-linkki
    "tästä"
    (tiedot/mailto-linkki (tiedot/mailto-paakayttaja) (tiedot/palaute-body) palaute-tyyppi)]
   [:span " lähettääksesi palautetta Harjan tehtävälistaa ylläpitävälle pääkäyttäjälle."]])

(defn- palauteohje [palautetyyppi]
  [:div.palauteohje
   (case (:tyyppi palautetyyppi)
     nil [:span ""]
     :tehtavalista [palauteohje-tehtavalista (:nimi palautetyyppi)]
     :kayttooikeus [palauteohje-kayttooikeus (:nimi palautetyyppi)]
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

       [yleiset/vihje-elementti [:span
                                 [:span "Olethan tutustunut "]
                                 [modal/modal-linkki
                                  "Harjan koulutusvideoihin"
                                  +linkki-koulutusvideot+
                                  "_blank"]
                                 [:span " ennen palautteen lähettämistä?"]]]])))

(defn palaute-linkki []
  [:a {:class "klikattava"
       :id "palautelinkki"
       :on-click #(modal/nayta! {:otsikko "Palautteen lähettäminen"
                                 :luokka "palaute-dialog"}
                                [palautelomake])}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Palautetta!"]])
