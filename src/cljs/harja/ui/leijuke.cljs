(ns harja.ui.leijuke
  "Yleinen leijuke -komponentti. Leijuke on muun sisällön päälle tuleva absoluuttisesti
  positioitu pieni elementti, esim. lyhyt lomake."
  (:require [reagent.core :as r]
            [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [goog.events.EventType :as EventType]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))


(def avautumissuunta-tyyli
  {:alas-vasen {:top "calc(100% - 1px)"
                :right "0"
                :bottom "auto"}

   :alas-oikea {:top "calc(100% - 1px)"
                :bottom "auto"}

   :ylos-vasen {:bottom "calc(100% - 1px)"
                :right "0"
                :top "auto"}

   :ylos-oikea {:bottom "calc(100% - 1px)"
                :top "auto"}})

(defn- maarita-suunta [komponentti]
  (let [wrapper-node (r/dom-node komponentti)
        komponentti-node (.-firstChild wrapper-node)
        [_ _ leveys korkeus :as sij] (dom/sijainti komponentti-node)
        viewport-korkeus @dom/korkeus
        etaisyys-oikeaan-reunaan (dom/elementin-etaisyys-viewportin-oikeaan-reunaan komponentti-node)
        etaisyys-ylareunaan (dom/elementin-etaisyys-viewportin-ylareunaan komponentti-node)
        suunta (if (>= etaisyys-ylareunaan korkeus) ;; Ylös jos riittävästi tilaa, muuten alas (alas voi aina scrollata)
                 (if (< etaisyys-oikeaan-reunaan leveys)
                   :ylos-vasen
                   :ylos-oikea)
                 (if (< etaisyys-oikeaan-reunaan leveys)
                   :alas-vasen
                   :alas-oikea))]
    suunta))

(defn leijuke
  "Leijuke komponentti annetulla sisällöllä.
  Optiot:

  :sulje!   funktio, jota kutsutaan kun leijukkeen sulje-raksia tai ESC-näppäintä painetaan
  :luokka   optionaalinen lisäluokka
  :otsikko  Leijukkeessa näytettävä otsikko-teksti"
  [optiot sisalto]
  (let [;; Avautuissuunta määritetään komponentin korkeuden perusteella, joka määrittyy sisällön mukaan.
        ;; Tällöin komponentti rendataan aluksi piilotettuna, jotta suunta saadaan määritettyä.
        suunta (r/atom nil)
        paivita-suunta! (fn [this _]
                          (reset! suunta
                                  (maarita-suunta this)))
        sulje-esc-napilla! (fn [_ event]
                             (when (= 27 (.-keyCode event))
                               ((:sulje! optiot))))]
    (komp/luo
      (komp/piirretty paivita-suunta!)
      (komp/dom-kuuntelija js/window
                           EventType/RESIZE paivita-suunta!
                           EventType/KEYUP sulje-esc-napilla!)
      (fn [{:keys [luokka sulje! otsikko] :as optiot} sisalto]
        (let [suunta @suunta]
          [:div.leijuke-wrapper
           [:div.leijuke {:class luokka
                          :on-click #(.stopPropagation %)
                          :style (if suunta
                                   (avautumissuunta-tyyli suunta)
                                   (merge
                                     ;; Suunta voidaan määrittää vasta kun komponentti on mountattu
                                     ;; ja sen ulottuvuudet määritetty. Siksi piilotetaan ekan
                                     ;; renderin ajaksi.
                                     (avautumissuunta-tyyli :alas-vasen)
                                     {:visibility "hidden"}))}
            [:header
             [napit/sulje-ruksi sulje!]
             [:h4 otsikko]]
            [:div.leijuke-sisalto
             sisalto]]])))))

(defn vihjeleijuke [optiot leijuke-sisalto]
  (let [nakyvissa? (atom false)]
    (fn [optiot leijuke-sisalto]
      [:div.inline-block.yleinen-pikkuvihje.klikattava {:style {:margin-left "16px"}}
       [:div.vihjeen-sisalto {:on-click #(reset! nakyvissa? true)}
        (if @nakyvissa?
          [leijuke (merge
                     {:otsikko [ikonit/ikoni-ja-teksti (ikonit/nelio-info) "Vihje"]
                      :sulje! #(reset! nakyvissa? false)}
                     optiot)
           [:div {:style {:min-width "300px"}}
            leijuke-sisalto]]
          [ikonit/ikoni-ja-teksti
           (ikonit/nelio-info)
           "Ohje"])]])))

(defn otsikko-ja-vihjeleijuke [otsikko-taso otsikko leijuke-optiot leijuke-sisalto]
  [:div
   [(keyword (str "h" otsikko-taso)) {:style {:display :inline-block}} otsikko]
   [:span " "]
   [:span
    [vihjeleijuke
     leijuke-optiot
     leijuke-sisalto]]])

(defn multipage-vihjesisalto [& sisallot]
  (let [sivu-index (atom 0)
        seuraava-index-saatavilla? (fn [sivu-index sisallot]
                                     (< sivu-index (dec (count sisallot))))
        seuraava-index (fn []
                         (when (seuraava-index-saatavilla? @sivu-index sisallot)
                           (swap! sivu-index inc)))
        edellinen-index-saatavilla? (fn [sivu-index]
                                      (> sivu-index 0))
        edellinen-index (fn []
                          (when (edellinen-index-saatavilla? @sivu-index)
                            (swap! sivu-index dec)))
        linkki-elementti (fn [voi-klikata?]
                           (if voi-klikata? :a :span))]

    (fn []
      (let [edellinen-saatavilla (edellinen-index-saatavilla? @sivu-index)
            seuraava-saatavilla (seuraava-index-saatavilla? @sivu-index sisallot)]
        [:div
         [:div (nth sisallot @sivu-index)]

         [:div.text-center
          [(linkki-elementti edellinen-saatavilla)
           (when edellinen-saatavilla
             {:class "klikattava" :on-click edellinen-index})
           "Edellinen vihje"]
          [:span " - "]
          [(linkki-elementti seuraava-saatavilla)
           (when seuraava-saatavilla
             {:class "klikattava " :on-click seuraava-index})
           "Seuraava vihje"]]]))))
