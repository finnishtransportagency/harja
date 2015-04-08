(ns harja.asiakas.main
  (:require [harja.asiakas.ymparisto :as ymparisto]
            [harja.views.main :as main-view]
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]

    ;; Tässä voidaan vaatia tiedonhallinnan juttuja, jotka kytkeytyvät app eventeihin
    ;; ja hakevat tietoa tarpeen mukaan
            [harja.tiedot.hallintayksikot :as hal]

            [reagent.core :as reagent]
            [harja.loki :refer [log]]

            [cljsjs.react]

            [harja.pvm]))

(defn render []
  (reagent/render [main-view/main] (.getElementById js/document "app")))

(defn ^:export harja []
  (ymparisto/alusta {:on-reload render})
  (render)

  ;; Jotkut komponentit haluavat body klikkauksia kuunnella
  (set! (.-onclick js/document.body)
        (fn [e]
          (t/julkaise! {:aihe      :body-klikkaus
                        :tapahtuma e})))

  ;; Kaapataan raportoimattomat virheet ja lähetetään ne backin kautta logiin
  (set! (.-onerror js/window)
        (fn [errorMsg url lineNumber column errorObj]
          (k/post! :raportoi-selainvirhe
                   {:url    url
                    :viesti errorMsg
                    :rivi   lineNumber
                    :sarake column
                    :selain (.-userAgent (.-navigator js/window))
                    :stack (when errorObj (aget errorObj "stack"))})))

  (t/julkaise! {:aihe :harja-ladattu})
  (aset js/window "HARJA_LADATTU" true)
  )


