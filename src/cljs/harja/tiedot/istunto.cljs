(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log]]

            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def kayttaja (atom nil))

(def kayttajan-nimi
  (reaction (when-let [k @kayttaja]
              (str (:etunimi k) " " (:sukunimi k)))))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (tapahtumat/julkaise! (merge {:aihe :kayttajatiedot} k)))

(def oletuskayttoaika-ilman-kayttajasyotteita-sekunteina (* 60 60 2))

(def ajastin-kaynnissa (atom false))

(def kayttoaikaa-jaljella-sekunteina (atom oletuskayttoaika-ilman-kayttajasyotteita-sekunteina))

(defn pysayta-ajastin []
  (reset! ajastin-kaynnissa false))

; TODO Lisää tälle event listener
(defn resetoi-ajastin []
  (reset! kayttoaikaa-jaljella-sekunteina oletuskayttoaika-ilman-kayttajasyotteita-sekunteina))

(def varoitus-nakyvissa (atom false))

(defn kirjaudu-ulos []
  ;; TODO Unmounttaa komponentit
  )

(defn kirjaudu-ulos-jos-kayttoaika-umpeutunut []
  (if (<= @kayttoaikaa-jaljella-sekunteina 0)
    (kirjaudu-ulos)))

(defn nayta-varoitus-aikakatkaisusta []
  (reset! varoitus-nakyvissa true)
  (modal/nayta! {:otsikko "Haluatko jatkaa käyttöä?"
                 :footer  [:span
                           [:button.nappi-kielteinen {:type     "button"
                                                      :on-click #(do (.preventDefault %)
                                                                     (reset! varoitus-nakyvissa false)
                                                                     (kirjaudu-ulos)
                                                                     (modal/piilota!))}
                            "Kirjaudu ulos"]
                           [:button.nappi-myonteinen {:type     "button"
                                                      :on-click #(do (.preventDefault %)
                                                                     (reset! varoitus-nakyvissa false)
                                                                     (resetoi-ajastin)
                                                                     (modal/piilota!))}
                            "Jatka"]
                           ]}
                [:div
                 [:p (str "Et ole käyttänyt Harjaa aktiivisesti vähään aikaan. Sinut kirjataan pian ulos. Haluatko jatkaa käyttöä?")]
                 [:p (str "Käyttöaikaa jäljellä: " "XX:XX")]])) ; TODO Näytä käyttöaika ja jos 0, tekstinä: Harjan käyttö aikakatkaistu kahden tunnin käyttämättömyyden takia. Lataa sivu uudelleen.

(defn varoita-jos-kayttoaika-umpeutumassa []
  (if (and (< @kayttoaikaa-jaljella-sekunteina (* 60 15))
           (false? @varoitus-nakyvissa))
    (nayta-varoitus-aikakatkaisusta)))

(defn kaynnista-ajastin []
  (if (false? @ajastin-kaynnissa)
    (go
      (reset! ajastin-kaynnissa true)
      (loop []
        (<! (timeout 1000))
        (if @ajastin-kaynnissa
          (do
            (reset! kayttoaikaa-jaljella-sekunteina (- @kayttoaikaa-jaljella-sekunteina 1))
            (varoita-jos-kayttoaika-umpeutumassa)
            (kirjaudu-ulos-jos-kayttoaika-umpeutunut)
            (recur)))))))