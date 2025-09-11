(ns harja.views.urakka.muutokset.yhteiset
  "Muutokset välilehden yhteiset komponentit"
  (:require [reagent.core :as r]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot]))


(defn liite-kentta
  "Lomakkeen liitekenttä, joka näyttää liitteiden listauksen ja mahdollistaa uusien liitteiden lisäämisen."
  [e! {:keys [uusi-liite muokattava-muutos] :as _app}]
  [{:otsikko "Liite" 
    :nimi :liitteet 
    :kaariva-luokka "muutosliite"
    :tyyppi :komponentti 
    ::lomake/col-luokka "col-xs-12"
    :uusi-rivi? true
    :komponentti (fn [_]
                   (when (every? :nimi (:liitteet muokattava-muutos))
                     [liitteet/liitteet-ja-lisays
                      @nav/valittu-urakka-id
                      (:liitteet muokattava-muutos)
                      {:uusi-liite-atom (r/wrap uusi-liite
                                          #(e! (muutos-tiedot/->LisaaLiite %)))
                       :uusi-liite-teksti "Lisää liite"
                       :nayta-lisatyt-liitteet? false
                       :lisaa-usea-liite? true
                       :salli-poistaa-lisatty-liite? true
                       :poista-lisatty-liite-fn #(e! (muutos-tiedot/->PoistaLisattyLiite))
                       :salli-poistaa-tallennettu-liite? true
                       :poista-tallennettu-liite-fn #(e! (muutos-tiedot/->PoistaTallennettuLiite %))}]))}])


(defn kehystetty-avattava-grid 
  "Piirtää yhtenäisesti Muutoksien taulukot collapsoitaviksi.
   summan saa piiloon antamalla sille arvon :ei-summaa"
  [e! app {:keys [taulukon-avain taulukon-nakyvyys-event
                  otsikko summa toiminnot taulukko] :as _tiedot}]
  
  (let [sisalto-nakyvissa? (get-in app [:taulukko-nakyvissa? taulukon-avain])]
    [:div.collapsoitava-osio
     [:div.otsikkorivi.klikattava {:on-click taulukon-nakyvyys-event}
      [:span
       [ikonit/navigation-ympyrassa (if sisalto-nakyvissa?
                                      :down
                                      :right)]
       [:h2 otsikko]]
      
      (when-not (= summa :ei-summaa)
        [:div.summa {:aria-label (str otsikko " yhteensä " summa " euroa")}
         (fmt/euro-opt summa)])]
     
     (when sisalto-nakyvissa?
       [:span
        [:div.toiminnot
         [toiminnot e! app]]
        [:div.taulukko
         [taulukko e! app]]])]))


(defn +rivi-muutoksen-syy+ []
  {:nimi :syy
   :otsikko "Muutoksen syy"
   :tyyppi :text
   :palstoja 2
   :koko [90 4]
   :aputeksti (str
                "Kuvaile muutos mahdollisimman tarkasti. "
                "Ethän syötä kenttään henkilö- tai muuta arkaluontoista tietoa.")
   :pituus-max 1000
   :uusi-rivi? true
   :pakollinen? true
   ::lomake/col-luokka "perustiedot col-sm-6 aputeksti"})


(defn +rivi-muutos-voimassa+ [app]
  {:nimi :voimassa_alkaen :otsikko "Voimassa alkaen"
   :tyyppi :pvm :uusi-rivi? true
   :pakollinen? true
   ;; Pysyvän muutoksen lomakkeella valitaan hoitokausi mistä eteenpäin muutos vaikuttaa. Se ei saa olla
   ;; pienempi kuin voimassa alkaen, joten kutsuttava :aseta funktiota. Ei vaikuta ainakaan vielä muissa muutostyypeissä
   :aseta (fn [rivi arvo]
            (-> rivi
              (assoc :voimassa_alkaen arvo)
              (assoc :mahdolliset-hoitovuodet-lomakkeella
                (filter #(pvm/jalkeen? (first %) arvo)
                  (:urakan-hoitokaudet app)))))})


(defn lomake-yhteinen [e! app]
  (concat
    [(lomake/ryhma {:otsikko "Perustiedot"}
       (+rivi-muutoksen-syy+)
       (+rivi-muutos-voimassa+ app))]
    (liite-kentta e! app)))
