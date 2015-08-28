(ns harja.views.urakka.laadunseuranta.havainnot
  "Listaa urakan havainnot, jotka voivat olla joko tarkastukseen liittyviä tai irrallisia."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta :refer [validi-havainto?]]
            [harja.tiedot.istunto :as istunto]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce listaus (atom :kaikki))


(defonce urakan-havainnot
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      [alku loppu] @tiedot-urakka/valittu-aikavali
                      laadunseurannassa? @laadunseuranta/laadunseurannassa?
                      valilehti @laadunseuranta/valittu-valilehti
                      listaus @listaus]
                     (log "urakka-id: " urakka-id "; alku: " alku "; loppu: " loppu "; laadunseurannassa? " laadunseurannassa? "; valilehti: " (pr-str valilehti) "; listaus: " (pr-str listaus))
                     (when (and laadunseurannassa? (= :havainnot valilehti)
                                urakka-id alku loppu)
                       (laadunseuranta/hae-urakan-havainnot listaus urakka-id alku loppu))))


(defonce valittu-havainto-id (atom nil))

(defn uusi-havainto []
  {:tekija (roolit/osapuoli @istunto/kayttaja (:id @nav/valittu-urakka))})

(defonce valittu-havainto
         (reaction<! [id @valittu-havainto-id]
                     (when id
                       (go (let [havainto (if (= :uusi id)
                                            (uusi-havainto)
                                            (<! (laadunseuranta/hae-havainnon-tiedot (:id @nav/valittu-urakka) id)))]
                             (-> havainto

                                 ;; Tarvitsemme urakan liitteen linkitystä varten

                                 (assoc :urakka (:id @nav/valittu-urakka))
                                 (assoc :sanktiot (into {}
                                                        (map (juxt :id identity) (:sanktiot havainto))))))))))

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
    :konsultti "Konsultti"
    "Ei tiedossa"))


(defn havaintolistaus
  "Listaa urakan havainnot"
  []

  [:div.havainnot

   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]

   [yleiset/pudotusvalikko
    "Näytä havainnot"
    {:valinta    @listaus
     :valitse-fn #(reset! listaus %)
     :format-fn  #(case %
                   :kaikki "Kaikki"
                   :kasitellyt "Käsitellyt (päätös tehty)"
                   :selvitys "Odottaa urakoitsijan selvitystä"
                   :omat "Minun kirjaamat / kommentoimat")}

    [:kaikki :selvitys :kasitellyt :omat]]

   [urakka-valinnat/aikavali @nav/valittu-urakka]

   (when @laadunseuranta/voi-kirjata?
     [napit/uusi "Uusi havainto" #(reset! valittu-havainto-id :uusi)])

   [grid/grid
    {:otsikko "Havainnot" :rivi-klikattu #(reset! valittu-havainto-id (:id %))
     :tyhja   "Ei havaintoja."}
    [{:otsikko "Päivämäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}
     {:otsikko "Kohde" :nimi :kohde :leveys 1}
     {:otsikko "Kuvaus" :nimi :kuvaus :leveys 3}
     {:otsikko "Tekijä" :nimi :tekija :leveys 1 :fmt kuvaile-tekija}
     {:otsikko "Päätös" :nimi :paatos :fmt kuvaile-paatos :leveys 2} ;; Päätös
     ]

    @urakan-havainnot
    ]])

(defn paatos?
  "Onko annetussa havainnossa päätös?"
  [havainto]
  (not (nil? (get-in havainto [:paatos :paatos]))))


(defn tallenna-havainto
  "Tallentaa annetun havainnon palvelimelle. Lukee serveriltä palautuvan havainnon ja 
   päivittää/lisää sen nykyiseen listaukseen, jos se kuuluu listauksen aikavälille."
  [havainto]
  (let [havainto (-> havainto
                     (assoc :sanktiot (vals (:sanktiot havainto))))]
    (go
      (let [tulos (<! (laadunseuranta/tallenna-havainto havainto))]
        (if (k/virhe? tulos)
          ;; Palautetaan virhe, jotta nappi näyttää virheviestin
          tulos

          ;; Havainto tallennettu onnistuneesti, päivitetään sen tiedot
          (let [uusi-havainto tulos
                aika (:aika uusi-havainto)
                [alku loppu] @tiedot-urakka/valittu-aikavali]
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
            true))))))

(defn kuvaile-sanktion-sakko [{:keys [sakko? summa indeksi]}]
  (if-not sakko?
    "Muistutus"
    (str "Sakko " (fmt/euro-opt summa)
         (when indeksi (str " (" indeksi ")")))))


(defn havainnon-sanktiot
  "Näyttää muokkaus-gridin havainnon sanktioista. Ottaa kaksi parametria, sanktiot (muokkaus-grid muodossa)
sekä sanktio-virheet atomin, jonne yksittäisen sanktion virheet kirjoitetaan (id avaimena)"
  [_ _]
  (let [g (grid/grid-ohjaus)]
    (fn [sanktiot-atom sanktio-virheet]
      [:div.sanktiot
       [grid/muokkaus-grid
        {:tyhja        "Ei kirjattuja sanktioita."
         :lisaa-rivi   " Lisää sanktio"
         :ohjaus       g
         :uusi-rivi    (fn [rivi]
                         (grid/avaa-vetolaatikko! g (:id rivi))
                         (assoc rivi :sakko? true))
         :vetolaatikot (into {}
                             (map (juxt first
                                        (fn [[id sanktio]]
                                          [lomake/lomake
                                           {:otsikko  "Sanktion tiedot"
                                            :luokka   :horizontal
                                            :muokkaa! (fn [uudet-tiedot]
                                                        (swap! sanktiot-atom
                                                               assoc id uudet-tiedot))
                                            :virheet  (r/wrap (get @sanktio-virheet id)
                                                              #(swap! sanktio-virheet assoc id %))}
                                           [{:otsikko       "Sakko/muistutus"
                                             :nimi          :sakko?
                                             :tyyppi        :valinta
                                             :hae           #(if (:sakko? %) :sakko :muistutus)
                                             :aseta         #(assoc %1 :sakko? (= :sakko %2))
                                             :valinnat      [:sakko :muistutus]
                                             :valinta-nayta #(case %
                                                              :sakko "Sakko"
                                                              :muistutus "Muistutus")
                                             :leveys-col    2}

                                            (when (:sakko? sanktio)
                                              {:otsikko       "Toimenpide"
                                               :nimi          :toimenpideinstanssi
                                               :tyyppi        :valinta
                                               :valinta-arvo  :tpi_id
                                               :valinta-nayta :tpi_nimi
                                               :valinnat      @tiedot-urakka/urakan-toimenpideinstanssit
                                               :leveys-col    3
                                               :validoi       [[:ei-tyhja "Valitse toimenpide, johon sakko liittyy"]]})

                                            (when (:sakko? sanktio)
                                              {:otsikko    "Sakko (€)"
                                               :tyyppi     :numero
                                               :nimi       :summa
                                               :leveys-col 2
                                               :validoi    [[:ei-tyhja "Anna sakon summa euroina"]]})

                                            (when (:sakko? sanktio)
                                              {:otsikko       "Sidotaan indeksiin" :nimi :indeksi :leveys 2
                                               :tyyppi        :valinta
                                               :valinnat      ["MAKU 2005" "MAKU 2010"] ;; FIXME: haetaanko indeksit tiedoista?
                                               :valinta-nayta #(or % "Ei sidota indeksiin")
                                               :leveys-col    3})

                                            ]
                                           sanktio]))
                                  @sanktiot-atom))}

        [{:tyyppi :vetolaatikon-tila :leveys 0.5}
         {:otsikko "Perintäpvm" :nimi :perintapvm :tyyppi :pvm :leveys 2
          :validoi [[:ei-tyhja "Anna sanktion päivämäärä"]]}
         {:otsikko       "Laji" :tyyppi :valinta :leveys 1
          :nimi          :laji
          :aseta         #(assoc %1
                           :laji %2
                           :tyyppi nil)
          :valinnat      [:A :B :C :muistutus]
          :valinta-nayta #(case %
                           :A "Ryhmä A"
                           :B "Ryhmä B"
                           :C "Ryhmä C"
                           "- valitse -")
          :validoi       [[:ei-tyhja "Valitse laji"]]}
         {:otsikko       "Tyyppi" :nimi :tyyppi :leveys 3
          :tyyppi        :valinta
          :aseta         (fn [sanktio tyyppi]
                           ;; Asetetaan uusi sanktiotyyppi sekä toimenpideinstanssi, joka tähän kuuluu
                           (log "VALITTIIN TYYPPI: " (pr-str tyyppi))
                           (assoc sanktio
                             :tyyppi tyyppi
                             :toimenpideinstanssi (:tpi_id (first (filter #(= (:toimenpidekoodi tyyppi)
                                                                              (:id %))
                                                                          @tiedot-urakka/urakan-toimenpideinstanssit)))))
          :valinnat-fn   #(laadunseuranta/lajin-sanktiotyypit (:laji %))
          :valinta-nayta :nimi
          :validoi       [[:ei-tyhja "Valitse sanktiotyyppi"]]
          }
         {:otsikko     "Sakko" :nimi :summa :hae kuvaile-sanktion-sakko :tyyppi :string :leveys 3.5
          :muokattava? (constantly false)}

         ]

        sanktiot-atom]])))

(defn havainto [asetukset havainto]
  (let [sanktio-virheet (atom {})
        alkuperainen @havainto]
    (komp/luo
      (fn [{:keys [osa-tarkastusta?] :as asetukset} havainto]
        (let [muokattava? (constantly (not (paatos? alkuperainen)))
              uusi? (not (:id alkuperainen))]

          [:div.havainto
           (when-not osa-tarkastusta?
             [napit/takaisin "Takaisin havaintoluetteloon" #(reset! valittu-havainto-id nil)])

           (when-not osa-tarkastusta? [:h3 "Havainnon tiedot"])
           [lomake/lomake
            {:muokkaa!     #(reset! havainto %)
             :luokka       :horizontal
             :voi-muokata? @laadunseuranta/voi-kirjata?
             :footer       (when-not osa-tarkastusta?
                             [napit/palvelinkutsu-nappi
                              ;; Määritellään "verbi" tilan mukaan, jos päätöstä ei ole: Tallennetaan havainto,
                              ;; jos päätös on tässä muokkauksessa lisätty: Lukitaan havainto
                              (cond
                                (and (not (paatos? alkuperainen))
                                     (paatos? @havainto))
                                "Tallenna ja lukitse havainto"

                                :default
                                "Tallenna havainto")

                              #(tallenna-havainto @havainto)
                              {:ikoni        (ikonit/tallenna)
                               :disabled     (not (validi-havainto? @havainto))
                               :kun-onnistuu (fn [_] (reset! valittu-havainto-id nil))}])}

            [(when-not osa-tarkastusta?
               {:otsikko    "Havainnon pvm ja aika"
                :tyyppi     :pvm-aika
                :nimi       :aika
                :validoi    [[:ei-tyhja "Anna havainnon päivämäärä ja aika"]]
                :varoita    [[:urakan-aikana]]
                :leveys-col 5})

             {:otsikko       "Tekijä" :nimi :tekija
              :tyyppi        :valinta
              :valinnat      [:tilaaja :urakoitsija :konsultti]
              :valinta-nayta #(case %
                               :tilaaja "Tilaaja"
                               :urakoitsija "Urakoitsija"
                               :konsultti "Konsultti"
                               "- valitse osapuoli -")
              :leveys-col    4
              :muokattava?   muokattava?
              :validoi       [[:ei-tyhja "Valitse havainnon tehnyt osapuoli"]]}

             (when-not (= :urakoitsija (:tekija @havainto))
               {:otsikko "Urakoitsijan selvitystä pyydetään"
                :nimi    :selvitys-pyydetty
                :tyyppi  :boolean})

             (when-not osa-tarkastusta?
               {:otsikko     "Kohde" :tyyppi :string :nimi :kohde
                :leveys-col  4
                :muokattava? muokattava?
                :validoi     [[:ei-tyhja "Anna havainnon kohde"]]})

             {:otsikko     "Kuvaus" :nimi :kuvaus :tyyppi :text
              :validoi     [[:ei-tyhja "Kirjoita kuvaus"]] :pituus-max 4096
              :placeholder "Kirjoita kuvaus..." :koko [80 :auto]}

             {:otsikko     "Liitteet" :nimi :liitteet
              :komponentti [:span (for [liite (:liitteet @havainto)]
                                    [:span (liitteet/liitetiedosto liite)])
                            [liitteet/liite {:urakka-id     (:id @nav/valittu-urakka)
                                             :liite-ladattu #(swap! havainto assoc :uusi-liite %)
                                             :nappi-teksti " Lisää liite havaintoon"
                                             }]]}

             (when-not uusi?
               (lomake/ryhma
               "Kommentit"
                 {:otsikko     "" :nimi :kommentit
                  :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                                     :voi-liittaa      true
                                                     :liita-nappi-teksti " Lisää liite kommenttiin"
                                                     :placeholder      "Kirjoita kommentti..."
                                                     :uusi-kommentti   (r/wrap (:uusi-kommentti @havainto)
                                                                               #(swap! havainto assoc :uusi-kommentti %))}
                                (:kommentit @havainto)]}))

             ;; Päätös
             (when (:id alkuperainen)
               (lomake/ryhma
                 "Käsittely ja päätös"

                 {:otsikko     "Käsittelyn pvm"
                  :nimi        :paatos-pvm
                  :hae         (comp :kasittelyaika :paatos) :aseta #(assoc-in %1 [:paatos :kasittelyaika] %2)
                  :tyyppi      :pvm-aika
                  :muokattava? muokattava?}

                 {:otsikko       "Käsitelty" :nimi :kasittelytapa
                  :hae           (comp :kasittelytapa :paatos)
                  :aseta         #(assoc-in %1 [:paatos :kasittelytapa] %2)
                  :tyyppi        :valinta
                  :valinnat      [:tyomaakokous :puhelin :kommentit :muu]
                  :valinta-nayta #(if % (kuvaile-kasittelytapa %) "- valitse käsittelytapa -")
                  :leveys-col    4
                  :muokattava?   muokattava?}

                 (when (= :muu (:kasittelytapa (:paatos @havainto)))
                   {:otsikko     "Muu käsittelytapa"
                    :nimi        :kasittelytapa-selite
                    :hae         (comp :muukasittelytapa :paatos)
                    :aseta       #(assoc-in %1 [:paatos :muukasittelytapa] %2)
                    :tyyppi      :string
                    :leveys-col  4
                    :validoi     [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]
                    :muokattava? muokattava?})


                 {:otsikko       "Päätös"
                  :nimi          :paatos-paatos
                  :tyyppi        :valinta
                  :valinnat      [:sanktio :ei_sanktiota :hylatty]
                  :hae           (comp :paatos :paatos)
                  :aseta         #(assoc-in %1 [:paatos :paatos] %2)
                  :valinta-nayta #(if % (kuvaile-paatostyyppi %) "- valitse päätös -")
                  :leveys-col    4
                  :muokattava?   muokattava?}

                 (when (:paatos (:paatos @havainto))
                   {:otsikko     "Päätöksen selitys"
                    :nimi        :paatoksen-selitys
                    :tyyppi      :text
                    :hae         (comp :perustelu :paatos)
                    :koko        [80 4]
                    :leveys-col  6
                    :aseta       #(assoc-in %1 [:paatos :perustelu] %2)
                    :muokattava? muokattava?
                    :validoi     [[:ei-tyhja "Anna päätöksen selitys"]]})


                 (when (= :sanktio (:paatos (:paatos @havainto)))
                   ;; FIXME: tarkista myös oikeus, urakanvalvoja... urakoitsija/konsultti EI saa päätöstä tehdä
                   {:otsikko     "Sanktiot"
                    :nimi        :sanktiot
                    :komponentti [havainnon-sanktiot
                                  (r/wrap (:sanktiot @havainto)
                                          #(swap! havainto assoc :sanktiot %))
                                  sanktio-virheet]})
                 ))]

            @havainto]])))))


(defn havainnot []
  (if @valittu-havainto
    [havainto {} valittu-havainto]
    [havaintolistaus]))
  
  
  
