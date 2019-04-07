(ns harja.ui.dom
  "Yleisiä apureita DOMin ja selaimen hallintaan"
  (:require [reagent.core :as r]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! timeout] :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn sisalla?
  "Tarkistaa onko annettu tapahtuma tämän React komponentin sisällä."
  ([komponentti tapahtuma] (sisalla? komponentti tapahtuma nil))
  ([komponentti tapahtuma {elementti? :elementti?}]
   (let [dom (r/dom-node komponentti)
         elt (if elementti? tapahtuma (.-target tapahtuma))]
     (loop [ylempi (.-parentNode elt)]
       (if (or (nil? ylempi)
               (= ylempi js/document.body))
         false
         (if (= dom ylempi)
           true
           (recur (.-parentNode ylempi))))))))


(def ie? (let [ua (-> js/window .-navigator .-userAgent)]
           (or (not= -1 (.indexOf ua "MSIE "))
               (not= -1 (.indexOf ua "Trident/"))
               ;; Edge perustuu IE:hen, joskaan ei ole enää sama selain, mutta laskemme sen IE:ksi kaiken varalta
               (not= -1 (.indexOf ua "Edge/")))))

(defn maarita-ie-versio-user-agentista
  "Määrittää IE-version user-agentin tietojen perusteella. Jos käytössä ei ole IE tai versiota ei voida määrittää,
  palauttaa versioksi nil."
  [user-agent-text]
  (let [ie? (not= -1 (.indexOf user-agent-text "MSIE "))
        ie-versio (when ie?
                    (let [ie-alku-index (.indexOf user-agent-text "MSIE ")
                          ie-versio-ja-loput-teksti (subs user-agent-text ie-alku-index (+ ie-alku-index 10))
                          ie-versio-teksti (re-find (re-pattern "\\d+") ie-versio-ja-loput-teksti)]
                      (js/parseInt ie-versio-teksti)))]
    ie-versio))


(def ei-tuettu-ie?
  "Kaikissa vanhoissa IE-versioissa (< 11) pitäisi olla user agentin tiedoissa kohta MSIE, jota
   seuraava numero kertoo version. Versiossa 11 tätä ei välttämättä ole, mutta sillä ei ole väliä, koska
   tarkoitus on havaita nimenomaan versiota 11 vanhemmat selaimet."
  (let [ua (-> js/window .-navigator .-userAgent)
        ie-versio (maarita-ie-versio-user-agentista ua)]
    (and (integer? ie-versio) (<= 10 ie-versio))))

(defonce korkeus (r/atom (-> js/window .-innerHeight)))
(defonce leveys (r/atom (-> js/window .-innerWidth)))

(defonce ikkunan-koko
         (reaction [@leveys @korkeus]))

(defn- ikkunan-koko-muuttunut [& _]
  (t/julkaise! {:aihe :ikkunan-koko-muuttunut :leveys @leveys :korkeus @korkeus}))

(defonce ikkunan-koko-tapahtuman-julkaisu
  (do (add-watch korkeus ::ikkunan-koko-muuttunut ikkunan-koko-muuttunut)
      (add-watch leveys ::ikkunan-koko-muuttunut ikkunan-koko-muuttunut)
      true))

(defonce koon-kuuntelija (do (set! (.-onresize js/window)
                                   (fn [_]
                                     (reset! korkeus (-> js/window .-innerHeight))
                                     (reset! leveys (-> js/window .-innerWidth))))
                             true))

(defn elementti-idlla [id]
  (.getElementById js/document (name id)))

(defn- elementti-idlla-odota
  "Pollaa DOMia 10ms välein kunnes annettu elementti löytyy. Palauttaa kanavan, josta
  elementin voi lukea."
  [id]
  (go (loop [elt (.getElementById js/document id)]
        (if elt
          elt
          (do #_(log "odotellaan elementtiä " id)
            (<! (timeout 10))
            (recur (.getElementById js/document id)))))))

(defn sijainti
  "Laskee DOM-elementin sijainnin, palauttaa [x y w h]."
  [elt]
  (assert elt (str "Ei voida laskea sijaintia elementille null"))
  (let [r (.getBoundingClientRect elt)
        sijainti [(.-left r) (.-top r) (- (.-right r) (.-left r)) (- (.-bottom r) (.-top r))]]
    sijainti))

(defn offset-korkeus [elt]
  (loop [offset (.-offsetTop elt)
         parent (.-offsetParent elt)]
    (if (or (nil? parent)
            (= js/document.body parent))
      offset
      (recur (+ offset (.-offsetTop parent))
             (.-offsetParent parent)))))

(defn sijainti-sailiossa
  "Palauttaa elementin sijainnin suhteessa omaan säiliöön."
  [elt]
  (let [[x1 y1 w1 h1] (sijainti elt)
        [x2 y2 w2 h2] (sijainti (.-parentNode elt))]
    [(- x1 x2) (- y1 y2) w1 h1]))

(defn scroll-sijainti-ylareunaan []
  (-> js/window .-window .-scrollY))

(defn elementin-etaisyys-viewportin-alareunaan [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (- @korkeus (.-bottom r))]
    etaisyys))

(defn elementin-etaisyys-viewportin-ylareunaan-alareunasta
  [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (.-bottom r)]
    etaisyys))

(defn elementin-etaisyys-viewportin-ylareunaan
  [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (.-top r)]
    etaisyys))

(defn elementin-etaisyys-dokumentin-ylareunaan
  [solmu]
  (+ (scroll-sijainti-ylareunaan)
     (elementin-etaisyys-viewportin-ylareunaan solmu)))

(defn elementin-etaisyys-viewportin-oikeaan-reunaan
  [solmu]
  (let [r (.getBoundingClientRect solmu)
        etaisyys (- @leveys (.-right r))]
    etaisyys))

(defn elementin-korkeus
  [solmu]
  (let [r (.getBoundingClientRect solmu)
        korkeus (.-height r)]
    korkeus))

(defn elementin-leveys
  [solmu]
  (let [r (.getBoundingClientRect solmu)
        leveys (.-width r)]
    leveys))

(defn lataus-komponentille
  "Jos komponentin luominen kestää pitkää, tämän voi wrapata komponentin ympärille, jolloinka
   näytetään lataus gif sen aikaa, että react on kerennyt mountata komponentin."
  [& args]
  (let [naytettava-osio (atom {:loader? true
                               :komponentti? false})
        loader (fn [viesti]
                 [:div
                  [:img {:src "images/ajax-loader.gif"}]
                  [:span (str " " viesti)]])
        komponentti (fn [args naytettava-osio]
                      (r/create-class
                        {:component-did-mount (fn [_]
                                                (when (:komponentti? @naytettava-osio)
                                                  (swap! naytettava-osio assoc :loader? false)
                                                  ;; kutsutaa flush, koska reagent ei välittömästi renderöi
                                                  ;; uutta tilaaa eikä haluta näyttää lataus giffiä ja itse
                                                  ;; komponenttia yhtäaikaa
                                                  (r/flush)))
                         :reagent-render (fn [args naytettava-osio]
                                           args)}))]
    (r/create-class
      {:component-did-mount (fn [this]
                              (swap! naytettava-osio assoc :komponentti? true))
       :reagent-render
       (fn [& args]
         (let [{:keys [loader? komponentti?]} @naytettava-osio
               [viesti args] (if (map? (first args))
                               [(:viesti (first args)) (vec (rest args))]
                               [nil (vec args)])]
           [:div
            ;; Key metadata on näissä olennaiset, koska jos niitä ei anneta, niin komponentti
            ;; remountataan, silloin kun sen indexi muuttuu ilman avainta. Eli 'loader' ei sinänsä
            ;; tarvitsisi avainta, mutta komponentti tarvitsee.
            (when loader?
              ^{:key "loader"}
              [loader viesti])
            (when komponentti?
              ^{:key "komponentti"}
              [komponentti args naytettava-osio])]))})))
