(ns harja.asiakas.main
  (:require [harja.atom]
            [harja.asiakas.ymparisto :as ymparisto]
            [harja.views.main :as main-view]
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.virhekasittely :as v]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.istunto :as istunto]

            [reagent.core :as reagent]
            [reagent.dom :as rdom]
            [harja.loki :refer [log error]]

            [cljsjs.react]

            [harja.pvm]
            [harja.ui.modal :as modal]
            [harja.tiedot.hairioilmoitukset :as hairiotiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn render []
  (rdom/render [#'main-view/main] (.getElementById js/document "app")))

(defn harja []
  (ymparisto/alusta {:on-reload #(try
                                   (render)
                                   (catch js/Error e
                                     (log "VIRHE RENDERISSÄ")))})
  (render)

  ;; Jotkut komponentit haluavat body-klikkauksia kuunnella
  (.addEventListener js/document.body "click"
    (fn [e]
      (t/julkaise! {:aihe :body-klikkaus
                    :tapahtuma e}))
    ;; Note: React 17 Event Delegation muutosten myötä täytyy body-klikkauksia kuunnella
    ;; capture vaiheessa, jotta tapahtuma saadaan kiinni kuuntelijassa.
    ;; React 17 tottelee stopPropagationia, joten tapahtuman kulku estetään ellei capture vaihetta käytetä.
    ;; https://legacy.reactjs.org/blog/2020/08/10/react-v17-rc.html#fixing-potential-issues
    (clj->js {:capture true}))

  ;; Asennetaan yleisten näppäinten handlerin body tasolle
  (set! (.-onkeydown js/document.body)
        (fn [e]
          (case (.-keyCode e)
            27 (t/julkaise! {:aihe :esc-painettu})
            13 (t/julkaise! {:aihe :enter-painettu})
            nil)))

  (when ymparisto/raportoi-selainvirheet?
    ;; Kaapataan raportoimattomat virheet ja lähetetään ne backin kautta logiin
    (set! (.-onerror js/window)
          (fn [errorMsg url lineNumber column errorObj]
            (error errorObj)
            (k/post! :raportoi-selainvirhe
                     {:url    url
                      :sijainti (-> js/window .-location .-href)
                      :viesti errorMsg
                      :rivi   lineNumber
                      :sarake column
                      :selain (.-userAgent (.-navigator js/window))
                      :stack (when errorObj (aget errorObj "stack"))})
            (v/arsyttava-virhe errorMsg " " url " " lineNumber ":" column " " errorObj))))

  (t/julkaise! {:aihe :harja-ladattu})
  (aset js/window "HARJA_LADATTU" true)
  (go
    (istunto/lisaa-ajastin-tapahtumakuuntelijat)
    (istunto/kaynnista-ajastin!)
    (modal/aloita-urln-kuuntelu)
    (hairiotiedot/tarkkaile-hairioilmoituksia!)
    (k/kaynnista-palvelimen-pingaus)
    (k/kaynnista-yhteysvirheiden-raportointi)
    (k/kysy-pois-kytketyt-ominaisuudet! istunto/pois-kytketyt-ominaisuudet)
    (istunto/aseta-kayttaja (<! (k/post! :kayttajatiedot
                                         (reset! istunto/istunto-alkoi (js/Date.)))))))

(harja)
