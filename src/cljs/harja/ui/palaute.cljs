(ns harja.ui.palaute
  (:require
            [reagent.core :refer [atom]]
            [harja.ui.ikonit :as ikonit]
            [reagent.core :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]))

(def +linkki-koulutusvideot+ "http://finnishtransportagency.github.io/harja/")

(def palautetyypit
  [{:nimi "Yleinen palaute" :avain :yleinen}
   {:nimi "Kehitysidea" :avain :kehitysidea}
   {:nimi "Bugi" :avain :ongelma}
   {:nimi "Käyttöoikeusongelma" :avain :kayttooikeus}
   {:nimi "Tehtävälista" :avain :tehtavalista}])

(defn- palautelomake []
  (let [lomakedata-atom (atom nil)]
    (fn []
      [:div
       [:p "Valitse, mitä palautteesi koskee:"]
       [lomake/lomake
        {:otsikko ""
         :muokkaa! (fn [uusi]
                     (reset! lomakedata-atom uusi))}
        [{:otsikko ""
          :nimi :palaute-tyyppi
          :tyyppi :valinta
          :valinnat palautetyypit
          :valinta-arvo :avain
          :valinta-nayta #(or (:nimi %) "- valitse -")}]
        @lomakedata-atom]

       [yleiset/vihje-elementti [:span
                                 [:span "Olethan tutustunut "]
                                 [:a {:href +linkki-koulutusvideot+} "Harjan koulutusvideoihin"]
                                 [:span " ennen palautteen lähettämistä?"]]]])))

(defn palaute-linkki []
  [:a {:class "klikattava"
       :id "palautelinkki"
       :on-click #(modal/nayta! {:otsikko "Palautteen lähettäminen"
                                 :luokka "palaute-dialog"}
                                [palautelomake])}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Palautetta!"]])
