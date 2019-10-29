(ns harja.tyokalut.tuck
  "Tuck-apureita"
  (:require [cljs.test :as t :refer-macros [is]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [cljs.core.async :as async :refer [<! >! chan timeout unsub pub put! close!]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def palveukutsu-viive (atom {})) ;; Palvelukontekstin tunniste -> palvelukutsu-eventin id

(def ^:dynamic *kutsu-jarjestys* nil)

(defonce jarjestys-kanava (chan))

(defonce jarjestys-pub (pub jarjestys-kanava :kutsu-kasitelty?))

(defn- palvelukutsu*
  "Optiot:
   viive              Aika millisekunteina, jonka verran palvelupyynnön lähetystä
                      pidätellään. Vaatii kaveriksi myös tunniste-argumentin.
                      Mikäli odotusaikana saman tunnisteen alla yritetään lähettää
                      useita pyyntöjä, vain tuorein näistä lähetetään.

   tunniste           Tunnisteen kannattaa olla jokin kontekstia kuvaava avain.
                      Palvelun nimikin käy, mutta kannattaa kuvata mieluummin konteksti,
                      sillä samaa palvelua saatetaan kutsua eri kontekstissa."
  [app palvelu argumentit {:keys [onnistui onnistui-parametrit viive tunniste
                                  epaonnistui epaonnistui-parametrit lahetetty
                                  paasta-virhe-lapi? palauta-kanava?]
                           jarjestys-sub ::jarjestys-sub}]
  (assert (or (nil? viive)
              (and viive tunniste))
          "Viive vaatii tunnisteen!")

  (let [lahetetty! (when lahetetty (tuck/send-async! lahetetty))
        onnistui! (when onnistui (apply tuck/send-async! onnistui
                                        onnistui-parametrit))
        epaonnistui! (when epaonnistui (apply tuck/send-async! epaonnistui
                                              epaonnistui-parametrit))
        suorituskanava (try
                          (go
                            (let [event-tunniste (when tunniste (gensym tunniste))]
                              (when (and tunniste viive)
                                (swap! palveukutsu-viive assoc tunniste event-tunniste)
                                (<! (timeout viive)))

                              ;; Jos viive käytössä:
                              ;; Mikäli viiveen jälkeen palvelukutsu-viive atomissa on palvelukutsun
                              ;; tunnisteena edelleen sama, tämän eventin generoima merkkijono,
                              ;; niin silloin palvelukutsua ei ole yritetty tehdä uudestaan
                              ;; timeoutin aikana ja tämä kutsu saa lähteä.
                              ;; Muussa tapauksessa on uusi timeouttaava palvelukutsu jonossa,
                              ;; joten tämä kutsu hylätään.

                              (if (or
                                    (not viive)
                                    (and viive tunniste (= (tunniste @palveukutsu-viive) event-tunniste)))
                                (do
                                  (when lahetetty! (lahetetty!))
                                  (let [vastaus (if argumentit
                                                  (<! (k/post! palvelu argumentit nil (boolean paasta-virhe-lapi?)))
                                                  (<! (k/get! palvelu nil (boolean paasta-virhe-lapi?))))
                                        vastauksen-kasittely (fn [vastaus]
                                                               (if (k/virhe? vastaus)
                                                                 (when epaonnistui! (epaonnistui! vastaus))
                                                                 (when onnistui! (onnistui! vastaus))))
                                        kasittele-vastaus-jarjestyksessa (fn [vastaus]
                                                                           (vastauksen-kasittely vastaus)
                                                                           (unsub jarjestys-pub :kutsu-kasitelty? jarjestys-sub)
                                                                           (close! jarjestys-sub)
                                                                           (swap! *kutsu-jarjestys* rest)
                                                                           (put! jarjestys-kanava {:kutsu-kasitelty? true}))]
                                    (cond
                                      (nil? *kutsu-jarjestys*) (vastauksen-kasittely vastaus)
                                      (= (first (deref *kutsu-jarjestys*)) palvelu) (kasittele-vastaus-jarjestyksessa vastaus)
                                      :else (<! (go-loop [aikaisempi-kutsu-kasitelty (<! jarjestys-sub)]
                                                  (if (= (first (deref *kutsu-jarjestys*)) palvelu)
                                                    (kasittele-vastaus-jarjestyksessa vastaus)
                                                    (recur (<! jarjestys-sub))))))
                                    :kutsu-kasitelty))
                                (do (log "Hylätään palvelukutsu, viiveen aikana on uusi jonossa.")
                                    :kutsu-hylatty))))
                          (catch :default e
                            (when epaonnistui! (epaonnistui! nil))
                            (throw e)))]
    (if palauta-kanava?
      suorituskanava
      app)))

(defn get!
  ([palvelu optiot]
   (get! nil palvelu optiot))
  ([app palvelu optiot]
   (palvelukutsu* app palvelu nil optiot)))

(defn post!
  ([palvelu argumentit optiot]
   (post! nil palvelu argumentit optiot))
  ([app palvelu argumentit optiot]
   (palvelukutsu* app palvelu argumentit optiot)))

(defn e-kanavalla!
  "Antaa paluukanavan tuck-eventille. Palauttaa kanavan, josta vastauksen voi lukea.
   Tällä voi integroida esim. Gridin tallennuksen helposti Tuck-eventtiin, kunhan myös itse eventti
   tukee paluukanavan käsittelyä."
  [e! tapahtuma & tapahtuma-args]
  (let [ch (chan)]
    (e! (apply tapahtuma (conj (vec tapahtuma-args) ch)))
    ch))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord AloitaViivastettyjenEventtienKuuntelu [viive kanava])

(extend-protocol tuck/Event
  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))

  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))
  AloitaViivastettyjenEventtienKuuntelu
  (process-event [{:keys [viive kanava]} app]
    (tuck/action! (fn [e!]
                    (go-loop [viivastetyt-eventit {}
                              [tunniste event] (<! kanava)]
                             (let [{:keys [tunnisteen-kanava saije]} (get viivastetyt-eventit tunniste)
                                   uusi-tunnisteen-kanava (chan)]
                               (when (and saije (nil? (async/poll! saije)))
                                 (async/put! tunnisteen-kanava true))
                               (recur (assoc viivastetyt-eventit
                                        tunniste {:tunnisteen-kanava uusi-tunnisteen-kanava
                                                  :saije (go (<! (timeout viive))
                                                             (if (async/poll! uusi-tunnisteen-kanava)
                                                               false
                                                               (do (e! event)
                                                                   true)))})
                                      (<! kanava))))))
    app))
