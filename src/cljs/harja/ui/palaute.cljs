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
  [:span
   [:span "Klikkaa "]
   [:a
    {:href (mailto-yleinen)}
    "tästä"]
   [:span " lähettääksesi palautetta Harjan kehitystiimille"]])

(defn palauteohje-kayttooikeus []
  [:span
   [:div "Jos käyttäjältä puuttuu käyttäjätunnukset Harjaan, ole yhteydessä oman organisaatiosi pääkäyttäjään."]
   [:div "Mikäli et pääse suorittamaan Harjassa jotain tehtävää, johon sinulla tulisi olla oikeus, klikkaa "]
   [:a
    {:href (mailto-yleinen)}
    "tästä"]
   [:span " lähettääksesi palautetta Harjan kehitystiimille."]])

(defn palauteohje-tehtavalista []
  [:span
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

(defn- palauteohje [tyyppi]
  [:div.palauteohje
   (case tyyppi
     nil [:span ""]
     :tehtavalista [palauteohje-tehtavalista]
     :kayttooikeus [palauteohje-kayttooikeus]
     [palauteohje-yleinen])])

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

       [palauteohje (:avain @valinta-atom)]

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
