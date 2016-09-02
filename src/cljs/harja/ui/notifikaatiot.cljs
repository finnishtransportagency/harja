(ns harja.ui.notifikaatiot
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(defn tarkista-notification-api-tuki []
  (some? (.-Notification js/window)))

;; Käytetään Notification Web APIa jos selain tukee sitä,
;; muuten fallbackinä pelkkä äänen soittaminen.
(def kayta-web-notification-apia?
  (tarkista-notification-api-tuki))

(defn notifikaatiolupa? []
  (= (.-permission js/Notification) "granted"))

(def notifikaatiolupaa-pyydetty? (atom false))

(defn- soita-aani []
  ;; TODO Implement me
  (log "Soitetaan ääni: BLING!"))

(defn pyyda-notifikaatiolupa
  "Pyytää käyttäjältä lupaa näyttää web-notifikaatioita, jos lupaa ei ole jo annettu
   eikä ole jo kerran pyydetty (ei häiritä useilla pyynnöillä jos vastaus on ollut kielteinen)"
  []
  (when (and (not= (.-permission js/Notification) "granted")
             (not @notifikaatiolupaa-pyydetty?))
    (reset! notifikaatiolupaa-pyydetty? true)
    (.requestPermission js/Notification)))

(defn- nayta-web-notifikaatio [otsikko teksti]
  ;; TODO Implement me
  )

(defn- yrita-nayttaa-web-notifikaatio
  "Näyttää web-notifikaation, jos käyttäjä on antanut siihen luvan.
   Muussa tapauksessa pyytää lupaa."
  [otsikko teksti]
  (if (notifikaatiolupa?)
    (pyyda-notifikaatiolupa)
    (nayta-web-notifikaatio otsikko teksti)))

(defn luo-notifikaatio [otsikko teksti]
  (if kayta-web-notification-apia?
    (yrita-nayttaa-web-notifikaatio otsikko teksti)
    (soita-aani)))