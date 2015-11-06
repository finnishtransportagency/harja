(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.views.urakat :as urakat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko] :as yleiset]
            [harja.fmt :as fmt]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.grid :as grid]
            [cljs.core.async :refer [<! >! chan]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.ui.raportti :as raportti]
            [harja.transit :as t]
            [clojure.string :as str])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-raporttityyppi (atom nil))


;; Tähän asetetaan suoritetun raportin elementit, jotka renderöidään
(defonce suoritettu-raportti (atom nil))

;; Mäppi raporttityyppejä, haetaan ensimmäisellä kerralla kun raportointiin tullaan
(defonce raporttityypit (atom nil))
(tarkkaile! "Raporttityypit: " raporttityypit)

(defonce mahdolliset-raporttityypit
  (reaction (let [v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko
                  mahdolliset-kontekstit (into #{"koko maa"}
                                               (keep identity [(when v-ur "urakka")
                                                               (when v-hal "hallintayksikko")]))
                  tyypit (vals @raporttityypit)]
              (into []
                    (filter #(some mahdolliset-kontekstit (:konteksti %)))
                    tyypit))))

(defonce poista-ei-mahdollinen-raporttityyppivalinta
  (run! (let [mahdolliset (into #{} @mahdolliset-raporttityypit)
              valittu @valittu-raporttityyppi]
          (when-not (mahdolliset valittu)
            (reset! valittu-raporttityyppi nil)))))

;; Raportin parametrit, parametrityypin lisäämiseksi luo
;; defmethodit parametrin tyypin mukaan

(defmulti raportin-parametri
  "Muodosta UI-komponentti raportin parametristä. Komponentin tulee olla täysin
  itsenäinen ja sisällettävä otsikon ja muun tarpeellisen."
  :tyyppi)

(defmulti raportin-parametri-arvo
  "Hae raportin parametrin arvo. Palauttaa mäppinä raportin parametrin arvon (tai arvot).
  Jos parametri ei ole kelvollisessa tilassa, palauta {:virhe \"Syy\"}."
  :tyyppi)

(defmethod raportin-parametri "hoitokausi" [p]
  [valinnat/urakan-hoitokausi @nav/valittu-urakka])

(defmethod raportin-parametri-arvo "hoitokausi" [p]
  (let [[alku loppu] @u/valittu-hoitokausi]
    {:hk-alkupvm alku
     :hk-loppupvm loppu}))

(defmethod raportin-parametri "kontekstin_hoitokausi" [p]
  [valinnat/kontekstin-hoitokaudet @nav/hallintayksikon-urakkalista])

(defmethod raportin-parametri-arvo "kontekstin_hoitokausi" [p]
  (let [[alku loppu] @u/valittu-kontekstin-hoitokausi]
    (if (and alku loppu)
      {:hk-alkupvm  alku
       :hk-loppupvm loppu}
      {:virhe "Valitse hoitokausi"})))

(defmethod raportin-parametri "hoitokauden-kuukausi" [p]
  [valinnat/hoitokauden-kuukausi])

(defmethod raportin-parametri-arvo "hoitokauden-kuukausi" [p]
  (let [[alku loppu] @u/valittu-hoitokauden-kuukausi]
    {:aikavali-alkupvm alku
     :aikavali-loppupvm loppu}))

(defmethod raportin-parametri "aikavali" [p]
  [valinnat/aikavali @nav/valittu-urakka])

(defmethod raportin-parametri-arvo "aikavali" [p]
  (let [[alku loppu] @u/valittu-aikavali]
    (if (and alku loppu)
      {:alkupvm alku
       :loppupvm loppu}
      {:virhe "Aseta alku ja loppupäivä"})))

(defmethod raportin-parametri "urakan-toimenpide" [p]
  [valinnat/urakan-toimenpide+kaikki])

(defmethod raportin-parametri-arvo "urakan-toimenpide" [p]
  (if-let [tpi @u/valittu-toimenpideinstanssi]
    {:toimenpide-id (:id tpi)}
    {:virhe "Ei tpi valintaa"}))

(defonce urakoittain? (atom false))

(defmethod raportin-parametri "urakoittain" [p]
  [:div.urakoittain
   [:input {:type "checkbox" :checked @urakoittain?
            :on-change #(swap! urakoittain? not)}]
   [:span {:on-click #(swap! urakoittain? not)} " " (:nimi p)]])

(defmethod raportin-parametri-arvo "urakoittain" [p]
  {:urakoittain? @urakoittain?})

(defmethod raportin-parametri "checkbox" [p]
  [:div
   [yleiset/raksiboksi (:nimi p) true (fn []) nil false]])


(defmethod raportin-parametri-arvo "checkbox" [p]
  nil)

(defmethod raportin-parametri :default [p]
  [:span (pr-str p)])

(defmethod raportin-parametri-arvo :default [p]
  {:virhe (str "Ei arvoa parametrilla: " (:nimi p))})

(def parametrien-jarjestys
  ;; Koska parametreillä ei ole mitään järjestysnumeroa
  ;; annetaan osalle sellainen, että esim. kuukauden hoitokausi
  ;; ei tule hoitokausivalinnan yläpuolelle.
  {"hoitokausi" 1
   "hoitokauden-kuukausi" 2
   "urakan-toimenpide" 3})

(defn raportin-parametrit [raporttityyppi konteksti v-ur v-hal]
  (let [parametrit (sort-by #(or (parametrien-jarjestys (:tyyppi %))
                                 100)
                            (filter #(let [k (:konteksti %)]
                                       (or (nil? k)
                                           (= k konteksti)))
                                    (:parametrit raporttityyppi)))
        arvot #(reduce merge {} (map raportin-parametri-arvo parametrit))
        arvot-nyt (arvot)
        _ (log "Arvot: " (pr-str arvot-nyt))]

    ;; Jos parametreja muutetaan tai ne vaihtuu lomakkeen vaihtuessa, tyhjennä suoritettu raportti
    (reset! suoritettu-raportti nil)
    [:span
     
     (map-indexed (fn [i cols]
                    ^{:key i}
                    [:div.row (seq cols)])
                  (loop [rows []
                         row nil
                         [p & parametrit] parametrit]
                    (if-not p
                      (do (log "RIVIT: " (pr-str rows))
                          rows)
                      (let [par [:div.col-md-4 [raportin-parametri p]]]
                        (cond
                          ;; checkboxit aina omalle riville
                          (= "checkbox" (:tyyppi p))
                          (recur (conj (if row
                                         (conj rows row)
                                         rows)
                                       [par])
                                 nil
                                 parametrit)

                          ;; Jos rivi on täynnä aloitetaan uusi
                          (= 3 (count row))
                          (recur (conj rows row)
                                 [par]
                                 parametrit)

                          ;; Muutoin lisätään aiempaan riviin
                          :default
                          (recur rows
                                 (if row (conj row par)
                                     [par])
                                 parametrit))))))

     [:div.row
      [:div.col-md-12
       [:div.raportin-toiminnot
        
        [:form {:target "_blank" :method "POST" :id "raporttipdf"
                :action (k/pdf-url :raportointi)}
         [:input {:type "hidden" :name "parametrit"
                  :value ""}]
         [:button.nappi-ensisijainen.pull-right
          {:type "submit"
           :disabled (contains? arvot-nyt :virhe)
           :on-click #(do (let [input (-> js/document
                                          (.getElementById "raporttipdf")
                                          (aget "parametrit"))
                                parametrit (case konteksti
                                             "koko maa" (raportit/suorita-raportti-koko-maa-parametrit (:nimi raporttityyppi) (arvot))
                                             "hallintayksikko" (raportit/suorita-raportti-hallintayksikko-parametrit (:id v-hal) (:nimi raporttityyppi) (arvot))
                                             "urakka" (raportit/suorita-raportti-urakka-parametrit (:id v-ur) (:nimi raporttityyppi) (arvot)))]
                            (set! (.-value input)
                                  (t/clj->transit parametrit)))
                          true)}
          (ikonit/print) " Tallenna PDF"]]
        [napit/palvelinkutsu-nappi " Tee raportti"
         #(go (reset! suoritettu-raportti :ladataan)
              (let [raportti (<! (case konteksti
                                   "koko maa" (raportit/suorita-raportti-koko-maa (:nimi raporttityyppi)
                                                                                  (arvot))
                                   "hallintayksikko" (raportit/suorita-raportti-hallintayksikko (:id v-hal)
                                                                                                (:nimi raporttityyppi) (arvot))
                                   "urakka" (raportit/suorita-raportti-urakka (:id v-ur)
                                                                              (:nimi raporttityyppi)
                                                                              (arvot))))]
                (if-not (k/virhe? raportti)
                  (reset! suoritettu-raportti raportti)
                  (do
                    (reset! suoritettu-raportti nil)
                    raportti))))
         {:ikoni [ikonit/list]
          :disabled (contains? arvot-nyt :virhe)}]]]]]))

(defn raporttivalinnat []
  (komp/luo
    (fn []
      (let [v-ur @nav/valittu-urakka
            v-hal @nav/valittu-hallintayksikko
            konteksti (cond
                        v-ur "urakka"
                        v-hal "hallintayksikko"
                        :default "koko maa")]
        [:div.raporttivalinnat
         [:h3 "Raportin tiedot"]
         [yleiset/tietoja {}
          "Kohde" (case konteksti
                    "urakka" "Urakka"
                    "hallintayksikko" "Hallintayksikkö"
                    "koko maa" "Koko maa")
          "Urakka" (when (= "urakka" konteksti)
                     (:nimi v-ur))
          "Hallintayksikkö" (when (= "hallintayksikko" konteksti)
                                (:nimi v-hal))
          "Raportti" [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                                           ;;\u2014 on väliviivan unikoodi
                                           :format-fn  #(if % (:kuvaus %) "Valitse")
                                           :valitse-fn #(reset! valittu-raporttityyppi %)
                                           :class      "valitse-raportti-alasveto"}
            @mahdolliset-raporttityypit]]
         
         (when @valittu-raporttityyppi
           [:div.raportin-asetukset
            [raportin-parametrit @valittu-raporttityyppi konteksti v-ur v-hal]])]))))

(defn raporttivalinnat-ja-raportti []
  (let [v-ur @nav/valittu-urakka
        hae-urakan-tyot (fn [ur]
                          (log "[RAPORTTI] Haetaan urakan yks. hint. ja kok. hint. työt")
                          (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! u/urakan-yks-hint-tyot
                                      (s/prosessoi-tyorivit ur
                                                            (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]

    (when v-ur (hae-urakan-tyot @nav/valittu-urakka)) ; FIXME Tämä on kopioitu suoraan views.urakka-namespacesta.
                                                      ; Yritin siirtää urakka-namespaceen yhteyseksi, mutta tuli circular dependency. :(
                                                      ; Toimisko paremmin jos urakan yks. hint. ja kok. hint. työt käyttäisi
                                                      ; reactionia(?) --> ajettaisiin aina kun urakka vaihtuu
    [:span
     [raporttivalinnat]
     (let [r @suoritettu-raportti]
       (cond (= :ladataan r)
             [yleiset/ajax-loader "Raporttia suoritetaan..."]

             (not (nil? r))
             [raportti/muodosta-html r]))]))

(defn raportit []
  (komp/luo
   (komp/sisaan #(when (nil? @raporttityypit)
                   (go (reset! raporttityypit (<! (raportit/hae-raportit))))))
   (komp/sisaan-ulos #(do
                       (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                       (nav/vaihda-kartan-koko! :M))
                     #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
      (if (roolit/roolissa? roolit/tilaajan-kayttaja)
        [:span
         [kartta/kartan-paikka]
         (raporttivalinnat-ja-raportti)]
        [:span "Sinulla ei ole oikeutta tarkastella raportteja."]))))
