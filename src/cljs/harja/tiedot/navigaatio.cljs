(ns harja.tiedot.navigaatio
  "Tämä nimiavaruus hallinnoi sovelluksen navigoinnin. Sisältää atomit, joilla eri sivuja ja polkua 
sovelluksessa ohjataan sekä kytkeytyy selaimen osoitepalkin #-polkuun ja historiaan. Tämä nimiavaruus
ei viittaa itse näkymiin, vaan näkymät voivat hakea täältä tarvitsemansa navigointitiedot."
 
  (:require
   ;; Reititykset
   [goog.events :as events]
   [goog.Uri :as Uri]
   [goog.history.EventType :as EventType]
   [reagent.core :refer [atom]]
   
   [harja.asiakas.tapahtumat :as t]
   [harja.tiedot.urakat :as ur])
  
   (:require-macros [cljs.core.async.macros :refer [go]])
  
  (:import goog.History))


(declare kasittele-url! paivita-url)

;; Atomi, joka sisältää valitun sivun
(defonce sivu (atom :urakat))

;; Kartan koko. Voi olla aluksi: S (pieni, urakan pääsivulla), M (puolen ruudun leveys) tai L (koko leveys)
(def kartan-koko "Kartan koko" (atom :M))

(defn vaihda-kartan-koko! [uusi-koko]
  (reset! kartan-koko uusi-koko)
  (t/julkaise! {:aihe :kartan-koko-vaihdettu :uusi-koko uusi-koko}))

;; Atomi, joka sisältää valitun hallintayksikön
(def valittu-hallintayksikko "Tällä hetkellä valittu hallintayksikkö (tai nil)" (atom nil))

;; Atomi, joka sisältää valitun urakan
(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

;; Atomi, joka sisältää valitun hallintayksikön urakat
(def urakkalista "Hallintayksikon urakat" (atom nil))

;; Rajapinta hallintayksikön valitsemiseen, jota viewit voivat kutsua
(defn valitse-hallintayksikko [yks]
  (reset! valittu-hallintayksikko yks)
  (reset! urakkalista nil)
  (reset! valittu-urakka nil)
  (paivita-url)
  (if yks
    (do
      (go (reset! urakkalista (<! (ur/hae-hallintayksikon-urakat yks))))
      (t/julkaise! (assoc yks :aihe :hallintayksikko-valittu)))
    (t/julkaise! {:aihe :hallintayksikkovalinta-poistettu})))

(defn valitse-urakka [ur]
  (reset! valittu-urakka ur)
  (paivita-url)
  (if ur
    (t/julkaise! (assoc ur :aihe :urakka-valittu))
    (t/julkaise! {:aihe :urakkavalinta-poistettu})))

;; Quick and dirty history configuration.
(defonce historia (let [h (History. false)]
  (events/listen h EventType/NAVIGATE #(kasittele-url! (.-token %)))
  h))

;; asettaa oikean sisällön urliin ohjelman tilan perusteella
(defn paivita-url []
  (let [url (str (name @sivu)
                 "?"
   (when-let [hy @valittu-hallintayksikko] (str "&hy=" (:id hy)))
   (when-let [u @valittu-urakka] (str "&u=" (:id u))))]
    (when (not= url (.-token historia))
      (.setToken historia url))
  ))

(defn vaihda-sivu!
  "Vaihda nykyinen sivu haluttuun."
  [uusi-sivu]
    (when-not (= @sivu uusi-sivu)
      (reset! sivu uusi-sivu)
      (.log js/console "when-not ennen kutsua päivitä url " uusi-sivu)
      (paivita-url))
    )


;; käyttäjä asettaa browseriin urlin / tulee bookmarkista
(defn kasittele-url!
  "Käsittelee urlin (route) muutokset."
  [url]
  (.log js/console "in kasittele-url " url)
  (let [uri (goog.Uri/parse url)
        polku (.getPath uri)
        parametrit (.getQueryData uri)]
    (case polku
      "urakat" (vaihda-sivu! :urakat)
      "raportit" (vaihda-sivu! :raportit)
      "tilannekuva" (vaihda-sivu! :tilannekuva)
      "ilmoitukset" (vaihda-sivu! :ilmoitukset)
      "hallinta" (vaihda-sivu! :hallinta))
    (when-let [hy (.get parametrit "hy")]
      (.log js/console "hyksikkö valittu " hy)
      (reset! valittu-hallintayksikko {:id (js/parseInt hy) :nimi "hy demo"}))
    (when-let [u (.get parametrit "u")]
      (.log js/console "urakka valittu " u)
      (valitse-urakka {:id (js/parseInt u) :nimi "urakka demo" }))))

(.setEnabled historia true)
(kasittele-url! (-> js/document .-location .-hash (.substring 1)))