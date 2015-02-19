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
                   (log "TEKSTI: " t ", pvm: " d)
                   (reset! teksti t)
                   (reset! data d)))
        ]
    (fn [_ data]
      (let [nykyinen-pvm @data
            nykyinen-teksti @teksti]
        [:span {:on-mouse-over #(reset! auki true)
                :on-mouse-out #(reset! auki false)}
         [:input.pvm {:value nykyinen-teksti
                      :on-change #(muuta! (-> % .-target .-value))}]
         (when @auki
           [:div.aikavalinta
            [pvm-valinta/pvm {:valitse #(do (log "PVM: " %)
                                            (reset! data %)
                                            (reset! teksti (pvm/pvm %))) :pvm nykyinen-pvm}]])]))))

(defmulti validoi-saanto (fn [saanto data optiot] saanto))

(defmethod validoi-saanto :ei-tyhja [_ data [viesti]]
  (when (str/blank? data)
    viesti))

(defn validoi
  "Palauttaa kaikki validointivirheet kentälle, jos tyhjä niin validointi meni läpi."
  [data saannot]
  (keep #(fn [[saanto & optiot]]
           (apply validoi-saanto saanto data optiot))
        saannot))

   
(defn grid
  "Taulukko, jossa tietoa voi tarkastella ja muokata. Skeema on vektori joka sisältää taulukon sarakkeet.
Jokainen skeeman itemi on mappi, jossa seuraavat avaimet:
  :nimi       kentän hakufn
  :fmt        kentän näyttämis fn (oletus str)
  :otsikko    ihmiselle näytettävä otsikko
  :tyyppi     kentän tietotyyppi, yksi #{:string :int :pvm :aika :pvm-aika}
  
Tyypin mukaan voi olla lisäavaimia, jotka määrittelevät tarkemmin kentän validoinnin.

Tiedot tulee olla atomi, jossa on vektori riveistä. Jokainen rivi on mappi, jossa kentät
on nimetyillä avaimilla. Lisäksi riveillä on hyvä olla :id attribuutti, jota käytetään rivin
key arvona Reactille. Jos :id arvoa ei ole, otetaan koko rivin hashcode avaimeksi.

Optiot on mappi optioita:
  :tallenna   funktio, jolle kaikki muutokset, poistot ja lisäykset muokkauksen päätyttyä

  
  "
  [{:keys [otsikko tallenna tyhja]} skeema tiedot]
  (let [muokatut (atom nil) ;; muokattu datajoukko
        uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])

        viime-assoc (atom nil) ;; edellisen muokkauksen, jos se oli assoc-in, polku
        
        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [funktio & argumentit]
                   (if  (= funktio assoc-in)
                     ;; assoc-in muutos polkuun, ei tallenneta historiaa jos sama polku kuin edellisessä
                     (do 
                       (when-not (= (first argumentit) @viime-assoc)
                         (swap! historia conj @muokatut))
                       (reset! viime-assoc (first argumentit)))
                     ;; muu muutos, tallennetaan historia aina
                     (do (swap! historia conj @muokatut)
                         (reset! viime-assoc nil)))
                   (apply swap! muokatut funktio argumentit))

        ;; Peruu yhden muokkauksen
        peru! (fn []
               
                (reset! muokatut (peek @historia))
                (swap! historia pop))

        nollaa-muokkaustiedot! (fn []
                                 (reset! muokatut nil)
                                 (reset! historia nil)
                                 (reset! viime-assoc nil)
                                 (reset! uusi-id 0))]
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
               [:button.btn.btn-primary.btn-sm {:on-click #(do (reset! muokatut tiedot) nil)}
                (ikonit/pencil) " Muokkaa"]]
              [:span.pull-right.muokkaustoiminnot
               [:button.btn.btn-sm.btn-default
                {:disabled  (empty? @historia)
                 :on-click #(do (.stopPropagation %)
                                (.preventDefault %)
                                (peru!))}
                (ikonit/peru) " Kumoa"]
               [:button.btn.btn-default.btn-sm.grid-lisaa {:on-click #(muokkaa! conj {:id (swap! uusi-id dec)})}
                (ikonit/plus-sign) " Lisää rivi"]

               [:button.btn.btn-primary.btn-sm.grid-tallenna
                {:on-click #(go (if (<! (tallenna @muokatut))
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
                 (for [{:keys [otsikko leveys]} skeema]
                   [:th {:width leveys} otsikko])
                 (when muokataan
                   [:th.toiminnot " "])
                 [:th.toiminnot ""]]]

               [:tbody
                (let [rivit (filterv #(not (:poistettu %))
                                     (if muokataan @muokatut tiedot))]
                  (if (empty? rivit)
                    [:tr.tyhja [:td {:col-span (inc (count skeema))} tyhja]]
                    (map-indexed
                     (if muokataan
                       (fn [i rivi]
                         ^{:key (or (:id rivi) (hash rivi))}
                         [:tr.muokataan {:class (str (if (even? i)
                                                       "parillinen"
                                                       "pariton"))}
                          (for [{:keys [nimi hae aseta fmt] :as s} skeema]
                            (let [arvo (if hae
                                         (hae rivi)
                                         (get rivi nimi))
                                  virheet (validoi arvo (:validoi s))]
                              [:td {:class (str (when-not (empty? virheet)
                                                  "has-error"))
                                    
                                    }
                               [tee-kentta s (r/wrap
                                              arvo
                                              (fn [uusi]
                                                (if aseta
                                                  (muokkaa! update-in [i] (fn [rivi]
                                                                            (aseta rivi uusi)))
                                                  (muokkaa! assoc-in [i nimi] uusi))))]]))
                          [:td.toiminnot
                           [:span {:on-click #(muokkaa! assoc-in [i :poistettu] true)}
                          
                            (ikonit/trash)]]
                          ])
                       (fn [i rivi]
                         [:tr {:class (if (even? i) "parillinen" "pariton")}
                          (for [{:keys [nimi hae fmt]} skeema]
                            [:td ((or fmt str) (if hae
                                                 (hae rivi)
                                                 (get rivi nimi)))])]))
                     rivit)))]])]]))})))

