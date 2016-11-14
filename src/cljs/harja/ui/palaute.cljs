(ns harja.ui.palaute
  (:require
    [reagent.core :refer [atom]]
    [harja.ui.ikonit :as ikonit]
    [reagent.core :refer [atom]]
    [harja.tiedot.palaute :as tiedot]
    [harja.loki :refer [log]]
    [harja.ui.modal :as modal]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.lomake :as lomake]
    [harja.tiedot.istunto :as istunto]))

(def +linkki-koulutusvideot+ "http://finnishtransportagency.github.io/harja/")

(defn mailto-yleinen []
  (-> (tiedot/mailto)
      (tiedot/subject tiedot/palaute-otsikko "?")
      (tiedot/body (str tiedot/palaute-body
                        (tiedot/tekniset-tiedot
                          @istunto/kayttaja
                          (-> js/window .-location .-href)
                          (-> js/window .-navigator .-userAgent)))
                   (if-not (empty? tiedot/palaute-otsikko) "&" "?"))))

(def palautetyypit
  [{:nimi "Yleinen palaute" :avain :yleinen}
   {:nimi "Kehitysidea" :avain :kehitysidea}
   {:nimi "Bugi" :avain :ongelma}
   {:nimi "Käyttöoikeusongelma" :avain :kayttooikeus}
   {:nimi "Tehtävälista" :avain :tehtavalista}])

(defn palauteohje-yleinen []
  [:div
   [:span "Klikkaa "]
   [:a
    {:href (mailto-yleinen)}
    "tästä"]
   [:span " lähettääksesi palautetta Harjan kehitystiimille"]])

(defn palauteohje-kayttooikeus []
  [:div
   [:div "Jos käyttäjältä puuttuu käyttäjätunnukset Harjaan, ole yhteydessä oman organisaatiosi pääkäyttäjään."]
   [:div "Mikäli et pääse suorittamaan Harjassa jotain tehtävää, johon sinulla tulisi olla oikeus, klikkaa "]
   [:a
    {:href (mailto-yleinen)}
    "tästä"]
   [:span " lähettääksesi palautetta Harjan kehitystiimille."]])

(defn palauteohje-tehtavalista []
  [:div
   [:span "Klikkaa "]
   [:a
    {:href (-> (tiedot/mailto)
               (tiedot/subject tiedot/palaute-otsikko "?")
               (tiedot/body (str tiedot/palaute-body
                                 (tiedot/tekniset-tiedot
                                   @istunto/kayttaja
                                   (-> js/window .-location .-href)
                                   (-> js/window .-navigator .-userAgent)))
                            (if-not (empty? tiedot/palaute-otsikko) "&" "?")))}
    "tästä"]
   [:span " lähettääksesi palautetta Harjan kehitystiimille."]])

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

       (case (:palaute-tyyppi @lomakedata-atom)
         :tehtavalista [palauteohje-tehtavalista]
         :kayttooikeus [palauteohje-kayttooikeus]
         [palauteohje-yleinen])

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
