(ns harja.ui.grid
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [schema.core :as s]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [ajax-loader linkki alasvetovalinta]]
            [bootstrap :as bs]
            [harja.ui.ikonit :as ikonit]
            [harja.pvm :as pvm]
            [harja.ui.pvm :as pvm-valinta]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmulti tee-kentta (fn [t _] (:tyyppi t)))

(defmethod tee-kentta :string [{:keys [nimi pituus-max pituus-min regex]} data]
  [:input {:on-change #(reset! data (-> % .-target .-value))
           :value @data
           :class (if @data ;; validoi
                    "ok"
                    "virhe")}])


(defmethod tee-kentta :numero [kentta data]
  [:input {:type "number"
           :value @data
           :on-change #(reset! data (-> % .-target .-value))}
   ])


(defmethod tee-kentta :email [kentta data]
  [:input {:type "email"
           :value @data
           :on-change #(reset! data (-> % .-target .-value))}])

(defmethod tee-kentta :puhelin [kentta data]
  [:input {:type "tel"
           :value @data
           :max-length (:pituus kentta)
           :on-change #(let [uusi (-> % .-target .-value)]
                         (when (re-matches #"(\s|\d)*" uusi)
                           (reset! data uusi)))}]) 

(defmethod tee-kentta :valinta [{:keys [valinta-nayta valinta-arvo valinnat]} data]
  (let [arvo (or valinta-arvo :id)
        nayta (or valinta-nayta str)
        nykyinen-arvo (arvo @data)]
    [alasvetovalinta {:valinta @data
                      :valitse-fn #(do (log "valinta: " %)
                                       (reset! data %))
                      :format-fn valinta-nayta}
     valinnat]))


(defmethod tee-kentta :kombo [{:keys [valinnat]} data]
  (let [auki (atom false)]
    (fn [{:keys [valinnat]} data]
      (let [nykyinen-arvo (or @data "")]
        [:div.dropdown {:class (when @auki "open")}
         [:input.kombo {:type "text" :value nykyinen-arvo
                        :on-change #(reset! data (-> % .-target .-value))}]
         [:button {:on-click #(do (swap! auki not) nil)}
          [:span.caret ""]]
         [:ul.dropdown-menu {:role "menu"}
          (for [v (filter #(not= -1 (.indexOf (.toLowerCase (str %)) (.toLowerCase nykyinen-arvo))) valinnat)]
            ^{:key (hash v)}
            [:li {:role "presentation"} [linkki v #(do (reset! data v)
                                                       (reset! auki false))]])]]))))


  
(defmethod tee-kentta :pvm [_ data]
  
  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        teksti (atom (if-let [p @data]
                       (pvm/pvm p)
                       ""))
        ;; picker auki?
        auki (atom false)

        muuta! (fn [t]
                 (let [d (pvm/->pvm t)]
                   (reset! teksti t)
                   (reset! data d)))
        ]
    (r/create-class
     {:component-will-receive-props
      (fn [this [_ _ data]]
        (swap! teksti #(if-let [p @data]
                         (pvm/pvm p)
                         %)))
      
      :reagent-render
      (fn [_ data]
        (let [nykyinen-pvm @data
              nykyinen-teksti @teksti]
          [:span {:on-click #(do (reset! auki true) nil)}
           [:input.pvm {:value nykyinen-teksti
                        :on-change #(muuta! (-> % .-target .-value))}]
           (when @auki
             [:div.aikavalinta
              [pvm-valinta/pvm {:valitse #(do (reset! auki false)
                                              (reset! data %)
                                              (reset! teksti (pvm/pvm %)))
                                :pvm nykyinen-pvm}]])]))})))

(defmulti validoi-saanto (fn [saanto data  & optiot] saanto))

(defmethod validoi-saanto :ei-tyhja [_ data & [viesti]]
  (when (str/blank? data)
    viesti))

(defn validoi-saannot
  "Palauttaa kaikki validointivirheet kentälle, jos tyhjä niin validointi meni läpi."
  [data rivi saannot]
  (keep (fn [saanto]
          (if (fn? saanto)
            (saanto data rivi)
            (let [[saanto & optiot] saanto]
              (apply validoi-saanto saanto data optiot))))
        saannot))

(defn validoi-rivi
  "Tekee validoinnin yhden rivin kaikille kentille. Palauttaa mäpin kentän nimi -> virheet vektori."
  [rivi skeema]
  (loop [v {}
         [s & skeema] skeema]
    (if-not s
      v
      (let [{:keys [nimi hae validoi]} s]
        (if (empty? validoi)
          (recur v skeema)
          (let [virheet (validoi-saannot (if hae
                                                  (hae rivi)
                                                  (get rivi nimi))
                                                rivi
                                                validoi)]
            (recur (if (empty? virheet) v (assoc v nimi virheet))
                   skeema)))))))

   
(defn grid
  "Taulukko, jossa tietoa voi tarkastella ja muokata. Skeema on vektori joka sisältää taulukon sarakkeet.
Jokainen skeeman itemi on mappi, jossa seuraavat avaimet:
  :nimi       kentän hakufn
  :fmt        kentän näyttämis fn (oletus str)
  :otsikko    ihmiselle näytettävä otsikko
  :tyyppi     kentän tietotyyppi,  #{:string :puhelin :email :pvm}
  
Tyypin mukaan voi olla lisäavaimia, jotka määrittelevät tarkemmin kentän validoinnin.


Optiot on mappi optioita:
  :tallenna   funktio, jolle kaikki muutokset, poistot ja lisäykset muokkauksen päätyttyä

  
  "
  [{:keys [otsikko tallenna tyhja]} skeema tiedot]
  (let [muokatut (atom nil) ;; muokattu datajoukko
        uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet (atom {}) ;; validointivirheet: (:id rivi) => [virheet]
        viime-assoc (atom nil) ;; edellisen muokkauksen, jos se oli assoc-in, polku
        
        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [id funktio & argumentit]
                   (log "muokataan " id " \n funktio : " funktio )
                   (log "muokatut: " muokatut)
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         uudet-tiedot (swap! muokatut
                                             (fn [muokatut]
                                               (apply update-in muokatut [id]
                                                      funktio argumentit)))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       ;;(log "VANHAT: " (pr-str vanhat-tiedot) "\nUUDET: " (pr-str uudet-tiedot))
                       (swap! historia conj [vanhat-tiedot vanhat-virheet])
                       (swap! virheet (fn [virheet]
                                        (let [rivin-virheet (validoi-rivi (get uudet-tiedot id) skeema)]
                                          (if (empty? rivin-virheet)
                                            (dissoc virheet id)
                                            (assoc virheet id rivin-virheet))))))))

        lisaa-rivi! (fn []
                      (let [id (swap! uusi-id dec)
                            vanhat-tiedot @muokatut
                            vanhat-virheet @virheet
                            uudet-tiedot (swap! muokatut assoc id {:id id})]
                        (swap! historia conj [vanhat-tiedot vanhat-virheet])))



        ;; Peruu yhden muokkauksen
        peru! (fn []
                (let [[muok virh] (peek @historia)]
                  (reset! muokatut muok)
                  (reset! virheet virh))
                (swap! historia pop))

        nollaa-muokkaustiedot! (fn []
                                 (reset! virheet {})
                                 (reset! muokatut nil)
                                 (reset! historia nil)
                                 (reset! viime-assoc nil)
                                 (reset! uusi-id 0))
        aloita-muokkaus! (fn [tiedot]
                           (nollaa-muokkaustiedot!)
                           (loop [muok (array-map)
                                  [r & rivit] (reverse tiedot)]
                             (if-not r
                               (reset! muokatut muok)
                               (recur (assoc muok
                                        (:id r) r)
                                      rivit)))
                           nil)
        ]
    (r/create-class
     {:component-will-receive-props
      (fn [this new-argv]
        ;; jos gridin data vaihtuu, muokkaustila on peruttava, jotta uudet datat tulevat näkyviin
        (nollaa-muokkaustiedot!))
      
      :reagent-render 
      (fn [{:keys [otsikko tallenna]} skeema tiedot]
        (let [muokataan (not (nil? @muokatut))]
          [:div.panel.panel-default.grid
           [:div.panel-heading
            [:h6.panel-title otsikko
           
             ]
          
            (if-not muokataan
              [:span.pull-right
               [:button.btn.btn-primary.btn-sm {:on-click #(aloita-muokkaus! tiedot)}
                (ikonit/pencil) " Muokkaa"]]
              [:span.pull-right.muokkaustoiminnot
               [:button.btn.btn-sm.btn-default
                {:disabled  (empty? @historia)
                 :on-click #(do (.stopPropagation %)
                                (.preventDefault %)
                                (peru!))}
                (ikonit/peru) " Kumoa"]
               [:button.btn.btn-default.btn-sm.grid-lisaa {:on-click lisaa-rivi!}
                (ikonit/plus-sign) " Lisää rivi"]

               [:button.btn.btn-primary.btn-sm.grid-tallenna
                {:disabled (not (empty? @virheet))
                 :on-click #(go (if (<! (tallenna  (mapv second @muokatut)))
                                  (nollaa-muokkaustiedot!)))} ;; kutsu tallenna-fn: määrittele paluuarvo?
                (ikonit/ok) " Tallenna"]
           
               [:button.btn.btn-default.btn-sm.grid-peru
                {:on-click #(do (nollaa-muokkaustiedot!) nil)}
                (ikonit/ban-circle) " Peruuta"]
               ])
            ]
           [:div.panel-body
            (if (nil? tiedot)
              (ajax-loader)
              [:table.grid
               [:thead
                [:tr
                 (for [{:keys [otsikko leveys nimi]} skeema]
                   ^{:key (str nimi)}
                   [:th {:width leveys} otsikko])
                 (when muokataan
                   [:th.toiminnot {:width "5%"} " "])
                 [:th.toiminnot ""]]]

               [:tbody
                (if muokataan
                  ;; Muokkauskäyttöliittymä
                  (let [muokatut @muokatut]
                    (if (empty? muokatut)
                      [:tr.tyhja [:td {:col-span (inc (count skeema))} tyhja]]
                      (let [kaikki-virheet @virheet]
                        (map-indexed
                         (fn [i [id rivi]]
                           (let [rivin-virheet (get kaikki-virheet id)]
                             (when-not (:poistettu rivi)
                               ^{:key id}
                               [:tr.muokataan {:class (str (if (even? i)
                                                             "parillinen"
                                                             "pariton"))}
                                (for [{:keys [nimi hae aseta fmt] :as s} skeema]
                                  (let [arvo (if hae
                                               (hae rivi)
                                               (get rivi nimi))
                                        kentan-virheet (get rivin-virheet nimi)]
                                    ^{:key (str nimi)}
                                    [:td {:class (str (when-not (empty? kentan-virheet)
                                                        "has-error"))}
                                     (when-not (empty? kentan-virheet)
                                       [:div.virheet
                                        [:div.virhe
                                         (for [v kentan-virheet]
                                           [:span v])]])
                                     [tee-kentta s (r/wrap
                                                    arvo
                                                    (fn [uusi]
                                                      (if aseta
                                                        (muokkaa! id (fn [rivi]
                                                                       (aseta rivi uusi)))
                                                        (muokkaa! id assoc nimi uusi))))]]))
                                [:td.toiminnot
                                 [:span {:on-click #(muokkaa! id assoc :poistettu true)}
                                  (ikonit/trash)]]])))
                         (seq muokatut)))))

                  ;; Näyttömuoto
                  (let [rivit tiedot]
                    (if (empty? rivit)
                      [:tr.tyhja [:td {:col-span (inc (count skeema))} tyhja]]
                      (map-indexed
                       (fn [i rivi]
                         ^{:key (:id rivi)}
                         [:tr {:class (if (even? i) "parillinen" "pariton")}
                          (for [{:keys [nimi hae fmt]} skeema]
                            ^{:key (str nimi)}
                            [:td ((or fmt str) (if hae
                                                 (hae rivi)
                                                 (get rivi nimi)))])])
                       rivit))))]])]]))})))




