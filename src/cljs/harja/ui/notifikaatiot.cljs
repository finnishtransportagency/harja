(ns harja.ui.notifikaatiot
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(def +notifikaatio-ikoni+ "images/harja_favicon.png")
(def +notifikaatio-aani+ "sounds/notifikaatio.mp3")

(defn notification-api-tuettu? []
  (some? (.-Notification js/window)))

(def kayta-web-notification-apia?
  (notification-api-tuettu?))

(defn notifikaatiolupa? []
  (if kayta-web-notification-apia?
    (= (.-permission js/Notification) "granted")
    false))

(def notifikaatiolupaa-pyydetty? (atom false))

(defn soita-merkkiaani []
  (let [aani (js/Audio. +notifikaatio-aani+)]
    (.play aani)))

(defn pyyda-notifikaatiolupa
  "Pyytää käyttäjältä lupaa näyttää web-notifikaatioita,
  jos lupaa ei ole annettu eikä pyyntöä ole jo kertaalleen esitetty."
  []
  (when kayta-web-notification-apia? ;; Tsekkaa tämä ensin, jottei kaadu jos Notification API ei ole tuettu
    (when (and
            (not= (.-permission js/Notification) "granted")
            (not @notifikaatiolupaa-pyydetty?))
      (reset! notifikaatiolupaa-pyydetty? true)
      (.requestPermission js/Notification))))

(defn- nayta-web-notifikaatio [otsikko teksti]
  (if (notifikaatiolupa?)
    ;; Mobiiliympäristössä (ainakin Android Chrome) tämä ei toimi, vaan tulisi käyttää Service Workeria
    ;; (jotta notifikaatiot toimivat myös jos käyttäjä sulkee selaimen).
    ;; Tälle ei toistaiseksi ole tehty tukea, vaan yritetään näyttää notifikaatio normaalisti.
    ;; Jos ei onnistu, failaa hiljaa.
    (try
      (js/Notification. otsikko #js {:body teksti
                                     :icon +notifikaatio-ikoni+})
      (catch js/Error e
        (log "Notifikaation luonti epäonnistui: " e)))))

(defn- yrita-nayttaa-web-notifikaatio
  "Näyttää web-notifikaation, jos käyttäjä on antanut siihen luvan.
   Muussa tapauksessa pyytää lupaa."
  [otsikko teksti]
  (if (notifikaatiolupa?)
    (nayta-web-notifikaatio otsikko teksti)
    (pyyda-notifikaatiolupa)))

(defn luo-notifikaatio
  "Näyttää web-notifikaation ja soittaa ääniefektin.
   Notifikaatio näytetään vain jos käyttäjä on antanut tähän
   luvan. Jos lupaa ei ole vielä pyydetty, pyydetään.

   Optiot:
   aani?      Soitetaanko äänimerkki (true/false), default true.
              Jos false ja web-notifikaatioita ei ole sallittu, ei luoda minkäänlaista
              notifikaatiota"
  ([otsikko teksti] (luo-notifikaatio otsikko teksti {}))
  ([otsikko teksti {:keys [aani?] :as optiot}]
   (log "Luodaan notifikaatio. Otsikko: " (pr-str otsikko) " Teksti: " (pr-str teksti) " Optiot: " (pr-str optiot))
   (when kayta-web-notification-apia?
     (yrita-nayttaa-web-notifikaatio otsikko teksti))
    ;; Notification API tukee äänen soittamista suoraan,
    ;; mutta ATM tämä on huonosti tuettu selaimissa.
   (when (or (nil? aani?) (boolean aani?))
     (soita-merkkiaani))))
