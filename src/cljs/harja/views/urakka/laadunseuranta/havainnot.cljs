(ns harja.views.urakka.laadunseuranta.havainnot
  "Listaa urakan havainnot, jotka voivat olla joko tarkastukseen liittyviä tai irrallisia."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce listaus (atom :kaikki))

(defonce hoitokauden-kuukaudet
  (reaction (some-> @tiedot-urakka/valittu-hoitokausi
                    pvm/hoitokauden-kuukausivalit)))

(defonce aikavali
  (reaction (first @hoitokauden-kuukaudet)))

(defonce urakan-havainnot
  (reaction<! (let [urakka-id (:id @nav/valittu-urakka)
                    [alku loppu] @aikavali
                    listaus @listaus]
                (when (and urakka-id alku loppu)
                  (laadunseuranta/hae-urakan-havainnot listaus urakka-id alku loppu)))))

                    
(defonce valittu-havainto-id (atom nil))

(defonce valittu-havainto
  (reaction<!
   (when-let [id @valittu-havainto-id]
     (if (= :uusi id)
       (go {})
       (laadunseuranta/hae-havainnon-tiedot (:id @nav/valittu-urakka) id)))
   (fn [havainto]
     ;; Tarvitsemme urakan liitteen linkitystä varten
     (assoc havainto :urakka (:id @nav/valittu-urakka)))))

(defn kuvaile-kasittelytapa [kasittelytapa]
  (case kasittelytapa
    :tyomaakokous "Työmaakokous"
    :puhelin "Puhelimitse"
    :kommentit "Harja-kommenttien perusteella"
    :muu "Muu tapa"
    nil))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :sanktio "Sanktio"
    :ei_sanktiota "Ei sanktiota"
    :hylatty "Hylätty"))
  
(defn kuvaile-paatos [{:keys [kasittelyaika paatos kasittelytapa]}]
  (when paatos
    (str
     (pvm/pvm kasittelyaika)
     " "
     (kuvaile-paatostyyppi paatos)
     " ("
     (kuvaile-kasittelytapa kasittelytapa) ")")))

(defn kuvaile-tekija [tekija]
  (case tekija
    :tilaaja "Tilaaja"
    :urakoitsija "Urakoitsija"
    :konsultti "Konsultti"))


(def +testidata+
  [{:id 1
    :pvm (pvm/->pvm-aika "2.6.2015 08:22")
    :kohde "Tie 8 Sammalniemen kohdalla liukkautta"
    :tekija :konsultti
    :paatos nil
    :kommentit [{:pvm (pvm/->pvm-aika "2.6.2015 08:22")
                 :tekija "Late Laadukas"
                 :kommentti "Tie 8 Sammalniemen kohdalla liukkautta. Kitkamittaus tehty, arvo 0,15 alittaa laatuvaatimukset vaarallisesti."}]
    }

   {:id 2
    :pvm (pvm/->pvm-aika "22.3.2015 11:07")
    :kohde "Vt 20 / Kuusamontien risteys"
    :paatos {:pvm (pvm/->pvm-aika "28.3.2015 15:00")
             :paatos :sanktio
             :kasittelytapa :tyomaakokous
             :selitys "Työmaakokouksessa käsitelty asia, urakoitsija myöntänyt aliurakoitsijan jättäneen alueen hoitamatta."}
    :tekija :tilaaja
    :kommentit [{:pvm (pvm/->pvm-aika "22.3.2015 11:07")
                 :tekija "Sami Sanktioija"
                 :rooli :tilaaja
                 :kommentti "Vt 20 Kuusamontien risteyksessä on täysin jätetty auraamatta. Nyt 30cm lunta ja polanteet hyvin vaarallisia."}]
    :sanktiot {1 {:perintapvm (pvm/->pvm "8.4.2014")
                  :ryhma :A
                  :summa 3500
                  :indeksi "MAKU 2005"}}}
      

   {:id 3
    :pvm (pvm/luo-pvm 2015 1 25)
    :kohde "Tie 123, aet 100 auraamatta"
    :tekija :urakoitsija
    :kommentit [{:pvm (pvm/luo-pvm 2015 1 25)
                 :rooli :urakoitsija
                 :tekija "Unto Urakoistija"
                 :kommentti "Emme voineet aurata tie 123, alusta, tiellä oli pysähtynyt yhdistelmärekka. Katso kuvat!"
                 :liitteet [{:nimi "rekka_kaatui.jpg"
                             :koko 70272
                             :tyyppi "image/jpeg"
                             :pikkukuva-url "/images/rekka_kaatui_thumbnail.jpg"
                             :url "/images/rekka_kaatui.jpg"}]}
                  
                {:pvm (pvm/luo-pvm 2015 1 27)
                 :rooli :tilaaja
                 :tekija "Antti Anteeksiantava"
                 :kommentti "Näyttää tosiaan olevan, ei hätää, sitähän sattuu kaikille..."}]}
     
                   

   ])


(defn havaintolistaus
  "Listaa urakan havainnot"
  []

  [:div.havainnot

   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]

   
   
   [yleiset/pudotusvalikko
    "Kuukausi"
    {:valinta @aikavali
     :valitse-fn #(reset! aikavali  %)
     :format-fn (fn [[kk _]] (str (pvm/kuukauden-nimi (pvm/kuukausi kk)) " " (pvm/vuosi kk)))}
    @hoitokauden-kuukaudet]

   [yleiset/pudotusvalikko
    "Näytä havainnot"
    {:valinta @listaus
     :valitse-fn #(reset! listaus %)
     :format-fn #(case %
                   :kaikki "Kaikki"
                   :kasitellyt "Käsitellyt (päätös tehty)"
                   :selvitys "Odottaa urakoitsijan selvitystä"
                   :omat "Minun kirjaamat / kommentoimat")}

    [:kaikki :selvitys :kasitellyt :omat]]


   [:button.nappi-ensisijainen {:on-click #(reset! valittu-havainto-id :uusi)}
    (ikonit/plus-sign)
    " Uusi havainto"]
    
     
   [grid/grid
    {:otsikko "Havainnot" :rivi-klikattu #(reset! valittu-havainto-id (:id %))
     :tyhja "Ei havaintoja."}
    [{:otsikko "Päivämäärä" :nimi :aika :fmt pvm/pvm-aika :leveys "10%"}
     {:otsikko "Kohde" :nimi :kohde :leveys "25%"}
     {:otsikko "Tekijä" :nimi :tekija :leveys "25%" :fmt kuvaile-tekija}
     {:otsikko "Päätös" :nimi :paatos :fmt kuvaile-paatos :leveys "35%"} ;; Päätös
     ]

    @urakan-havainnot
    ]])

(defn kommentit [{:keys [voi-kommentoida? kommentoi! uusi-kommentti placeholder]} kommentit]
  (log "KOMMENTIT: " (pr-str kommentit))
  [:div.kommentit
   (for [{:keys [aika tekijanimi kommentti tekija liite]} kommentit]
     ^{:key (pvm/millisekunteina aika)}
     [:div.kommentti {:class (when tekija (name tekija))}
      [:span.kommentin-tekija tekijanimi]
      [:span.kommentin-aika (pvm/pvm-aika aika)]
      [:div.kommentin-teksti kommentti]
      (when liite
        [liitteet/liitetiedosto liite])])
   (when voi-kommentoida?
     [:div.uusi-kommentti
      [:div.uusi-kommentti-teksti
       [kentat/tee-kentta {:tyyppi :text :nimi :teksti
                           :placeholder (or placeholder "Kirjoita uusi kommentti...")
                           :koko [80 :auto]}
        (r/wrap (:kommentti @uusi-kommentti) #(swap! uusi-kommentti assoc :kommentti %))]]
      (when kommentoi!
        [:button.nappi-ensisijainen.uusi-kommentti-tallenna
         {:on-click #(kommentoi! @uusi-kommentti)
          :disabled (str/blank? (:kommentti @uusi-kommentti))}
         "Tallenna kommentti"])
      [liitteet/liite {:urakka-id (:id @nav/valittu-urakka)
                       :liite-ladattu #(swap! uusi-kommentti assoc :liite %)}]])])


(defn paatos?
  "Onko annetussa havainnossa päätös?"
  [havainto]
  (not (nil? (get-in havainto [:paatos :paatos]))))


(defn tallenna-havainto
  "Tallentaa annetun havainnon palvelimelle. Lukee serveriltä palautuvan havainnon ja 
   päivittää/lisää sen nykyiseen listaukseen, jos se kuuluu listauksen aikavälille."
  [havainto]
  (go 
    (let [tulos (<! (laadunseuranta/tallenna-havainto havainto))]
      (if (k/virhe? tulos)
        ;; Palautetaan virhe, jotta nappi näyttää virheviestin
        tulos

        ;; Havainto tallennettu onnistuneesti, päivitetään sen tiedot
        (let [uusi-havainto tulos
              aika (:aika uusi-havainto)
              [alku loppu] @aikavali]
          (when (and (pvm/sama-tai-jalkeen? aika alku)
                     (pvm/sama-tai-ennen? aika loppu))
            ;; Kuuluu aikavälille, lisätään tai päivitetään
            (if (:id havainto)
              ;; Päivitetty olemassaolevaa
              (swap! urakan-havainnot
                     (fn [havainnot]
                       (mapv (fn [h]
                               (if (= (:id h) (:id uusi-havainto))
                                 uusi-havainto
                                 h)) havainnot)))
              ;; Luotu uusi
              (swap! urakan-havainnot
                     conj uusi-havainto)))
          true)))))
      
(defn havainto [havainto]
  (let [havainto (atom havainto)]
    (tarkkaile! "Havainto: " havainto)
    (komp/luo
     {:component-will-receive-props
      (fn [this uusi-havainto]
        (reset! havainto uusi-havainto))}
     
     (fn [alkuperainen]
       (let [muokattava? (constantly (not (paatos? alkuperainen)))
             uusi? (not (:id alkuperainen))]
         [:div.havainto
          [:button.nappi-toissijainen {:on-click #(reset! valittu-havainto-id nil)}
           (ikonit/chevron-left) " Takaisin havaintoluetteloon"]

          #_[(aget js/window "FOO") 1]
          [:h3 "Havainnon tiedot"]
          [lomake/lomake
           {:muokkaa! #(reset! havainto %)
            :luokka :horizontal
            :footer [napit/palvelinkutsu-nappi
                     ;; Määritellään "verbi" tilan mukaan, jos päätöstä ei ole: Tallennetaan havainto,
                     ;; jos päätös on tässä muokkauksessa lisätty: Lukitaan havainto
                     (cond
                      (and (not (paatos? alkuperainen))
                           (paatos? @havainto))
                      "Tallenna ja lukitse havainto"
                      
                      :default
                      "Tallenna havainto")
                     
                     #(tallenna-havainto @havainto)
                     {:ikoni (ikonit/check)
                      :disabled (let [h @havainto]
                                  (not (and (:toimenpideinstanssi h)
                                            (:aika h))))
                      :kun-onnistuu (fn [_] (reset! valittu-havainto-id nil))}]}
           [
            {:otsikko "Toimenpide" :nimi :toimenpideinstanssi
             :tyyppi :valinta
             :valinnat @tiedot-urakka/urakan-toimenpideinstanssit
             :valinta-nayta #(or (:tpi_nimi %) "- valitse toimenpide -")
             :valinta-arvo :tpi_id
             :leveys-col 4
             :validoi [[:ei-tyhja "Valitse urakan toimenpide"]]}
            
            {:otsikko "Havainnon pvm ja aika"
             :tyyppi :pvm-aika
             :nimi :aika
             :validoi [[:ei-tyhja "Anna havainnon päivämäärä ja aika"]]}
            
            {:otsikko "Tekijä" :nimi :tekija
             :tyyppi :valinta
             :valinnat [:tilaaja :urakoitsija :konsultti]
             :valinta-nayta #(case %
                               :tilaaja "Tilaaja"
                               :urakoitsija "Urakoitsija"
                               :konsultti "Konsultti"
                               "- valitse osapuoli -")
             :leveys-col 4
             :muokattava? muokattava?
             :validoi [[:ei-tyhja "Valitse havainnon tehnyt osapuoli"]]}

            (when-not (= :urakoitsija (:tekija @havainto))
              {:otsikko "Urakoitsijan selvitystä pyydetään"
               :nimi :selvitys-pyydetty
               :tyyppi :boolean})

            {:otsikko "Kohde" :tyyppi :string :nimi :kohde
             :leveys-col 4
             :muokattava? muokattava?
             :validoi [[:ei-tyhja "Anna havainnon kohde"]]}

            {:otsikko "Kuvaus ja kommentit" :nimi :kommentit
             :komponentti [kommentit {:voi-kommentoida? true
                                      :placeholder (if uusi?
                                                     "Kirjoita kuvaus..."
                                                     "Kirjoita kommentti...")
                                      :uusi-kommentti (r/wrap (:uusi-kommentti @havainto)
                                                              #(swap! havainto assoc :uusi-kommentti %))}
                           (:kommentit @havainto)]
             }

            ;; Päätös
            (when (:id alkuperainen)
              (lomake/ryhma
               "Käsittely ja päätös"
           
               {:otsikko "Käsittelyn pvm"
                :nimi :paatos-pvm
                :hae (comp :kasittelyaika :paatos) :aseta #(assoc-in %1 [:paatos :kasittelyaika] %2)                
                :tyyppi :pvm-aika
                :muokattava? muokattava?}
           
               {:otsikko "Käsitelty" :nimi :kasittelytapa
                :hae (comp :kasittelytapa :paatos)
                :aseta #(assoc-in %1 [:paatos :kasittelytapa] %2)
                :tyyppi :valinta
                :valinnat [:tyomaakokous :puhelin :kommentit :muu]
                :valinta-nayta #(if % (kuvaile-kasittelytapa %) "- valitse käsittelytapa -")
                :leveys-col 4
                :muokattava? muokattava?}
           
               (when (= :muu (:kasittelytapa (:paatos @havainto)))
                 {:otsikko "Muu käsittelytapa"
                  :nimi :kasittelytapa-selite
                  :hae (comp :kasittelytapa-selite :paatos)
                  :aseta #(assoc-in %1 [:paatos :muukasittelytapa] %2)
                  :tyyppi :string
                  :leveys-col 4
                  :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]
                  :muokattava? muokattava?})


               {:otsikko "Päätös"
                :nimi :paatos-paatos
                :tyyppi :valinta
                :valinnat [:sanktio :ei_sanktiota :hylatty]
                :hae (comp :paatos :paatos)
                :aseta #(assoc-in %1 [:paatos :paatos] %2)
                :valinta-nayta #(if % (kuvaile-paatostyyppi %) "- valitse päätös -")
                :leveys-col 4
                :muokattava? muokattava?}

               (when (:paatos (:paatos @havainto))
                 {:otsikko "Päätöksen selitys"
                  :nimi :paatoksen-selitys
                  :tyyppi :text
                  :hae (comp :perustelu :paatos)
                  :koko [80 4]
                  :leveys-col 6
                  :aseta #(assoc-in %1 [:paatos :perustelu] %2)
                  :muokattava? muokattava?})


               (when (= :sanktio (:paatos (:paatos @havainto)))
                 ;; FIXME: tarkista myös oikeus, urakanvalvoja... urakoitsija/konsultti EI saa päätöstä tehdä
                 {:otsikko "Sanktiot"
                  :nimi :sanktiot
                  :komponentti [:div.sanktiot
                                [grid/muokkaus-grid
                                 {:tyhja "Ei kirjattuja sanktioita."
                                  :lisaa-rivi " Lisää sanktio"}
                                 [{:otsikko "Perintäpvm" :nimi :perintapvm :tyyppi :pvm :leveys "20%"}
                                  {:otsikko "Sakkoryhmä" :tyyppi :valinta :leveys "25%"
                                   :nimi :ryhma
                                   :valinnat [:A :B :C :muistutus]
                                   :valinta-nayta #(case %
                                                     :A "Ryhmä A"
                                                     :B "Ryhmä B"
                                                     :C "Ryhmä C"
                                                     :muistutus "Muistutus"
                                                     "- valitse ryhmä -")}
                                  {:otsikko "Sakko (€)" :nimi :summa :tyyppi :numero :leveys "15%"}
                                  {:otsikko "Sidotaan indeksiin" :nimi :indeksi :leveys "35%"
                                   :tyyppi :valinta
                                   :valinnat ["MAKU 2005" "MAKU 2010"] ;; FIXME: haetaanko indeksit tiedoista?
                                   :valinta-nayta #(or % "Ei sidota indeksiin")}
                                  ]

                                 (r/wrap (:sanktiot @havainto) #(swap! havainto assoc :sanktiot %))]]})
               ))]
         
           @havainto]])))))
  

(defn havainnot []
  (if-let [valittu @valittu-havainto]
    [havainto valittu]
    [havaintolistaus]))
  
  
  
