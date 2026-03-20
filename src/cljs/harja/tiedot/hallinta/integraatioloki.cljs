(ns harja.tiedot.hallinta.integraatioloki
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!] :as async]
            [harja.asiakas.kommunikaatio :as k]
            [harja.atom :refer-macros [reaction<!]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn hae-jarjestelmien-integraatiot []
  (k/post! :hae-jarjestelmien-integraatiot nil))

(defn hae-integraatiotapahtumien-maarat [jarjestelma integraatio aikavali]
  (k/post! :hae-integraatiotapahtumien-maarat
    (merge {:jarjestelma jarjestelma
            :integraatio integraatio}
      (when aikavali
        {:alkaen (first aikavali)
         :paattyen (second aikavali)}))))

(defn hae-integraation-tapahtumat [jarjestelma integraatio aikavali hakuehdot]
  (k/post! :hae-integraatiotapahtumat
           (merge {:jarjestelma (:jarjestelma jarjestelma)
                   :integraatio integraatio
                   :hakuehdot hakuehdot}
                  (when aikavali
                    {:alkaen (first aikavali)
                     :paattyen (second aikavali)}))))

(defn hae-integraatiotapahtuman-viestit [tapahtuma-id]
  (k/post! :hae-integraatiotapahtuman-viestit tapahtuma-id))

(defn hae-epaillyt-duplikaattikuittaukset [aikavali]
  (k/post! :hae-epaillyt-duplikaattikuittaukset
           (when aikavali
             {:alkaen (first aikavali)
              :paattyen (second aikavali)})))

(def nakymassa? (atom false))

(defonce jarjestelmien-integraatiot (reaction<! [nakymassa? @nakymassa?]
                                                (when nakymassa?
                                                  (hae-jarjestelmien-integraatiot))))



(defonce valittu-jarjestelma (atom nil))
(defonce valittu-integraatio (atom nil))
(defonce valittu-aikavali (atom (pvm/tanaan-aikavali)))
(defonce hakuehdot (atom {:tapahtumien-tila :kaikki}))
(defonce nayta-uusimmat-tilassa? (atom true))
;; Kun seurataan ulkoista integraatiolokiin linkkaavaa urlia - näitä lokitetaan ja linkin voi avata suoraan slackista
(defonce tapahtuma-id (atom nil))
(defonce tultiin-urlin-kautta (atom nil))

(defonce nayta-graafit? (atom false))
(defonce nayta-kutsutut-integraatiot? (atom false))

(defn nayta-graafit! []
  (js/console.log "click" @nayta-graafit?)
  (if @nayta-graafit?
    (reset! nayta-graafit? false)
    (reset! nayta-graafit? true)))

(defn nayta-kutsutut-integraatiot! []
  (js/console.log "click" @nayta-kutsutut-integraatiot?)
  (if @nayta-kutsutut-integraatiot?
    (reset! nayta-kutsutut-integraatiot? false)
    (reset! nayta-kutsutut-integraatiot? true)))

(defn tyhjenna-hakuehdot! []
  (reset! hakuehdot
          (dissoc @hakuehdot :otsikot :parametrit :viestin-sisalto)))

(def tapahtumien-maarat (atom []))
(def maarat-granulariteetti (atom :day))
(def haetut-tapahtumat (atom [])) ;; nil jos haku käynnissä, [] jos tyhjä
(def epaillyt-duplikaattikuittaukset (atom :ei-kaytossa))
(def hae-automaattisesti? (atom false))

(def duplikaattikuittaukset-ladataan :ladataan)
(def duplikaattikuittaukset-epaonnistui :epaonnistui)

(defn nayta-duplikaattikuittaukset?
  [jarjestelma integraatio]
  (and (= "tloik" (:jarjestelma jarjestelma))
       (= "toimenpiteen-lahetys" integraatio)))

(defn hae-tapahtumat! []
  (let  [valittu-jarjestelma @valittu-jarjestelma
         valittu-integraatio @valittu-integraatio
         valittu-aikavali @valittu-aikavali
         nakymassa? @nakymassa?
         hakuehdot @hakuehdot
         hae-duplikaatit? (nayta-duplikaattikuittaukset? valittu-jarjestelma valittu-integraatio)]
    (when nakymassa?
      (reset! haetut-tapahtumat nil)
      (reset! tapahtumien-maarat nil)
      (reset! epaillyt-duplikaattikuittaukset (if hae-duplikaatit? duplikaattikuittaukset-ladataan :ei-kaytossa))
      ;; Palvelimen päässä on määritelty, että maksimissaan 500 tulosta palautetaan
      (go (let [tapahtumat (<! (hae-integraation-tapahtumat valittu-jarjestelma valittu-integraatio valittu-aikavali hakuehdot))
                maarat-vastaus (<! (hae-integraatiotapahtumien-maarat valittu-jarjestelma valittu-integraatio valittu-aikavali))
                duplikaattivastaus (when hae-duplikaatit?
                                    (<! (hae-epaillyt-duplikaattikuittaukset valittu-aikavali)))]
            (reset! haetut-tapahtumat tapahtumat)
            (reset! tapahtumien-maarat (:maarat maarat-vastaus))
            (reset! maarat-granulariteetti (:granulariteetti maarat-vastaus))
            (when hae-duplikaatit?
              (reset! epaillyt-duplikaattikuittaukset
                      (if (k/virhe? duplikaattivastaus)
                        duplikaattikuittaukset-epaonnistui
                        duplikaattivastaus)))
            (when @tultiin-urlin-kautta
              (go-loop [aukinainen-vetolaatikko (aget (.getElementsByClassName js/document "vetolaatikko-auki") 0)
                        kertoja-loopattu 0]
                       (if (or (= kertoja-loopattu 10) aukinainen-vetolaatikko)
                         (try (.scrollIntoView aukinainen-vetolaatikko true)
                              (catch :default e
                                (log "VIRHE: Skrollaaminen avattuun vetolaatikkoon ei onnistunut" e)))
                         (do
                           (<! (async/timeout 1200))
                           (recur (aget (.getElementsByClassName js/document "vetolaatikko-auki") 0)
                                  (inc kertoja-loopattu)))))
              (reset! tultiin-urlin-kautta nil))
            tapahtumat)))))
