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
   [cljs.core.async :refer [<! >! chan close!]]
   
   [harja.asiakas.tapahtumat :as t]
   [harja.tiedot.hallintayksikot :as hy]
   [harja.tiedot.urakat :as ur])
  
   (:require-macros [cljs.core.async.macros :refer [go]])
  
  (:import goog.History))


(declare kasittele-url! paivita-url valitse-urakka)

;; Atomi, joka sisältää valitun sivun
(defonce sivu (atom :urakat))

;; Kartan koko. Voi olla aluksi: S (pieni, urakan pääsivulla), M (puolen ruudun leveys) tai L (koko leveys)
(def kartan-koko "Kartan koko" (atom :M))

(defn vaihda-kartan-koko! [uusi-koko]
  (reset! kartan-koko uusi-koko)
  (t/julkaise! {:aihe :kartan-koko-vaihdettu :uusi-koko uusi-koko}))

;; I-vaiheessa aina :tie
(def valittu-vaylamuoto "Tällä hetkellä valittu väylämuoto" (atom :tie))

;; I-vaiheessa aina :hoito tai :yllapito (mieti myöhemmin jaetaanko ylläpidon urakat tarkemmin)
(def valittu-urakkatyyppi "Tällä hetkellä valittu väylämuodosta riippuvainen urakkatyyppi" (atom :hoito))

(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

;; Atomi, joka sisältää valitun hallintayksikön
(def valittu-hallintayksikko "Tällä hetkellä valittu hallintayksikkö (tai nil)" (atom nil))

;; Atomi, joka sisältää valitun urakan
(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

;; Atomi, joka sisältää valitun hallintayksikön urakat
(def urakkalista "Hallintayksikon urakat" (atom nil))

(add-watch valittu-hallintayksikko :loki 
           (fn [_ _ old new]
             (.log js/console "valittu-hallintayksikko " 
                   old " => " new)))
(add-watch urakkalista :loki 
           (fn [_ _ old new]
             (.log js/console "urakkalista " 
                   old " => " new)))

(defn aseta-hallintayksikko-ja-urakka [hy-id u-id]
  ;; jos hy sama kuin jo valittu, ei haeta sitä uudestaan vaan asetetaan vain urakka
  (if-not (= hy-id (:id @valittu-hallintayksikko))
    (go (let [yks (<! (hy/hae-hallintayksikko hy-id))]
      (reset! valittu-hallintayksikko yks)
      (reset! urakkalista nil)
      (reset! valittu-urakka nil)
      (paivita-url)
      (reset! urakkalista (<! (ur/hae-hallintayksikon-urakat yks)))
        (valitse-urakka (first (filter #(= u-id (:id %)) @urakkalista)))))
      ;; else
      (valitse-urakka (first (filter #(= u-id (:id %)) @urakkalista)))))

(defn vaihda-urakkatyyppi! [ut]
  (when (= @valittu-vaylamuoto :tie)
    (reset! valittu-urakkatyyppi ut)))
  
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
      (paivita-url))
    )


(defn kasittele-url!
  "Käsittelee urlin (route) muutokset."
  [url]
  (let [uri (goog.Uri/parse url)
        polku (.getPath uri)
        parametrit (.getQueryData uri)]
    (case polku
      "urakat" (vaihda-sivu! :urakat)
      "raportit" (vaihda-sivu! :raportit)
      "tilannekuva" (vaihda-sivu! :tilannekuva)
      "ilmoitukset" (vaihda-sivu! :ilmoitukset)
      "hallinta" (vaihda-sivu! :hallinta))
    (when-let [hy (some-> parametrit (.get "hy") js/parseInt)]
      (if-let [u (some-> parametrit (.get "u") js/parseInt)] 
      (aseta-hallintayksikko-ja-urakka hy u)
      ;; else
      (go
        (reset! valittu-hallintayksikko (<! (hy/hae-hallintayksikko (js/parseInt hy)))))
      ))
    ))

(.setEnabled historia true)
(kasittele-url! (-> js/document .-location .-hash (.substring 1)))