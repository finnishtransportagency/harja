(ns harja.ui.kentat
  "UI input kenttien muodostaminen typpin perusteella, esim. grid ja lomake komponentteihin."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.yleiset :refer [livi-pudotusvalikko linkki ajax-loader nuolivalinta]]
            [harja.ui.protokollat :refer [hae]]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; PENDING: dokumentoi rajapinta, mitä eri avaimia kentälle voi antaa

;; r/wrap skeeman arvolle
(defn atomina [{:keys [nimi hae aseta]} data vaihda!]
  (let [hae (or hae #(get % nimi))]
    (r/wrap
     (hae data)
     (fn [uusi]
       ;(log "DATA: " (pr-str data) "; UUSI ARVO KENTÄLLE " nimi ": " (pr-str uusi))
       (if aseta
         (vaihda! (aseta data uusi))
         (vaihda! (assoc data nimi uusi)))))))

   

(defmulti tee-kentta
  "Tekee muokattavan kentän tyypin perusteella"
  (fn [t _] (:tyyppi t)))

(defmulti nayta-arvo
  "Tekee vain-luku näyttömuodon kentän arvosta tyypin perusteella. Tämän tarkoituksena ei ole tuottaa 'disabled' tai 'read-only' elementtejä vaan tekstimuotoinen kuvaus arvosta. Oletustoteutus muuntaa datan vain merkkijonoksi."
  (fn [t _] (:tyyppi t)))


(defmethod nayta-arvo :default [_ data]
  (str @data))

(defmethod tee-kentta :haku [{:keys [lahde nayta placeholder pituus lomake?]} data]
  (let [nyt-valittu @data
        teksti (atom (if nyt-valittu
                       ((or nayta str) nyt-valittu) ""))
        tulokset (atom nil)
        valittu-idx (atom nil)]
    (fn [_ data]
      [:div.dropdown {:class (when-not (nil? @tulokset) "open")}
       
       [:input {:class (when lomake? "form-control")
                :value @teksti
                :placeholder placeholder
                :size pituus
                :on-change #(when (= (.-activeElement js/document) (.-target %))
                              ;; tehdään haku vain jos elementti on fokusoitu
                              ;; IE triggeröi on-change myös ohjelmallisista muutoksista
                              (let [v (-> % .-target .-value)]
                                (reset! data nil)
                                (reset! teksti v)
                                (if (> (count v) 2)
                                  (do (reset! tulokset :haetaan)
                                      (go (let [tul (<! (hae lahde v))]
                                            (reset! tulokset tul)
                                            (reset! valittu-idx nil))))
                                  (reset! tulokset nil))))
                :on-key-down (nuolivalinta #(let [t @tulokset]
                                              (log "YLÖS " @valittu-idx)
                                              (when (vector? t)
                                                (swap! valittu-idx
                                                       (fn [idx]
                                                         (if (or (= 0 idx) (nil? idx))
                                                           (dec (count t))
                                                           (dec idx))))))
                                           #(let [t @tulokset]
                                              (log "ALAS " @valittu-idx)
                                              (when (vector? t)
                                                (swap! valittu-idx
                                                       (fn [idx]
                                                         (if (and (nil? idx) (not (empty? t)))
                                                           0
                                                           (if (< idx (dec (count t)))
                                                             (inc idx)
                                                             0))))))
                                           #(let [t @tulokset
                                                  idx @valittu-idx]
                                              (when (number? idx)
                                                (let [v (nth t idx)]
                                                  (reset! data v)
                                                  (reset! teksti ((or nayta str) v))
                                                  (reset! tulokset nil)))))  }]
       [:ul.dropdown-menu {:role "menu"}
        (let [nykyiset-tulokset @tulokset
              idx @valittu-idx]
          (if (= :haetaan nykyiset-tulokset)
            [:li {:role "presentation"} (ajax-loader) " haetaan: " @teksti]
            (if (empty? nykyiset-tulokset)
              [:span.ei-hakutuloksia "Ei tuloksia"]
              (doall (map-indexed (fn [i t]
                                    ^{:key (hash t)}
                                    [:li {:class (when (= i idx) "korostettu") :role "presentation"}
                                     [linkki ((or nayta str) t) #(do (reset! data t)
                                                                     (reset! teksti ((or nayta str) t))
                                                                     (reset! tulokset nil))]])
                                  nykyiset-tulokset)))))]])))

                
                             
(defmethod tee-kentta :string [{:keys [nimi pituus-max pituus-min regex on-focus lomake?]} data]
  [:input {:class (when lomake? "form-control")
           :on-change #(reset! data (-> % .-target .-value))
           :on-focus on-focus
           :value @data
           :max-length pituus-max}])


;; Pitkä tekstikenttä käytettäväksi lomakkeissa, ei sovellu hyvin gridiin
(defmethod tee-kentta :text [{:keys [placeholder nimi koko on-focus lomake?]} data]
  (let [[koko-sarakkeet koko-rivit] koko
        rivit (atom (if (= :auto koko-rivit)
                      2
                      koko-rivit))]
    (komp/luo
     (when (= koko-rivit :auto)
       {:component-did-update
        (fn [this _]
          (let [n (r/dom-node this)]
            (let [erotus (- (.-scrollHeight n) (.-clientHeight n))]
              (when (> erotus 0)
                (swap! rivit + (/ erotus 19))))))})
     
     (fn [{:keys [nimi koko on-focus lomake?]} data]
       [:textarea {:value @data
                   :on-change #(reset! data (-> % .-target .-value))
                   :on-focus on-focus
                   :cols (or koko-sarakkeet 80)
                   :rows @rivit
                   :placeholder placeholder}]))))

(defmethod tee-kentta :numero [kentta data]
  (let [teksti (atom (str @data))]
    (r/create-class
     {:component-will-receive-props
      (fn [_ [_ _ data]]
        (reset! teksti (str @data)))
      
      :reagent-render
      (fn [{:keys [lomake?] :as kentta} data]
        (let [nykyinen-teksti @teksti]
          [:input {:class (when lomake? "form-control")
                   :type "text"
                   :on-focus (:on-focus kentta)
                   :value nykyinen-teksti
                   :on-change #(let [v (-> % .-target .-value)]
                                 (when (or (= v "") 
                                           (re-matches #"\d{1,10}((\.|,)\d{0,2})?" v))
                                   (reset! teksti v)
                                   (let [numero (js/parseFloat (str/replace v #"," "."))]
                                     (reset! data
                                             (when (not (js/isNaN numero))
                                               numero)))))}]))})))



(defmethod tee-kentta :email [{:keys [on-focus lomake?] :as kentta} data]
  [:input {:class (when lomake? "form-control")
           :type "email"
           :value @data
           :on-focus on-focus
           :on-change #(reset! data (-> % .-target .-value))}])



(defmethod tee-kentta :puhelin [{:keys [on-focus pituus lomake?] :as kentta} data]
  [:input {:class (when lomake? "form-control")
           :type "tel"
           :value @data
           :max-length pituus
           :on-focus on-focus
           :on-change #(let [uusi (-> % .-target .-value)]
                         (when (re-matches #"(\s|\d)*" uusi)
                           (reset! data uusi)))}])


(defmethod tee-kentta :radio [{:keys [valinta-nayta valinta-arvo valinnat on-focus]} data]
  (let [arvo (or valinta-arvo identity)
        nayta (or valinta-nayta str)
        nykyinen-arvo @data]
    [:span.radiovalinnat
     (doall
      (map-indexed (fn [i valinta]
                     (let [otsikko (nayta valinta)
                           arvo (arvo valinta)]
                       ^{:key otsikko}
                       [:span.radiovalinta
                        [:input {:type "radio" :value i
                                 :checked (= nykyinen-arvo arvo)
                                 :on-change #(reset! data arvo)}]
                        [:span.radiovalinta-label.klikattava {:on-click #(reset! data arvo)}
                         otsikko]]))
                   valinnat))]))

(defmethod nayta-arvo :radio [{:keys [valinta-nayta]} data]
  ((or valinta-nayta str) @data))

(defmethod tee-kentta :boolean [{:keys [otsikko]} data]
  [:div.checkbox
   [:label
    [:input {:type "checkbox" :checked @data
             :on-click #(do (swap! data not) nil)}
     otsikko]]])

(defmethod nayta-arvo :boolean [{:keys [otsikko]} data]
  [:span (if @data
           "\u2713 "
           "\u2610 ") otsikko])

(defmethod tee-kentta :valinta [{:keys [alasveto-luokka valinta-nayta valinta-arvo valinnat on-focus]} data]
  ;; valinta-arvo: funktio rivi -> arvo, jolla itse lomakken data voi olla muuta kuin valinnan koko item
  ;; esim. :id 
  (let [nykyinen-arvo @data]
    ;; FIXME: on-focus alasvetovalintaan?
    [livi-pudotusvalikko {:class (str "alasveto-gridin-kentta " alasveto-luokka)
                          :valinta (if valinta-arvo
                                     (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                                     nykyinen-arvo)
                          :valitse-fn #(reset! data
                                               (if valinta-arvo
                                                 (valinta-arvo %)
                                                 %))
                          :on-focus on-focus
                          :format-fn (or valinta-nayta str)}
     valinnat]))

(defmethod nayta-arvo :valinta [{:keys [valinta-nayta valinta-arvo]} data]
  ((or valinta-nayta str) @data))



(defmethod tee-kentta :kombo [{:keys [valinnat on-focus lomake?]} data]
  (let [auki (atom false)]
    (fn [{:keys [valinnat]} data]
      (let [nykyinen-arvo (or @data "")]
        [:div.dropdown {:class (when @auki "open")}
         [:input.kombo {:class (when lomake? "form-control")
                        :type "text" :value nykyinen-arvo
                        :on-focus on-focus
                        :on-change #(reset! data (-> % .-target .-value))}]
         [:button {:on-click #(do (swap! auki not) nil)}
          [:span.caret ""]]
         [:ul.dropdown-menu {:role "menu"}
          (for [v (filter #(not= -1 (.indexOf (.toLowerCase (str %)) (.toLowerCase nykyinen-arvo))) valinnat)]
            ^{:key (hash v)}
            [:li {:role "presentation"} [linkki v #(do (reset! data v)
                                                       (reset! auki false))]])]]))))



; ** 26.5.-15, Teemu K.***
; Päivämääräkentissä on seuraavanlainen bugi:
;  - Valitse kalenterista, tai kirjoita validi päivämäärä
;  - Yritä muokata kirjoittamalla esimerkiksi kuukautta
;  - Kentän arvo resetoituu välittömästi vanhaan arvoon, eli kenttää ei voi muokata enää käsin
;
; Bugiin törmättiin, kun :component-will-receive-props vaiheessa tehtävä swap! laitettiin palauttamaan
; alkuperäinen teksti, jos päivämäärän parsiminen ei onnistu.
;
; Samalla aiheutui "virhe", jossa kenttään voi syöttää tauhkaa - tästä kuitenkin tulee käyttöliittymään
; virheilmoitus, joten ainoa ongelma on, että yleensä Harjassa ei voi esim numerokenttiin syöttää aakkosia.

;; pvm-tyhjana ottaa vastaan pvm:n siitä kuukaudesta ja vuodesta, jonka sivu
;; halutaan näyttää ensin
(defmethod tee-kentta :pvm [{:keys [pvm-tyhjana rivi focus on-focus lomake?]} data]
  
  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        teksti (atom (if p
                       (pvm/pvm p)
                       ""))

        ;; picker auki?
        auki (atom false)

        muuta! (fn [data t]
                 (let [d (pvm/->pvm t)]
                   (reset! teksti t)
                   (reset! data d)))]
    (r/create-class
      {:component-will-receive-props
       (fn [this [_ {:keys [focus] :as s} data]]
         (when-not focus
             (reset! auki false))
         (swap! teksti #(if-let [p @data]
                         (pvm/pvm p)
                         %)))
       
       :reagent-render
       (fn [_ data]
         (let [nykyinen-pvm @data
               nykyinen-teksti @teksti
               pvm-tyhjana (or pvm-tyhjana (constantly nil))
               naytettava-pvm (if (nil? nykyinen-pvm)
                                (pvm-tyhjana rivi)
                                nykyinen-pvm)]
           [:span {:on-click #(do (reset! auki true) nil)}
            [:input.pvm {:class (when lomake? "form-control")
                         :value     nykyinen-teksti
                         :on-focus on-focus
                         :on-change #(muuta! data (-> % .-target .-value))}]
            (when @auki
              [:div.aikavalinta
               [pvm-valinta/pvm {:valitse #(do (reset! auki false)
                                               (reset! data %)
                                               (reset! teksti (pvm/pvm %)))
                                 :pvm     naytettava-pvm}]])]))})))

(defmethod nayta-arvo :pvm [_ data]
  (if-let [p @data]
    (pvm/pvm p)
    ""))
    
(defmethod tee-kentta :pvm-aika [{:keys [pvm-tyhjana rivi focus on-focus lomake?]} data]
  
  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        teksti (atom (if p
                       (pvm/pvm p)
                       ""))
        aika-teksti (atom (if p
                            (pvm/aika p)
                            ""))
        ;; picker auki?
        auki (atom false)]
    (r/create-class
      {:component-will-receive-props
       (fn [this [_ {:keys [focus] :as s} data]]
         (log "PVM-AIKA sai propsit: " (pr-str data))
         (when-not focus
           (reset! auki false))
         (swap! teksti #(if-let [p @data]
                          (pvm/pvm p)
                          %)))
       
       :reagent-render
       (fn [_ data]
         (let [aseta! (fn []
                        (let [pvm @teksti
                              aika @aika-teksti
                              p (pvm/->pvm-aika (str pvm " " aika))]
                          (log "pvm: " pvm ", aika: " aika ", p: " p)
                          (when p (reset! data p))))
               
               muuta! (fn [t]
                        (reset! teksti t)
                        (aseta!))
               
               muuta-aika! (fn [t]
                             (when (or (str/blank? t)
                                       (re-matches #"\d{1,2}(:\d*)?" t))
                               (reset! aika-teksti t)
                               (aseta!)))
               
               nykyinen-pvm @data
               nykyinen-teksti @teksti
               nykyinen-aika-teksti @aika-teksti
               pvm-tyhjana (or pvm-tyhjana (constantly nil))
               naytettava-pvm (if (nil? nykyinen-pvm)
                                (pvm-tyhjana rivi)
                                nykyinen-pvm)]
           [:span 
            [:table
             [:tbody
              [:tr
               [:td
                [:input.pvm {:class (when lomake? "form-control")
                             :placeholder "pp.kk.vvvv"
                             :on-click #(do (reset! auki true) nil)
                             :value     nykyinen-teksti
                             :on-focus on-focus
                             :on-change #(muuta! (-> % .-target .-value))}]]
               [:td
                [:input {:class (when lomake? "form-control")
                         :placeholder "tt:mm"
                         :size 5 :max-length 5
                         :value nykyinen-aika-teksti
                         :on-change #(muuta-aika! (-> % .-target .-value))}]]]]]
            
            (when @auki
              [:div.aikavalinta
               [pvm-valinta/pvm {:valitse #(do (reset! auki false)
                                               (muuta! (pvm/pvm %)))
                                 :pvm     naytettava-pvm}]])]))})))

(defmethod nayta-arvo :pvm-aika [_ data]
  (if-let [p @data]
    (pvm/pvm-aika p)
    ""))
