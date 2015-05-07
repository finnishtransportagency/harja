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

        [harja.loki :refer [log tarkkaile!]]
        [harja.asiakas.tapahtumat :as t]
        [harja.tiedot.urakoitsijat :as urk]
        [harja.tiedot.hallintayksikot :as hy]
        [harja.tiedot.urakat :as ur]
        [clojure.string :as str])
  
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]])
  
  (:import goog.History))


(declare kasittele-url! paivita-url valitse-urakka)

;; Atomi, joka sisältää valitun sivun vectorina. Elementit ovat järjestyksessä olevat sivut keywordeina.
;; Esim URL urakat/suunnittelu = [:urakat :suunnittelu]
(defonce sivu (atom [:urakat]))

;; Kartan koko. Voi olla aluksi: S (pieni, urakan pääsivulla), M (puolen ruudun leveys) tai L (koko leveys)
(def kartan-kokovalinta "Kartan koko" (atom :M))

(defn vaihda-kartan-koko! [uusi-koko]
  (reset! kartan-kokovalinta uusi-koko)
  (t/julkaise! {:aihe :kartan-koko-vaihdettu :uusi-koko uusi-koko}))

;; I-vaiheessa aina :tie
(def valittu-vaylamuoto "Tällä hetkellä valittu väylämuoto" (atom :tie))

(def +urakkatyypit+
  [{:nimi "Hoito" :arvo :hoito }
   {:nimi "Tiemerkintä" :arvo :tiemerkinta }
   {:nimi "Päällystys" :arvo :paallystys }
   {:nimi "Valaistus" :arvo :valaistus }])

(def valittu-urakkatyyppi "Tällä hetkellä valittu väylämuodosta riippuvainen urakkatyyppi"
  (atom (first +urakkatyypit+)))

(def valittu-urakoitsija "Suodatusta varten valittu urakoitsija
                         tätä valintaa voi käyttää esim. alueurakoitden 
                         urakoitsijakohtaiseen suodatukseen" (atom nil)) ;;(= nil kaikki)

;; Atomi, joka sisältää valitun hallintayksikön
(def valittu-hallintayksikko "Tällä hetkellä valittu hallintayksikkö (tai nil)" (atom nil))

;; Atomi, joka sisältää valitun urakan
(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

;; Atomi, joka sisältää valitun hallintayksikön urakat
(def urakkalista "Hallintayksikon urakat" (atom nil))

(defn paivita-urakka [urakka-id funktio & argumentit]
  (swap! urakkalista (fn [urakat]
                       (mapv #(if (= urakka-id (:id %))
                               (apply funktio % argumentit)
                               % ) urakat)))
  (swap! valittu-urakka #(if (= urakka-id (:id %))
                          (apply funktio % argumentit)
                          % )))
;; kehittäessä voit tarkkailla atomien tilan muutoksia
;;(tarkkaile! "valittu-hallintayksikko" valittu-hallintayksikko)

(def tarvitsen-karttaa "Set käyttöliittymänäkymiä (keyword), jotka haluavat pakottaa kartan näkyviin. 
  Jos tässä setissä on itemeitä, tulisi kartta pakottaa näkyviin vaikka se ei olisikaan muuten näkyissä."
  (atom #{}))

(def kartan-koko
  "Kartan laskettu koko riippuu kartan kokovalinnasta sekä kartan pakotteista."
  (reaction (let [valittu-koko @kartan-kokovalinta
                  tk @tarvitsen-karttaa
                  sivu @sivu]
              (if-not (empty? tk)
                ;; joku tarvitsee karttaa, pakotetaan M kokoon
                :M

                ;; Ei kartan pakotteita, tehdään sivukohtaisia special caseja
                ;; tai palautetaan käyttäjän valitsema koko
                (cond (= sivu :hallinta) :hidden
                      (= sivu :about) :hidden
                      (= sivu :tilannekuva) :L
                      :default valittu-koko)))))

(defn aseta-urakka-ja-urakkatyyppi [urakkalista, urakka-id]
  (let [ur (first (filter #(= urakka-id (:id %)) urakkalista))]
    (valitse-urakka ur)
    (reset! valittu-urakkatyyppi (first (filter #(= (:tyyppi ur) (:arvo %))
                                                +urakkatyypit+)))))

(defn aseta-hallintayksikko-ja-urakka [hy-id u-id]
  ;; jos hy sama kuin jo valittu, ei haeta sitä uudestaan vaan asetetaan vain urakka
  (if-not (= hy-id (:id @valittu-hallintayksikko))
    (go (let [yks (<! (hy/hae-hallintayksikko hy-id))]
          (reset! valittu-hallintayksikko yks)
          (reset! urakkalista (<! (ur/hae-hallintayksikon-urakat yks)))
          (aseta-urakka-ja-urakkatyyppi @urakkalista u-id)))
    ;; else
    (aseta-urakka-ja-urakkatyyppi @urakkalista u-id)))

(defn valitse-urakoitsija! [u]
   (reset! valittu-urakoitsija u))
  
(defn vaihda-urakkatyyppi!
  "Vaihtaa urakkatyypin ja resetoi valitun urakoitsijan, jos kyseinen urakoitsija ei
   löydy valitun tyyppisten urakoitsijain listasta."
  [ut]
  (when (= @valittu-vaylamuoto :tie)
    (reset! valittu-urakkatyyppi ut)
    (swap! valittu-urakoitsija
           #(let [nykyisen-urakkatyypin-urakoitsijat (case (:arvo ut)
                                                       :hoito @urk/urakoitsijat-hoito
                                                       :paallystys @urk/urakoitsijat-paallystys
                                                       :tiemerkinta @urk/urakoitsijat-tiemerkinta
                                                       :valaistus @urk/urakoitsijat-valaistus)]
             (if (nykyisen-urakkatyypin-urakoitsijat (:id %))
               %
               nil)))))

;; Rajapinta hallintayksikön valitsemiseen, jota viewit voivat kutsua
(defn valitse-hallintayksikko [yks]
  ;;(js* "debugger;")
  (reset! valittu-hallintayksikko yks)
  (reset! urakkalista nil)
  (reset! valittu-urakka nil)
  (reset! kartan-kokovalinta :M)
  (paivita-url)
  (if yks
    (do
      (go (reset! urakkalista (<! (ur/hae-hallintayksikon-urakat yks))))
      (t/julkaise! (assoc yks :aihe :hallintayksikko-valittu)))
    (t/julkaise! {:aihe :hallintayksikkovalinta-poistettu})))


(defn valitse-urakka [ur]
  (reset! valittu-urakka ur)
  (reset! kartan-kokovalinta :S)
  (paivita-url)
  (if ur
    (t/julkaise! (assoc ur :aihe :urakka-valittu))
    (t/julkaise! {:aihe :urakkavalinta-poistettu})))

(defonce urakka-klikkaus-kuuntelija
  (t/kuuntele! :urakka-klikattu
               ;; FIXME: tämä pitäisi faktoroida elegantimmaksi
               ;; joku tapa pitää olla sanoa, mitä halutaan tapahtuvan kun urakkaa
               ;; klikataan
               ;;
               ;; Ehkä joku pino kartan valintatapahtumien kuuntelijoita, jonne voi lisätä
               ;; itsensä ja ne ajettaisiin uusin ensin. Jos palauttaa true, ei ajeta muita.
               ;; Silloin komponentti voisi ylikirjoittaa valintatapahtumien käsittelyn.
                      
               (fn [urakka]
                 ;;(log "KLIKATTU URAKKAA: " (:nimi urakka))
                 (when (empty? @tarvitsen-karttaa)
                   (valitse-urakka urakka)))))
              
;; Quick and dirty history configuration.
(defonce historia (let [h (History. false)]
                    (events/listen h EventType/NAVIGATE #(do (log "NAVIGOINTIEVENTTI")
                                                             (kasittele-url! (.-token %))))
  h))

;; asettaa oikean sisällön urliin ohjelman tilan perusteella
(defn paivita-url []
  (let [url (str (clojure.string/join "/" (map name @sivu))
                 "?"
                 (when-let [hy @valittu-hallintayksikko] (str "&hy=" (:id hy)))
                 (when-let [u @valittu-urakka] (str "&u=" (:id u))))]
    (when (not= url (.-token historia))
      (log "url-vector: " (pr-str @sivu))
      (log "URL != token :: " url " != " (.getToken historia))
      (.setToken historia url))
  ))

(defn vaihda-sivu!
  "Vaihda nykyinen sivu haluttuun."
  [uusi-sivu]
    (let [uusi-sivu-vector (case uusi-sivu ; "Jos pyydetty sivu on alasivu, rakenna vector
                              :yleiset [:urakat :yleiset]
                              :suunnittelu [:urakat :suunnittelu]
                                  :kokonaishintaiset [:urakat :suunnittelu :kokonaishintaiset]
                                  :yksikkohintaiset [:urakat :suunnittelu :yksikkohintaiset]
                                  :materiaalit [:urakat :suunnittelu :materiaalit]
                              :toteumat [:urakat :toteumat]
                              :laadunseuranta [:urakat :laadunseuranta]
                              :siltatarkastukset [:urakat :siltatarkastukset]
                          [uusi-sivu])]
      (reset! sivu uusi-sivu-vector)
      (paivita-url)))

(def suodatettu-urakkalista "Urakat suodatettuna urakkatyypin ja urakoitsijan mukaan."
  (reaction
   (let [v-ur-tyyppi (:arvo @valittu-urakkatyyppi)
         v-urk @valittu-urakoitsija
         urakkalista @urakkalista]
     (into []
           (comp (filter #(= v-ur-tyyppi (:tyyppi %)))
                 (filter #(or (nil? v-urk) (= (:id v-urk) (:id (:urakoitsija %))))))
           urakkalista))))

(defn kasittele-url!
    "Käsittelee urlin (route) muutokset."
    [url]
    (let [uri (goog.Uri/parse url)
          polku (.getPath uri)
          polku-split (str/split polku #"/")
          parametrit (.getQueryData uri)]
        (log "polku " polku)
        (case (first polku-split)
            "urakat" (case (second polku-split)
                         "yleiset" (vaihda-sivu! :yleiset)
                         "suunnittelu" (case (get polku-split 2)
                                       "kokonaishintaiset" (vaihda-sivu! :kokonaishintaiset)
                                       "yksikkohintaiset" (vaihda-sivu! :yksikkohintaiset)
                                       "materiaalit" (vaihda-sivu! :materiaalit)
                                       (vaihda-sivu! :suunnittelu))
                         "toteumat" (vaihda-sivu! :toteumat)
                         "laadunseuranta" (vaihda-sivu! :laadunseuranta)
                         "siltatarkastukset" (vaihda-sivu! :siltatarkastukset)
                         (vaihda-sivu! :urakat))
             "raportit" (vaihda-sivu! :raportit)
             "tilannekuva" (vaihda-sivu! :tilannekuva)
             "ilmoitukset" (vaihda-sivu! :ilmoitukset)
             "hallinta" (vaihda-sivu! :hallinta)
             "about" (vaihda-sivu! :about)
            (vaihda-sivu! :urakat))
    (when-let [hy (some-> parametrit (.get "hy") js/parseInt)]
      (if-let [u (some-> parametrit (.get "u") js/parseInt)] 
        (do (log "ASETA HALLINTAYKSIKKO JA URAKKA")
            (aseta-hallintayksikko-ja-urakka hy u))
        ;; else
        (go
          (log "ASETA VAIN HALLINTAYKSIKKO")
          (valitse-hallintayksikko (<! (hy/hae-hallintayksikko (js/parseInt hy)))))
        ))
    ))

(.setEnabled historia true)

