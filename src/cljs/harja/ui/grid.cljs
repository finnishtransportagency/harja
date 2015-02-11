(ns harja.ui.grid
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [schema.core :as s]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [bootstrap :as bs]))

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
  :muokkaa-fn   funktio, jolle annetaan indeksi vektoriin, vanhat rivin tiedot ja uudet
                rivin tiedot. Muokkauksen tulee tehdä tarvittavat muutokset atomille, 
                jos muuttaminen on ok.
  :poista-fn    funktio, jolle annetaan indeksi vektoriin ja rivin tiedot. Poistamisen
                tulee tehdä tarvittavat muutokset atomille, jos poistaminen on ok.
  
  "
  [{:keys [otsikko muokkaa-fn poista-fn]} skeema tiedot]
  (let [muokataan (atom false)]
    (fn [{:keys [otsikko muokkaa-fn poista-fn]} skeema tiedot]
      (log "renderöi grid: " (pr-str tiedot))
      [:div.panel.panel-default.grid
       [:div.panel-heading
        [:h6.panel-title otsikko]
        (if-not @muokataan
          [:span.pull-right
           [:button.btn.btn-primary.btn-sm {:on-click #(do (swap! muokataan not) nil)}
            (bs/icon-pencil) " Muokkaa"]]
          [:span.pull-right.muokkaustoiminnot
           [:button.btn.btn-primary.btn-sm {:on-click #(do (swap! muokataan not) nil)} ;; kutsu tallenna-fn
            (bs/icon-ok) " Tallenna"]
           
           [:button.btn.btn-default.btn-sm {:on-click #(do (reset! muokataan false) nil)}
            (bs/icon-ban-circle) " Peruuta"]])]
       [:div.panel-body
        (if (nil? tiedot)
          (ajax-loader)
          [:table.grid
           [:thead
            [:tr
             (for [otsikko (map :otsikko skeema)]
               [:th otsikko])
             [:th.toiminnot ""]]]

           [:tbody
            (let [rivit tiedot
                  muokataan @muokataan]
              (map-indexed
               (if muokataan
                 (fn [i rivi]
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
                                            (muokkaa-fn i rivi
                                                        (assoc rivi nimi uusi)))))])])
                 (fn [i rivi]
                   [:tr {:class (if (even? i) "parillinen" "pariton")}
                    (for [{:keys [nimi hae fmt]} skeema]
                      [:td ((or fmt str) (if hae
                                           (hae rivi)
                                           (get rivi nimi)))])]))
               rivit))]])]])))

