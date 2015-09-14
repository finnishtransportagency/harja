(ns harja.asiakas.main
  (:require [harja.atom]
            [harja.asiakas.ymparisto :as ymparisto]
            [harja.views.main :as main-view]
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]

            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.istunto :as istunto]
            
            [reagent.core :as reagent]
            [harja.loki :refer [log]]

            [cljsjs.react]
            
            [harja.pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn render []
  (reagent/render [#'main-view/main] (.getElementById js/document "app")))
    
(defn ^:export harja []
  (ymparisto/alusta {:on-reload #(try
                                   (render)
                                   (catch js/Error e
                                     (log "VIRHE RENDERISSÄ")))})
  (render)

  ;; Jotkut komponentit haluavat body-klikkauksia kuunnella
  (set! (.-onclick js/document.body)
        (fn [e]
          (t/julkaise! {:aihe      :body-klikkaus
                        :tapahtuma e})))

  ;; Asennetaan yleisten näppäinten handlerin body tasolle
  (set! (.-onkeydown js/document.body)
        (fn [e]
          (case (.-keyCode e)
            27 (t/julkaise! {:aihe :esc-painettu})
            13 (t/julkaise! {:aihe :enter-painettu})
            nil)))

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
  (go
    (istunto/lisaa-ajastin-tapahtumakuuntelijat)
    (istunto/kaynnista-ajastin)
    (istunto/aseta-kayttaja (<! (k/post! :kayttajatiedot
                                         (reset! istunto/istunto-alkoi (js/Date.)))))))


