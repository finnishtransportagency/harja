(ns harja.views.kartta.pohjavesialueet
  "Pohjavesialueet karttataso. Hakee palvelimelta valitun hallintayksikön alueella olevat pohjavesialueet."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :refer [valittu-hallintayksikko valittu-urakka]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.protokollat :refer [Haku hae]]
            [cognitect.transit :as t]
            [cljs.core.async :refer [<! chan]]
            [clojure.string :as str]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; NÄIDEN pitäisi olla tiedot namespacessa, ei tasossa?


(defonce karttataso-pohjavesialueet (atom false))
(defonce pohjavesialueet-kartalla (atom []))

(def hallintayksikon-pohjavesialueet-haku
  (reify Haku
    (hae [_ teksti]
      (let [ch (chan)
            teksti (str/lower-case teksti)]
        (go (>! ch
                (into []
                      (filter #(or (not= -1 (.indexOf (str/lower-case (:nimi %)) teksti))
                                   (not= -1 (.indexOf (str/lower-case (:tunnus %)) teksti))))
                      @pohjavesialueet-kartalla)))
        ch))))

;; Pohjavesialueet eivät muutu edes vuosittain, joten voimme turvallisesti cachettaa
(defn tallenna-pohjavesialueet
  "Tallentaa ladatut pohjavesialueet annetulle hallintayksikölle localStorageen."
  [hal alueet]
  ;; Disabloitu localStorage tallennus, pitää miettiä miten se tehdään paremmin.
  ;; Tila loppuu kesken pohjavesialueissa ja virhetilanteessa jää toimimaton versio cacheen.
  (comment (try
             (.setItem js/localStorage (str "pohjavesialueet-" hal) (t/write (t/writer :json) alueet))
             (catch :default _
               nil))))

(defn lue-pohjavesialueet
  "Lukee localStoragesta muitissa olevat pohjavesialueet"
  [hal]
  (comment
    (try
      (let [alueet (.getItem js/localStorage (str "pohjavesialueet-" hal))]
        (when alueet
          (t/read (t/reader :json) alueet)))
      (catch :default _
        nil))))

(defn alueet
  "Lisää pohjavesialue tuloksiin frontin kannalta oleelliset kentät."
  [alueet]
  (into []
        (map #(assoc (update-in % [:alue] assoc
                                :color "blue" :fill "blue"
                                :radius 300
                                :stroke {:color "blue" :width 7})
               :type :pohjavesialue))
        alueet))

(run! (let [nakyvissa? @karttataso-pohjavesialueet
            urakka-id (:id @valittu-urakka)]
        (if (or (not nakyvissa?)
                (nil? urakka-id))
          ;; Jos taso ei ole näkyvissä tai urakkaa ei ole valittu => asetetaan heti tyhjä
          (reset! pohjavesialueet-kartalla [])

          ;; Taso näkyvissä ja hallintayksikkö valittu, haetaan alueet
          ;; NOTE: Pohjavesialueiden tallentaminen ja hakeminen localstoragesta on otettu pois käytöstä.
          (if-let [pa nil #_(lue-pohjavesialueet hal)]
            ;; muistissa oli aiemmin ladatut alueet, palautetaan ne
            (reset! pohjavesialueet-kartalla (alueet pa))

            ;; Ei muistissa, haetaan ne
            (go
              (let [res (<! (k/post! :hae-urakan-pohjavesialueet urakka-id))]
                ;; NOTE: Pohjavesialueiden tallentaminen ja hakeminen localstoragesta on otettu pois käytöstä.
                #_(tallenna-pohjavesialueet hal res)

                (when (= urakka-id (:id @valittu-urakka))
                  ;; Jos urakka ei ole ehditty vaihtaa ennen kuin vastaus tuli
                  (reset! pohjavesialueet-kartalla (alueet res)))))))))

  
