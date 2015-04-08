(ns harja.ui.grid
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [schema.core :as s]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [ajax-loader linkki alasvetovalinta]]
            [bootstrap :as bs]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta]]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defrecord Otsikko [teksti])

(defn otsikko
  "Luo otsikon annetulla tekstillä."
  [teksti]
  (->Otsikko teksti))

    
(defn otsikko? [x]
  (instance? Otsikko x))

(defmulti validoi-saanto (fn [saanto nimi data rivi taulukko & optiot] saanto))

(defmethod validoi-saanto :ei-tyhja [_ nimi data _ _ & [viesti]]
  (when (str/blank? data)
    viesti))

(defmethod validoi-saanto :uniikki [_ nimi data _ taulukko & [viesti]]
  (let [rivit-arvoittain (group-by nimi (vals taulukko))]
    (log "rivit-arvoittain:" (pr-str rivit-arvoittain) " JA DATA: " data)
    (when (> (count (get rivit-arvoittain data)) 1)
      viesti)))


(defn validoi-saannot
  "Palauttaa kaikki validointivirheet kentälle, jos tyhjä niin validointi meni läpi."
  [nimi data rivi taulukko saannot]
  (keep (fn [saanto]
          (if (fn? saanto)
            (saanto data rivi)
            (let [[saanto & optiot] saanto]
              (apply validoi-saanto saanto nimi data rivi taulukko optiot))))
        saannot))

(defn validoi-rivi
  "Tekee validoinnin yhden rivin kaikille kentille. Palauttaa mäpin kentän nimi -> virheet vektori."
  [taulukko rivi skeema]
  (loop [v {}
         [s & skeema] skeema]
    (if-not s
      v
      (let [{:keys [nimi hae validoi]} s]
        (if (empty? validoi)
          (recur v skeema)
          (let [virheet (validoi-saannot nimi (if hae
                                                  (hae rivi)
                                                  (get rivi nimi))
                                                rivi taulukko
                                                validoi)]
            (recur (if (empty? virheet) v (assoc v nimi virheet))
                   skeema)))))))

(defprotocol Grid
  "Ohjausprotokolla, jolla gridin muokkaustilaa voidaan kysellä ja manipuloida."

  (lisaa-rivi! [g rivin-tiedot] "Lisää muokkaustilassa uuden rivin. 
Annettu rivin-tiedot voi olla tyhjä tai se voi alustaa kenttien arvoja.")

  (hae-muokkaustila [g] "Hakee tämänhetkisen muokkaustilan, joka on mäppi id:stä rivin tietoihin.")


  (hae-virheet [g] "Hakee tämänhetkisen muokkaustilan mukaiset validointivirheet.")

  (nollaa-historia! [g] "Nollaa muokkaushistorian, tämä on lähinnä muokkaus-grid versiota varten. Tällä voi kertoa gridille, että data on täysin muuttunut eikä muokkaushistoria ole enää relevantti.")
  ;; PENDING: oisko "jemmaa muokkaushistoria", jolla sen saisi avaimella talteen ja otettua takaisin?
  
  ;; PENDING: lisää tänne tarvittaessa muita metodeja
  )

(defprotocol GridKahva
  "Sisäinen protokolla, jolle grid asettaa itsensä."
  (aseta-grid [this grid] "Asetaa gridin, jolle ohjauskutsut pitäisi välittää."))

(defn grid-ohjaus
  "Tekee grid ohjausinstanssin, jonka kutsuva puoli voi antaa gridille."
  []
  (let [gridi (atom nil)]
    (reify
      Grid
      (lisaa-rivi! [_ rivin-tiedot]
        (lisaa-rivi! @gridi rivin-tiedot))
      
      (hae-muokkaustila [_]
        (hae-muokkaustila @gridi))

      (hae-virheet [_]
        (hae-virheet @gridi))

      (nollaa-historia! [_]
        (nollaa-historia! @gridi))

      GridKahva
      (aseta-grid [_ grid]
        (reset! gridi grid)))))

      
  
(defn grid
  "Taulukko, jossa tietoa voi tarkastella ja muokata. Skeema on vektori joka sisältää taulukon sarakkeet.
Jokainen skeeman itemi on mappi, jossa seuraavat avaimet:
  :nimi            kentän hakufn
  :fmt             kentän näyttämis fn (oletus str)
  :otsikko         ihmiselle näytettävä otsikko
  :tunniste        rivin tunnistava kenttä, oletuksena :id
  :voi-poistaa?    voiko rivin poistaa
  :voi-lisata?     voiko rivin lisätä (boolean)
  :tyyppi          kentän tietotyyppi,  #{:string :puhelin :email :pvm}
  :ohjaus          gridin ohjauskahva, joka on luotu (grid-ohjaus) kutsulla
  
Tyypin mukaan voi olla lisäavaimia, jotka määrittelevät tarkemmin kentän validoinnin.

Optiot on mappi optioita:
  :tallenna        funktio, jolle kaikki muutokset, poistot ja lisäykset muokkauksen päätyttyä
                   jos tallenna funktiota ei ole annettu, taulukon muokkausta ei sallita eikä nappia näytetään
                   jos tallenna arvo on :ei-mahdollinen, näytetään Muokkaa-nappi himmennettynä
  :rivi-klikattu   funktio jota kutsutaan kun käyttäjä klikkaa riviä näyttömoodissa (parametrinä rivin tiedot)
  :muokkaa-footer  optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muokkaa-aina    jos true, grid on aina muokkaustilassa, eikä tallenna/peruuta nappeja ole
  :muutos          jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                   Parametrina Grid ohjauskahva
  :uusi-rivi       jos annettu uuden rivin tiedot käsitellään tällä funktiolla 

  
  "
  [{:keys [otsikko tallenna tyhja tunniste voi-poistaa? voi-lisata? rivi-klikattu
           muokkaa-footer muokkaa-aina muutos
           uusi-rivi] :as opts} skeema tiedot]
  (let [muokatut (atom nil) ;; muokattu datajoukko
        jarjestys (atom nil) ;; id:t indekseissä (tai otsikko)
        uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet (atom {}) ;; validointivirheet: (:id rivi) => [virheet]
        viime-assoc (atom nil) ;; edellisen muokkauksen, jos se oli assoc-in, polku
        
        ohjaus (reify Grid
                 (lisaa-rivi! [this rivin-tiedot]
                              (let [id (or (:id rivin-tiedot) (swap! uusi-id dec))
                                    vanhat-tiedot @muokatut
                                    vanhat-virheet @virheet
                                    vanha-jarjestys @jarjestys
                                    uudet-tiedot (swap! muokatut assoc id
                                                        ((or uusi-rivi identity)
                                                         (merge rivin-tiedot {:id id :koskematon true})))
                                    uusi-jarjestys (swap! jarjestys conj id)]
                                (swap! historia conj [vanhat-tiedot vanhat-virheet vanha-jarjestys])
                                (when muutos
                                  (muutos this))))
                 (hae-muokkaustila [_]
                   @muokatut)
                 (hae-virheet [_]
                   @virheet)
                 (nollaa-historia! [_]
                   (reset! historia []))
                 )
        
        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [id funktio & argumentit]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut
                                             (fn [muokatut]
                                               (update-in muokatut [id]
                                                          (fn [rivi]
                                                            (apply funktio (dissoc rivi :koskematon) argumentit)))))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       ;;(log "VANHAT: " (pr-str vanhat-tiedot) "\nUUDET: " (pr-str uudet-tiedot))
                       (swap! historia conj [vanhat-tiedot vanhat-virheet vanha-jarjestys])
                       (swap! virheet (fn [virheet]
                                        (let [uusi-rivi (get uudet-tiedot id)
                                              rivin-virheet (when-not (:poistettu uusi-rivi)
                                                              (validoi-rivi uudet-tiedot uusi-rivi skeema))]
                                          (if (empty? rivin-virheet)
                                            (dissoc virheet id)
                                            (assoc virheet id rivin-virheet))))))
                     (when muutos
                       (muutos ohjaus))))

         



        ;; Peruu yhden muokkauksen
        peru! (fn []
                (let [[muok virh jarj] (peek @historia)]
                  (reset! muokatut muok)
                  (reset! virheet virh)
                  (reset! jarjestys jarj))
                (swap! historia pop)
                (when muutos
                  (muutos ohjaus)))

        nollaa-muokkaustiedot! (fn []
                                 (reset! virheet {})
                                 (reset! muokatut nil)
                                 (reset! jarjestys nil)
                                 (reset! historia nil)
                                 (reset! viime-assoc nil)
                                 (reset! uusi-id 0))
        aloita-muokkaus! (fn [tiedot]
                           (nollaa-muokkaustiedot!)
                           (loop [muok {}
                                  jarj []
                                  [r & rivit] tiedot]
                             (if-not r
                               (do 
                                 (reset! muokatut muok)
                                 (reset! jarjestys jarj)) 
                               (if (otsikko? r)
                                 (recur muok
                                        (conj jarj r)
                                        rivit)
                                 (let [id ((or tunniste :id) r)]
                                   (recur (assoc muok
                                            id r)
                                          (conj jarj id)
                                          rivit)))))
                           nil)
        ]

    (when-let [ohj (:ohjaus opts)]
      (aseta-grid ohj ohjaus))
    
    (when muokkaa-aina
      (aloita-muokkaus! tiedot))
    
    (r/create-class
      {:component-will-receive-props
       (fn [this new-argv]
         ;; jos gridin data vaihtuu, muokkaustila on peruttava, jotta uudet datat tulevat näkyviin
         (nollaa-muokkaustiedot!)
         (when muokkaa-aina
           (aloita-muokkaus! (nth new-argv 3))))

       :reagent-render
       (fn [{:keys [otsikko tallenna voi-poistaa? voi-lisata? rivi-klikattu muokkaa-footer muokkaa-aina uusi-rivi tyhja]} skeema tiedot]
         (let [muokataan (not (nil? @muokatut))]
           [:div.panel.panel-default.grid
            [:div.panel-heading
             [:h6.panel-title otsikko

              ]

             (if-not muokataan
               [:span.pull-right
                (when tallenna
                  [:button.btn.btn-primary.btn-sm (if (= :ei-mahdollinen tallenna)
                                                    {:disabled (= :ei-mahdollinen tallenna)}
                                                    {:on-click #(do (.preventDefault %)
                                                                    (aloita-muokkaus! tiedot))})
                   (ikonit/pencil) " Muokkaa"])]
               [:span.pull-right.muokkaustoiminnot
                [:button.btn.btn-sm.btn-default
                 {:disabled (empty? @historia)
                  :on-click #(do (.stopPropagation %)
                                 (.preventDefault %)
                                 (peru!))}
                 (ikonit/peru) " Kumoa"]

                (when-not (= false voi-lisata?)
                  [:button.btn.btn-default.btn-sm.grid-lisaa {:on-click #(do (.preventDefault %)
                                                                             (lisaa-rivi! ohjaus {}))}
                   (ikonit/plus-sign) " Lisää rivi"])

                [:span {:class (str (if (empty? @virheet)
                                      "hide"
                                      "taulukossa-virheita"))}
                 [:span.hidden-xs (if (> (count @virheet) 1)
                                    "Korjaa virheet ennen tallennusta "
                                    "Korjaa virhe ennen tallennusta ")]
                 (ikonit/warning-sign)]
                (when-not muokkaa-aina
                  [:button.btn.btn-primary.btn-sm.grid-tallenna
                   {:disabled (not (empty? @virheet))
                    :on-click #(do (.preventDefault %)
                                   (go (if (<! (tallenna (filter (fn [rivi] (not (:koskematon rivi))) (mapv second @muokatut))))
                                         (nollaa-muokkaustiedot!))))} ;; kutsu tallenna-fn: määrittele paluuarvo?
                   (ikonit/ok) " Tallenna"])

                (when-not muokkaa-aina
                  [:button.btn.btn-default.btn-sm.grid-peru
                   {:on-click #(do (.preventDefault %) (nollaa-muokkaustiedot!) nil)}
                   (ikonit/ban-circle) " Peruuta"])
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
                   (let [muokatut @muokatut
                         jarjestys @jarjestys]
                     (if (empty? muokatut)
                       [:tr.tyhja [:td {:colSpan (inc (count skeema))} tyhja]]
                       (let [kaikki-virheet @virheet]
                         (doall (map-indexed
                                  (fn [i id]
                                    (if (otsikko? id)
                                      (let [teksti (:teksti id)]
                                        ^{:key teksti}
                                        [:tr.otsikko
                                         [:td {:colSpan (inc (count skeema))}
                                          [:h5 teksti]]])
                                      (let [rivi (get muokatut id)
                                            rivin-virheet (get kaikki-virheet id)]
                                        (when-not (:poistettu rivi)
                                          ^{:key id}
                                          [:tr.muokataan {:class (str (if (even? i)
                                                                        "parillinen"
                                                                        "pariton"))}
                                           (for [{:keys [nimi hae aseta fmt muokattava? tasaa] :as s} skeema]
                                             (let [arvo (if hae
                                                          (hae rivi)
                                                          (get rivi nimi))
                                                   kentan-virheet (get rivin-virheet nimi)
                                                   tasaus-luokka (if (= tasaa :oikea) "tasaa-oikealle" "")]
                                               (if (or (nil? muokattava?) (muokattava? rivi))
                                                 ^{:key (str nimi)}
                                                 [:td {:class (str tasaus-luokka (when-not (empty? kentan-virheet)
                                                                                   "has-error"))}
                                                  (when-not (empty? kentan-virheet)
                                                    [:div.virheet
                                                     [:div.virhe
                                                      (for [v kentan-virheet]
                                                        ^{:key (hash v)}
                                                        [:span v])]])
                                                  [tee-kentta s (r/wrap
                                                                  arvo
                                                                  (fn [uusi]
                                                                    (if aseta
                                                                      (muokkaa! id (fn [rivi]
                                                                                     (aseta rivi uusi)))
                                                                      (muokkaa! id assoc nimi uusi))))]]
                                                 ^{:key (str nimi)}
                                                 [:td {:class tasaus-luokka}
                                                  ((or fmt str) (if hae
                                                                  (hae rivi)
                                                                  (get rivi nimi)))])))
                                           [:td.toiminnot
                                            (when (or (nil? voi-poistaa?) (voi-poistaa? rivi))
                                              [:span {:on-click #(do (.preventDefault %)
                                                                     (muokkaa! id assoc :poistettu true))}
                                               (ikonit/trash)])]]))))
                                  jarjestys)))))

                   ;; Näyttömuoto
                   (let [rivit tiedot]
                     (if (empty? rivit)
                       [:tr.tyhja [:td {:col-span (inc (count skeema))} tyhja]]
                       (doall (map-indexed
                                (fn [i rivi]
                                  (if (otsikko? rivi)
                                    ^{:key (:teksti rivi)}
                                    [:tr.otsikko
                                     [:td {:colSpan (inc (count skeema))}
                                      [:h5 (:teksti rivi)]]]

                                    ^{:key ((or tunniste :id) rivi)}
                                    [:tr {:class    (str (if (even? i) "parillinen" "pariton")
                                                         (when rivi-klikattu
                                                           " klikattava"))
                                          :on-click (when rivi-klikattu
                                                      #(rivi-klikattu rivi))}
                                     (for [{:keys [nimi hae fmt tasaa]} skeema]
                                       ^{:key (str nimi)}
                                       [:td {:class
                                             (if (= tasaa :oikea) "tasaa-oikealle" "")}
                                        ((or fmt str) (if hae
                                                            (hae rivi)
                                                            (get rivi nimi)))])]))
                                rivit)))))]])
             (when (and muokataan muokkaa-footer)
               [muokkaa-footer ohjaus])
             ]]))})))


(defn muokkaus-grid
  "Versio gridistä, jossa on vain muokkaustila. Tilan tulee olla muokkauksen vaatimassa {<id> <tiedot>} array mapissa.
  Tiedot tulee olla atomi tai wrapatty data, jota tietojen muokkaus itsessään manipuloi.

Optiot on mappi optioita:
  :muokkaa-footer  optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muutos          jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                   Parametrina Grid ohjauskahva
  :uusi-rivi       jos annettu uuden rivin tiedot käsitellään tällä funktiolla 
  :voi-muokata?    jos false, tiedot eivät ole muokattavia ollenkaan 
  :voi-lisata?     jos false, uusia rivejä ei voi lisätä
  :jarjesta         jos annettu funktio, sortataan rivit tämän mukaan
  "
  [{:keys [otsikko tyhja tunniste voi-poistaa? rivi-klikattu
           voi-muokata? voi-lisata? jarjesta
           muokkaa-footer muutos uusi-rivi] :as opts} skeema muokatut]
  (let [uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet (atom {}) ;; validointivirheet: (:id rivi) => [virheet]
        viime-assoc (atom nil) ;; edellisen muokkauksen, jos se oli assoc-in, polku

        ohjaus (reify Grid
                 (lisaa-rivi! [this rivin-tiedot]
                              (let [id (or (:id rivin-tiedot) (swap! uusi-id dec))
                                    vanhat-tiedot @muokatut
                                    vanhat-virheet @virheet
                                    uudet-tiedot (swap! muokatut assoc id
                                                        ((or uusi-rivi identity)
                                                         (merge rivin-tiedot {:id id :koskematon true})))]
                                (swap! historia conj [vanhat-tiedot vanhat-virheet])
                                (swap! virheet (fn [virheet]
                                                 (let [rivin-virheet (validoi-rivi uudet-tiedot (get uudet-tiedot id) skeema)]
                                                   (if (empty? rivin-virheet)
                                                     (dissoc virheet id)
                                                     (assoc virheet id rivin-virheet)))))
                                (when muutos
                                  (muutos this))))
                 (hae-muokkaustila [_]
                   @muokatut)
                 (hae-virheet [_]
                   @virheet)
                 (nollaa-historia! [_]
                   (reset! historia [])))
        
        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [id funktio & argumentit]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         uudet-tiedot (swap! muokatut
                                             (fn [muokatut]
                                               (update-in muokatut [id]
                                                          (fn [rivi]
                                                            (apply funktio (dissoc rivi :koskematon) argumentit)))))]
                     
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       (swap! historia conj [vanhat-tiedot vanhat-virheet])
                       (swap! virheet (fn [virheet]
                                        (let [uusi-rivi (get uudet-tiedot id)
                                              rivin-virheet (when-not (:poistettu uusi-rivi)
                                                              (validoi-rivi uudet-tiedot uusi-rivi skeema))]
                                          (if (empty? rivin-virheet)
                                            (dissoc virheet id)
                                            (assoc virheet id rivin-virheet))))))
                     (when muutos
                       (muutos ohjaus))))
        
        
        ;; Peruu yhden muokkauksen
        peru! (fn []
                (let [[muok virh] (peek @historia)]
                  (reset! muokatut muok)
                  (reset! virheet virh))
                (swap! historia pop)
                (when muutos
                  (muutos ohjaus)))

        
        ]
    (when-let [ohj (:ohjaus opts)]
      (aseta-grid ohj ohjaus))
    
    (r/create-class
     {:component-will-receive-props
      (fn [this new-argv]
        ;; HUOM: tätä metodia ei tietenkään tässä versiossa kutsuta, jos parametrinä olevan atomi ei vaihdu
        ;; atomin *sisällön* muutoksista tätä ei kutsuta, siksi tarvitaan nollaa-historia!
        )
      
      :reagent-render 
      (fn [{:keys [otsikko tallenna jarjesta voi-poistaa? voi-muokata? voi-lisata? rivi-klikattu muokkaa-footer muokkaa-aina uusi-rivi tyhja]} skeema muokatut]
        [:div.panel.panel-default.grid
         [:div.panel-heading
          [:h6.panel-title otsikko]
          (when (not= false voi-muokata?)
            [:span.pull-right.muokkaustoiminnot
             [:button.btn.btn-sm.btn-default
              {:disabled  (empty? @historia)
               :on-click #(do (.stopPropagation %)
                              (.preventDefault %)
                              (peru!))}
              (ikonit/peru) " Kumoa"]
             (when (not= false voi-lisata?)
               [:button.btn.btn-default.btn-sm.grid-lisaa {:on-click #(do (.preventDefault %)
                                                                          (lisaa-rivi! ohjaus {}))}
                (ikonit/plus-sign) " Lisää rivi"])])]
         [:div.panel-body
          [:table.grid
           [:thead
            [:tr
             (for [{:keys [otsikko leveys nimi]} skeema]
               ^{:key (str nimi)}
               [:th {:width leveys} otsikko])
             [:th.toiminnot {:width "5%"} " "]
             [:th.toiminnot ""]]]

           [:tbody
            (let [muokatut @muokatut]
              (if (empty? muokatut)
                [:tr.tyhja [:td {:col-span (inc (count skeema))} tyhja]]
                (let [kaikki-virheet @virheet]
                  (doall (map-indexed
                          (fn [i [id rivi]]
                            (let [rivin-virheet (get kaikki-virheet id)]
                              (when-not (:poistettu rivi)
                                ^{:key id}
                                [:tr.muokataan {:class (str (if (even? i)
                                                              "parillinen"
                                                              "pariton"))}
                                 (for [{:keys [nimi hae aseta fmt muokattava?] :as s} skeema]
                                   (let [arvo (if hae
                                                (hae rivi)
                                                (get rivi nimi))
                                         kentan-virheet (get rivin-virheet nimi)]
                                     (if (or (nil? muokattava?) (muokattava? rivi))
                                       ^{:key (str nimi)}
                                       [:td {:class (str (when-not (empty? kentan-virheet)
                                                           "has-error"))}
                                        (when-not (empty? kentan-virheet)
                                          [:div.virheet
                                           [:div.virhe
                                            (for [v kentan-virheet]
                                              ^{:key (hash v)}
                                              [:span v])]])
                                        [tee-kentta s (r/wrap
                                                       arvo
                                                       (fn [uusi]
                                                         (if aseta
                                                           (muokkaa! id (fn [rivi]
                                                                          (aseta rivi uusi)))
                                                           (muokkaa! id assoc nimi uusi))))]]
                                       ^{:key (str nimi)}
                                       [:td ((or fmt str) (if hae
                                                            (hae rivi)
                                                            (get rivi nimi)))])))
                                 [:td.toiminnot
                                  (when (or (nil? voi-poistaa?) (voi-poistaa? rivi))
                                    [:span {:on-click #(do (.preventDefault %)
                                                           (muokkaa! id assoc :poistettu true))}
                                     (ikonit/trash)])]])))
                          (if jarjesta
                            (sort-by (comp jarjesta second) (seq muokatut))
                            (seq muokatut)))))))]]
          (when (and (not= false voi-muokata?) muokkaa-footer)
            [muokkaa-footer ohjaus])
          ]])})))






