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
  :muokkaustila :aina tai :nappi (oletus). Jos muokkaustila on :aina on kaikki rivit
                lomakekenttiä koko ajan. Jos muokkaustila on :nappi on rivin perässä
                muokkausta kuvaava painike, jota painamalla kyseinen rivi muuttuu
                muokattavaksi.
  
  "
  [{:keys [otsikko muokkaa-fn poista-fn muokkaustila]} skeema tiedot]
  (let [muokattava (atom nil)]
    (fn [{:keys [otsikko muokkaa-fn poista-fn muokkaustila]} skeema tiedot]
      (log "renderöi grid: " (pr-str tiedot))
      [:div.panel.panel-default.grid
       [:div.panel-heading
        [:h6.panel-title otsikko]
        [:span.pull-right
         [:button.btn.btn-default.btn-sm (bs/icon-pencil) " Muokkaa"]]]
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
                  muokattava @muokattava]
              (map-indexed
                 (fn [i rivi]
                   (let [muokkaa (or (= :aina muokkaustila)
                                     (= muokattava rivi))]
                     ^{:key (or (:id rivi) (hash rivi))}
                     [:tr {:class (str (if (even? i)
                                         "parillinen"
                                         "pariton")
                                       (when muokkaa " muokattava"))}
                      (for [{:keys [nimi hae fmt] :as s} skeema]
                        (if muokkaa
                          [:td (tee-kentta s (r/wrap
                                              (if nimi (get rivi nimi)
                                                  (hae rivi))
                                              (fn [uusi]
                                                (muokkaa-fn i rivi
                                                            (assoc rivi nimi uusi)))))]
                          [:td ((or fmt str) (if nimi (get rivi nimi)
                                                 (hae rivi)))]))]))
                 rivit))]])]])))
  
