(ns harja.ui.grid
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [schema.core :as s]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [ajax-loader linkki]]
            [bootstrap :as bs]
            [harja.ui.ikonit :as ikonit]))

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

(defn grid
  "Taulukko, jossa tietoa voi tarkastella ja muokata. Skeema on vektori joka sisältää taulukon sarakkeet.
Jokainen skeeman itemi on mappi, jossa seuraavat avaimet:
  :nimi       kentän nimi datassa
  :otsikko    ihmiselle näytettävä otsikko
  :tyyppi     kentän tietotyyppi, yksi #{:string :int :pvm :aika :pvm-aika}
  
Tyypin mukaan voi olla lisäavaimia, jotka määrittelevät tarkemmin kentän validoinnin.

Tiedot tulee olla atomi, jossa on vektori riveistä. Jokainen rivi on mappi, jossa kentät
on nimetyillä avaimilla. Lisäksi riveillä on hyvä olla :id attribuutti, jota käytetään rivin
key arvona Reactille. Jos :id arvoa ei ole, otetaan koko rivin hashcode avaimeksi.

Optiot on mappi optioita:
  :tallenna-fn   funktio, jolle kaikki muutokset, poistot ja lisäykset muokkauksen päätyttyä

  :poista-fn    funktio, jolle annetaan indeksi vektoriin ja rivin tiedot. Poistamisen
                tulee tehdä tarvittavat muutokset atomille, jos poistaminen on ok.
  
  "
  [{:keys [otsikko tallenna-fn]} skeema tiedot]
  (let [muokatut (atom nil) ;; muokattu datajoukko
        viimeksi-poistettu-idx (atom nil)
        uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])

        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [funktio & argumentit]
                   (swap! historia conj @muokatut)
                   (apply swap! muokatut funktio argumentit))

        ;; Peruu yhden muokkauksen
        peru! (fn []
                (reset! muokatut (peek @historia))
                (swap! historia pop))
        ]
    ;;(tarkkaile! "muokatut" muokatut)
    (fn [{:keys [otsikko tallenna-fn]} skeema tiedot]
      (let [muokataan (not (nil? @muokatut))]
        [:div.panel.panel-default.grid
         [:div.panel-heading
          [:h6.panel-title otsikko
           (when-not (empty? @historia)
             [:span.peru {:on-click peru!}
              (ikonit/peru)])
           ]
          
          (if-not muokataan
            [:span.pull-right
             [:button.btn.btn-primary.btn-sm {:on-click #(do (reset! muokatut tiedot) nil)}
              (ikonit/pencil) " Muokkaa"]]
            [:span.pull-right.muokkaustoiminnot
             [:button.btn.btn-default.btn-sm.grid-lisaa {:on-click #(muokkaa! conj {:id (swap! uusi-id dec)})}
              (ikonit/plus-sign) " Lisää rivi"]

             [:button.btn.btn-primary.btn-sm.grid-tallenna
              {:on-click #(do (reset! muokatut nil) nil)} ;; kutsu tallenna-fn
              (ikonit/ok) " Tallenna"]
           
             [:button.btn.btn-default.btn-sm.grid-peru
              {:on-click #(do (reset! muokatut nil)
                              (reset! viimeksi-poistettu-idx nil))}
              (ikonit/ban-circle) " Peruuta"]

             
             ])
          ]
         [:div.panel-body
          (if (nil? tiedot)
            (ajax-loader)
            [:table.grid
             [:thead
              [:tr
               (for [otsikko (map :otsikko skeema)]
                 [:th otsikko])
               (when muokataan
                 [:th.toiminnot " "])
               [:th.toiminnot ""]]]

             [:tbody
              (let [rivit (if muokataan @muokatut tiedot)]
                (map-indexed
                 (if muokataan
                   (fn [i rivi]
                     (when-not (::poistettu rivi)
                       ^{:key (or (:id rivi) (hash rivi))}
                       [:tr.muokataan {:class (str (if (even? i)
                                                     "parillinen"
                                                     "pariton"))}
                        (for [{:keys [nimi hae fmt] :as s} skeema]
                          [:td (tee-kentta s (r/wrap
                                              (if hae
                                                (hae rivi)
                                                (get rivi nimi))
                                              (fn [uusi]
                                                (muokkaa! assoc-in [i nimi] uusi))))])
                        [:td.toiminnot
                         [:span {:on-click #(muokkaa! (fn [muokatut]
                                                        (into []
                                                              (filter (fn [r]
                                                                        (not= r rivi)))
                                                              muokatut)))}
                          
                          (ikonit/trash)]]
                        ]))
                     (fn [i rivi]
                       [:tr {:class (if (even? i) "parillinen" "pariton")}
                        (for [{:keys [nimi hae fmt]} skeema]
                          [:td ((or fmt str) (if hae
                                               (hae rivi)
                                               (get rivi nimi)))])]))
                 rivit))]])]]))))

