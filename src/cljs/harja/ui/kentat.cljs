(ns harja.ui.kentat
  "UI input kenttien muodostaminen typpin perusteella, esim. grid ja lomake komponentteihin."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.yleiset :refer [livi-pudotusvalikko linkki ajax-loader nuolivalinta]]
            [harja.ui.protokollat :refer [hae]]
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

   

(defmulti tee-kentta (fn [t _] (:tyyppi t)))

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
           :value @data}])

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

 

(defmethod tee-kentta :valinta [{:keys [alasveto-luokka valinta-nayta valinta-arvo valinnat]} data]
  (let [arvo (or valinta-arvo :id)
        nayta (or valinta-nayta str)
        nykyinen-arvo (arvo @data)]
    ;; FIXME: on-focus alasvetovalintaan?
    [livi-pudotusvalikko {:class (str "alasveto-gridin-kentta " alasveto-luokka)
                          :valinta @data
                          :valitse-fn #(do (log "valinta: " %)
                                           (reset! data %))
                          :format-fn valinta-nayta}
     valinnat]))




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

        muuta! (fn [t]
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
                          "")))
       
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
                         :on-change #(muuta! (-> % .-target .-value))}]
            (when @auki
              [:div.aikavalinta
               [pvm-valinta/pvm {:valitse #(do (reset! auki false)
                                               (reset! data %)
                                               (reset! teksti (pvm/pvm %)))
                                 :pvm     naytettava-pvm}]])]))})))
 
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
                          "")))
       
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
