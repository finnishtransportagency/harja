(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log tarkkaile!]]

            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.modal :as modal]
            [goog.dom :as dom]
            [goog.events :as events]
            [reagent.core :as reagent]
            [goog.net.cookies :as cookie])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def kayttaja (atom nil))

(def kayttajan-nimi
  (reaction (when-let [k @kayttaja]
              (str (:etunimi k) " " (:sukunimi k)))))

(def istunto-alkoi (atom nil))

(defn aseta-kayttaja [k]
  (reset! kayttaja k)
  (tapahtumat/julkaise! (merge {:aihe :kayttajatiedot} k)))

(def oletuskayttoaika-ilman-kayttajasyotteita-sekunteina (* 60 60 2))
(def istunto-aikakatkaistu? (atom false))
(def ajastin-paalla? (atom false))
(def ajastin-taukotilassa? (atom false))
(def ajastimen-paivitys-paalla? (atom false))
(def kayttoaikaa-jaljella-sekunteina (atom oletuskayttoaika-ilman-kayttajasyotteita-sekunteina))

(defn pysayta-ajastin! []
  (reset! ajastin-paalla? false))

(defn resetoi-ajastin-jos-modalia-ei-nakyvissa! []
  (when (false? (:nakyvissa? @modal/modal-sisalto))
    (reset! kayttoaikaa-jaljella-sekunteina oletuskayttoaika-ilman-kayttajasyotteita-sekunteina)))

(defn resetoi-ajastin! []
  (reset! kayttoaikaa-jaljella-sekunteina oletuskayttoaika-ilman-kayttajasyotteita-sekunteina))

(defn lisaa-ajastin-tapahtumakuuntelijat []
  (events/listen (dom/getWindow) (.-MOUSEMOVE events/EventType) #(resetoi-ajastin-jos-modalia-ei-nakyvissa!))
  (events/listen (dom/getWindow) (.-KEYDOWN events/EventType) #(resetoi-ajastin-jos-modalia-ei-nakyvissa!))
  (events/listen (dom/getWindow) (.-TOUCHMOVE events/EventType) #(resetoi-ajastin-jos-modalia-ei-nakyvissa!))
  (events/listen (dom/getWindow) (.-SCROLL events/EventType) #(resetoi-ajastin-jos-modalia-ei-nakyvissa!))
  (events/listen (dom/getWindow) (.-CLICK events/EventType) #(resetoi-ajastin-jos-modalia-ei-nakyvissa!)))

(defn aikakatkaise-istunto! []
  (reset! istunto-aikakatkaistu? true)
  (reset! ajastimen-paivitys-paalla? false))

(defn aikakatkaise-istunto-jos-kayttoaika-umpeutunut! []
  (when (<= @kayttoaikaa-jaljella-sekunteina 0)
    (log "Käyttöaika umpeutui.")
    (reset! ajastin-paalla? false)
    (aikakatkaise-istunto!)))

(defn nayta-kayttoaika []
  (let [minuutit (int (/ @kayttoaikaa-jaljella-sekunteina 60))
        sekunnit (- @kayttoaikaa-jaljella-sekunteina (* minuutit 60))]
    (str minuutit ":" (when (< sekunnit 10) "0") sekunnit)))

(defn nayta-varoitus-aikakatkaisusta! []
  (let [kayttoaikaa-jaljella? (> @kayttoaikaa-jaljella-sekunteina 0)]
    (modal/nayta! {:otsikko (if kayttoaikaa-jaljella? "Haluatko jatkaa käyttöä?" "Käyttö aikakatkaistu")
                   :footer  (if kayttoaikaa-jaljella?
                              [:span
                               [:button.nappi-kielteinen {:type     "button"
                                                          :on-click #(do (.preventDefault %)
                                                                         (aikakatkaise-istunto!)
                                                                         (modal/piilota!))}
                                "Kirjaudu ulos"]
                               [:button.nappi-myonteinen {:type     "button"
                                                          :on-click #(do (.preventDefault %)
                                                                         (resetoi-ajastin!)
                                                                         (modal/piilota!))}
                                "Jatka käyttöä"]])
                   }
                  [:div
                   (if kayttoaikaa-jaljella?
                     [:span
                      [:p (str "Et ole käyttänyt Harjaa aktiivisesti pian kahteen tuntiin. Jos et jatka käyttöä, Harja suljetaan. Haluatko jatkaa käyttöä?")]
                      [:p (str "Käyttöaikaa jäljellä: " (nayta-kayttoaika))]]
                     [:p (str "Harjan käyttö aikakatkaistu kahden tunnin käyttämättömyyden takia. Lataa sivu uudelleen.")])])))

(defn varoita-jos-kayttoaika-umpeutumassa! []
  (when (and (< @kayttoaikaa-jaljella-sekunteina (* 60 5)))
    (nayta-varoitus-aikakatkaisusta!)))                      ; Kutsutaan tarkoituksella joka kerta, jotta modalin sisältö päivittyy

(defn kaynnista-ajastin! []
  (reset! ajastin-paalla? true)
  (if (false? @ajastimen-paivitys-paalla?)
    (go
      (reset! ajastimen-paivitys-paalla? true)
      (loop []
        (<! (timeout 1000))
        (when (and @ajastin-paalla? (not @ajastin-taukotilassa?))
          (reset! kayttoaikaa-jaljella-sekunteina (- @kayttoaikaa-jaljella-sekunteina 1))
          (varoita-jos-kayttoaika-umpeutumassa!)
          (aikakatkaise-istunto-jos-kayttoaika-umpeutunut!))
        (when @ajastimen-paivitys-paalla?
          (recur))))))

;; Testikäytön ominaisuudet

(defonce testikayttajat
  (reaction (let [k @kayttaja]
              (:testikayttajat k))))

(defonce testikayttaja
  (reaction (let [tk (cookie/get "testikayttaja")]
              (some #(when (= tk (:kayttajanimi %)) %) @testikayttajat))))

(defn testikaytto-mahdollista? []
  (not (empty? @testikayttajat)))

(defn aseta-testikayttaja! [kayttaja]
  (if-not kayttaja
    (cookie/remove "testikayttaja")
    (cookie/set "testikayttaja" (:kayttajanimi kayttaja)))
  (.reload js/window.location))


(defonce pois-kytketyt-ominaisuudet (atom nil)) ;; tähän luetaan set

(defn ominaisuus-kaytossa? [k]
  (let [pko @pois-kytketyt-ominaisuudet]
    (not (and (set? pko) (contains? pko k)))))
