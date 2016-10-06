(ns harja.views.urakka.laadunseuranta.laatupoikkeama
  "Yksittäisen laatupoikkeaman näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat-kartalla :as lp-kartalla]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [harja.domain.laadunseuranta :refer [validi-laatupoikkeama?]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset-nakyma]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn paatos?
  "Onko annetussa laatupoikkeamassa päätös?"
  [laatupoikkeama]
  (not (nil? (get-in laatupoikkeama [:paatos :paatos]))))

(defn tallenna-laatupoikkeama
  "Tallentaa annetun laatupoikkeaman palvelimelle. Lukee serveriltä palautuvan laatupoikkeaman ja
   päivittää/lisää sen nykyiseen listaukseen, jos se kuuluu listauksen aikavälille."
  [laatupoikkeama nakyma]
  (let [laatupoikkeama (as-> laatupoikkeama lp
                           (assoc lp :sanktiot (vals (:sanktiot lp)))
                             ;; Varmistetaan, että tietyssä näkymäkontekstissa tallennetaan vain näkymän
                             ;; sisältämät asiat (esim. on mahdollista vaihtaa koko valittu urakka päällystyksestä
                             ;; hoitoon, ja emme halua että hoidon lomakkeessa tallentuu myös ylläpitokohde)
                             (if (some #(= nakyma %) [:paallystys :paikkaus :tiemerkinta])
                               (dissoc lp :kohde)
                               (dissoc lp :yllapitokohde))
                             (if (integer? (:yllapitokohde lp))
                               lp
                               (assoc lp :yllapitokohde (get-in lp [:yllapitokohde :id]))))]
    (go
      (let [tulos (<! (laatupoikkeamat/tallenna-laatupoikkeama laatupoikkeama))]
        (if (k/virhe? tulos)
          ;; Palautetaan virhe, jotta nappi näyttää virheviestin
          tulos

          ;; Laatupoikkeama tallennettu onnistuneesti, päivitetään sen tiedot
          (let [uusi-laatupoikkeama tulos
                aika (:aika uusi-laatupoikkeama)
                [alku loppu] @tiedot-urakka/valittu-aikavali]
            (when (and (pvm/sama-tai-jalkeen? aika alku)
                       (pvm/sama-tai-ennen? aika loppu))
              ;; Kuuluu aikavälille, lisätään tai päivitetään
              (if (:id laatupoikkeama)
                ;; Päivitetty olemassaolevaa
                (swap! laatupoikkeamat/urakan-laatupoikkeamat
                       (fn [laatupoikkeamat]
                         (mapv (fn [h]
                                 (if (= (:id h) (:id uusi-laatupoikkeama))
                                   uusi-laatupoikkeama
                                   h)) laatupoikkeamat)))
                ;; Luotu uusi
                (swap! laatupoikkeamat/urakan-laatupoikkeamat
                       conj uusi-laatupoikkeama)))
            true))))))

(defn laatupoikkeaman-sanktiot
  "Näyttää muokkaus-gridin laatupoikkeaman sanktioista. Ottaa kaksi parametria, sanktiot (muokkaus-grid muodossa)
sekä sanktio-virheet atomin, jonne yksittäisen sanktion virheet kirjoitetaan (id avaimena)"
  [_ _ _]
  (let [g (grid/grid-ohjaus)]
    (fn [sanktiot-atom sanktio-virheet paatosoikeus?]
      [:div.sanktiot
       [grid/muokkaus-grid
        {:tyhja "Ei kirjattuja sanktioita."
         :lisaa-rivi " Lisää sanktio"
         :ohjaus g
         :voi-muokata? paatosoikeus?
         :uusi-rivi (fn [rivi]
                      (assoc rivi :sakko? true))}

        [{:otsikko "Perintäpvm" :nimi :perintapvm :tyyppi :pvm :leveys 1.5
          :validoi [[:ei-tyhja "Anna sanktion päivämäärä"]]}
         {:otsikko "Laji" :tyyppi :valinta :leveys 0.85
          :nimi :laji
          :aseta #(assoc %1
                   :laji %2
                   :tyyppi nil)
          :valinnat [:A :B :C]
          :valinta-nayta #(case %
                           :A "A"
                           :B "B"
                           :C "C"
                           "- valitse -")
          :validoi [[:ei-tyhja "Valitse laji"]]}
         {:otsikko "Tyyppi" :nimi :tyyppi :leveys 3
          :tyyppi :valinta
          :aseta (fn [sanktio {tpk :toimenpidekoodi :as tyyppi}]
                   ;; Asetetaan uusi sanktiotyyppi sekä toimenpideinstanssi, joka tähän kuuluu
                   (log "VALITTIIN TYYPPI: " (pr-str tyyppi))
                   (assoc sanktio
                     :tyyppi tyyppi
                     :toimenpideinstanssi
                     (when tpk
                       (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk)))))
          :valinnat-fn #(sanktiot/lajin-sanktiotyypit (:laji %))
          :valinta-nayta :nimi
          :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]}

         {:otsikko "Sakko/muistutus"
          :nimi :sakko?
          :tyyppi :valinta
          :hae #(if (:sakko? %) :sakko :muistutus)
          :aseta (fn [rivi arvo]
                   (let [sakko? (= :sakko arvo)]
                     (assoc rivi
                       :sakko? sakko?
                       :summa (when sakko? (:summa rivi))
                       :toimenpideinstanssi (when sakko?
                                              (:toimenpideinstanssi rivi)))))
          :valinnat [:sakko :muistutus]
          :valinta-nayta #(case %
                           :sakko "Sakko"
                           :muistutus "Muistutus")
          :leveys 2}

         {:otsikko "Toimenpide"
          :nimi :toimenpideinstanssi
          :tyyppi :valinta
          :valinta-arvo :tpi_id
          :valinta-nayta :tpi_nimi
          :valinnat-fn #(when (:sakko? %) @tiedot-urakka/urakan-toimenpideinstanssit)
          :leveys 3
          :validoi [[:ei-tyhja "Valitse toimenpide, johon sakko liittyy"]]
          :muokattava? :sakko?}

         {:otsikko "Sakko (€)"
          :tyyppi :numero
          :nimi :summa
          :leveys 1.5
          :validoi [[:ei-tyhja "Anna sakon summa euroina"]]
          :muokattava? :sakko?}

         (when (urakka/indeksi-kaytossa?)
           {:otsikko "Indeksi"
            :nimi :indeksi
            :leveys 1.5
            :tyyppi :valinta
            :valinnat ["MAKU 2005" "MAKU 2010"]               ;; FIXME: haetaanko indeksit tiedoista?
            :valinta-nayta #(or % "Ei sidota indeksiin")
            :palstoja 1
            :muokattava? :sakko?})]

        sanktiot-atom]])))

(defn avaa-tarkastus [tarkastus-id]
  (tarkastukset-nakyma/valitse-tarkastus tarkastus-id)
  (reset! (reitit/valittu-valilehti-atom :laadunseuranta) :tarkastukset))

(defn- tarkasta-sanktiotiedot [vertailu-fn laatupoikkeama]
  (vertailu-fn #(not (nil? %)) (map #(get-in laatupoikkeama %)
                                    [[:paatos :kasittelyaika]
                                     [:paatos :kasittelytapa]
                                     [:paatos :paatos]])))

(defn kaikki-sanktiotiedot-annettu? [laatupoikkeama]
  (tarkasta-sanktiotiedot every? laatupoikkeama))

(defn sanktiotietoja-annettu? [laatupoikkeama]
  (tarkasta-sanktiotiedot some laatupoikkeama))

(defn validoi-sanktiotiedot [laatupoikkeama]
  ;; Joko ei mitään sanktiotietoja, tai kaikki
  (log "Validoi-sanktiotiedot: " (pr-str laatupoikkeama))
  (or (not (sanktiotietoja-annettu? laatupoikkeama))
      (kaikki-sanktiotiedot-annettu? laatupoikkeama)))

(defn validoi-laatupoikkeama [laatupoikkeama]
  (if (and (not (lomake/muokattu? laatupoikkeama))
           (:id laatupoikkeama))
    false
    (not (and (lomake/voi-tallentaa-ja-muokattu? laatupoikkeama)
              (validoi-sanktiotiedot laatupoikkeama)))))

(defn lisaa-sanktion-validointi [tietoja-annettu-fn? kentta viesti]
  (merge kentta
         (when (tietoja-annettu-fn?)
           {:validoi [[:ei-tyhja viesti]]
            :pakollinen? true})))

(defn laatupoikkeamalomake
  ([laatupoikkeama] (laatupoikkeamalomake laatupoikkeama {}))
  ([laatupoikkeama optiot]
  (let [sanktio-virheet (atom {})
        muokattava? (not (paatos? @laatupoikkeama))
        urakka-id (:id @nav/valittu-urakka)
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-laatupoikkeamat
                                                  urakka-id)
        paatosoikeus? (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-laadunseuranta-sanktiot
                                               urakka-id @istunto/kayttaja)]
    (komp/luo
      (fn [laatupoikkeama optiot]
        (let [uusi? (not (:id @laatupoikkeama))
              sanktion-validointi (partial lisaa-sanktion-validointi
                                           #(sanktiotietoja-annettu? @laatupoikkeama))
              kohde-muuttui? (fn [vanha uusi] (not= vanha uusi))
              yllapitokohteet (:yllapitokohteet optiot)]
          (log "laatupoikkeama" (pr-str @laatupoikkeama))
          (if (and (some #(= (:nakyma optiot) %) [:paallystys :paikkaus :tiemerkinta])
                   (nil? yllapitokohteet)) ;; Pakko olla ylläpitokohteet ennen kuin lomaketta voi näyttää
            [ajax-loader "Ladataan..."]
            [:div.laatupoikkeama
             [napit/takaisin "Takaisin laatupoikkeamaluetteloon" #(reset! laatupoikkeamat/valittu-laatupoikkeama-id nil)]

            [lomake/lomake
             {:otsikko      "Laatupoikkeaman tiedot"
              :muokkaa!     #(let [uusi-lp
                                   (if (kohde-muuttui? (get-in @laatupoikkeama [:yllapitokohde :id])
                                                       (get-in % [:yllapitokohde :id]))
                                     (laatupoikkeamat/paivita-yllapitokohteen-tr-tiedot % yllapitokohteet)
                                     %)]
                              (log "muokkaa")
                              (reset! laatupoikkeama uusi-lp))
              :voi-muokata? @laatupoikkeamat/voi-kirjata?
              :footer       (when voi-kirjoittaa?
                              [napit/palvelinkutsu-nappi
                               ;; Määritellään "verbi" tilan mukaan, jos päätöstä ei ole: Tallennetaan laatupoikkeama,
                               ;; jos päätös on tässä muokkauksessa lisätty: Lukitaan laatupoikkeama
                               (cond
                                 (and (not (paatos? @laatupoikkeama))
                                      (paatos? @laatupoikkeama))
                                 "Tallenna ja lukitse laatupoikkeama"

                                 :default
                                 "Tallenna laatupoikkeama")

                               #(tallenna-laatupoikkeama @laatupoikkeama (:nakyma optiot))
                               {:ikoni        (ikonit/tallenna)
                                :disabled     (and (validoi-laatupoikkeama @laatupoikkeama))
                                :virheviesti  "Laatupoikkeaman tallennus epäonnistui"
                                :kun-onnistuu (fn [_] (reset! laatupoikkeamat/valittu-laatupoikkeama-id nil))}])}

             [{:otsikko "Päivämäärä ja aika"
               :pakollinen? true
               :muokattava? (constantly muokattava?)
               :tyyppi :pvm-aika
               :nimi :aika
               :validoi [[:ei-tyhja "Anna laatupoikkeaman päivämäärä ja aika"]]
               :huomauta [[:urakan-aikana-ja-hoitokaudella]]
               :palstoja 1}

              (if (or
                    (= (:nakyma optiot) :paallystys)
                    (= (:nakyma optiot) :paikkaus)
                    (= (:nakyma optiot) :tiemerkinta))
                {:otsikko       "Kohde" :tyyppi :valinta :nimi :yllapitokohde
                 ;:hae #(get-in % [:yllapitokohde :id])
                 :palstoja      1
                 :pakollinen?   true
                 :muokattava?   (constantly muokattava?)
                 :valinnat      yllapitokohteet
                 :jos-tyhja     "Ei valittavia kohteita"
                 :valinta-nayta (fn [arvo muokattava?]
                                  (if arvo (tierekisteri/yllapitokohde-tekstina arvo {:osoite arvo})
                                           (if muokattava?
                                             "- Valitse kohde -"
                                             "")))
                 :validoi       [[:ei-tyhja "Anna laatupoikkeaman kohde"]]}
                {:otsikko "Kohde" :tyyppi :string :nimi :kohde
                 :palstoja 1
                 :pakollinen? true
                 :muokattava? (constantly muokattava?)
                 :validoi [[:ei-tyhja "Anna laatupoikkeaman kohde"]]})

              {:otsikko "Tekijä" :nimi :tekija
               :uusi-rivi? true
               :tyyppi :valinta
               :valinnat [:tilaaja :urakoitsija :konsultti]
               :valinta-nayta #(case %
                                :tilaaja "Tilaaja"
                                :urakoitsija "Urakoitsija"
                                :konsultti "Konsultti"
                                "- valitse osapuoli -")
               :palstoja 1
               :muokattava? (constantly muokattava?)
               :validoi [[:ei-tyhja "Valitse laatupoikkeaman tehnyt osapuoli"]]}

              {:tyyppi :tierekisteriosoite
               :nimi :tr
               :muokattava? (constantly muokattava?)
               :sijainti (r/wrap (:sijainti @laatupoikkeama)
                                 #(swap! laatupoikkeama assoc :sijainti %))}

              (when-not (= :urakoitsija (:tekija @laatupoikkeama))
                {:nimi :selvitys-pyydetty
                 :tyyppi :checkbox
                 :teksti "Urakoitsijan selvitystä pyydetään"})

              {:otsikko "Kuvaus"
               :uusi-rivi? true
               :nimi :kuvaus
               :muokattava? (constantly muokattava?)
               :tyyppi :text
               :pakollinen? true
               :palstoja 2
               :validoi [[:ei-tyhja "Kirjoita kuvaus"]] :pituus-max 4096
               :placeholder "Kirjoita kuvaus..." :koko [80 :auto]}



              {:otsikko "Liitteet" :nimi :liitteet
               :palstoja 2
               :tyyppi :komponentti
               :komponentti (fn [_]
                              [liitteet/liitteet urakka-id (:liitteet @laatupoikkeama)
                              {:uusi-liite-atom (r/wrap (:uusi-liite @laatupoikkeama)
                                                        #(swap! laatupoikkeama assoc :uusi-liite %))
                               :uusi-liite-teksti "Lisää liite laatupoikkeamaan"}])}

              (when-not uusi?
                (lomake/ryhma
                  "Kommentit"
                  {:otsikko "" :nimi :kommentit :tyyppi :komponentti
                   :komponentti (fn [_]
                                  [kommentit/kommentit {:voi-kommentoida? true
                                                       :voi-liittaa true
                                                       :liita-nappi-teksti " Lisää liite kommenttiin"
                                                       :placeholder "Kirjoita kommentti..."
                                                       :uusi-kommentti (r/wrap (:uusi-kommentti @laatupoikkeama)
                                                                               #(swap! laatupoikkeama assoc :uusi-kommentti %))}
                                  (:kommentit @laatupoikkeama)])}))

              ;; Päätös
              (when (:id @laatupoikkeama)
                (lomake/ryhma
                  "Käsittely ja päätös"

                  (sanktion-validointi
                    {:otsikko "Käsittelyn pvm"
                     :nimi :paatos-pvm
                     :hae (comp :kasittelyaika :paatos)
                     :aseta #(assoc-in %1 [:paatos :kasittelyaika] %2)
                     :tyyppi :pvm-aika
                     :muokattava? (constantly (and muokattava? paatosoikeus?))}
                    "Anna käsittelyn päivämäärä ja aika")

                  (sanktion-validointi
                    {:otsikko "Käsitelty"
                     :nimi :kasittelytapa
                     :hae (comp :kasittelytapa :paatos)
                     :aseta #(assoc-in %1 [:paatos :kasittelytapa] %2)
                     :tyyppi :valinta
                     :valinnat [:tyomaakokous :puhelin :kommentit :muu]
                     :valinta-nayta #(if % (laatupoikkeamat/kuvaile-kasittelytapa %)
                                           (if paatosoikeus?
                                             "- Valitse käsittelytapa -"
                                             ""))
                     :palstoja 2
                     :muokattava? (constantly (and muokattava? paatosoikeus?))}
                    "Anna käsittelytapa")

                  (when (= :muu (:kasittelytapa (:paatos @laatupoikkeama)))
                    {:otsikko "Muu käsittelytapa"
                     :nimi :kasittelytapa-selite
                     :hae (comp :muukasittelytapa :paatos)
                     :aseta #(assoc-in %1 [:paatos :muukasittelytapa] %2)
                     :tyyppi :string
                     :palstoja 2
                     :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]
                     :muokattava? (constantly (and muokattava? paatosoikeus?))})


                  (sanktion-validointi
                    {:otsikko "Päätös"
                     :nimi :paatos-paatos
                     :tyyppi :valinta
                     :valinnat [:sanktio :ei_sanktiota :hylatty]
                     :hae (comp :paatos :paatos)
                     :aseta #(assoc-in %1 [:paatos :paatos] %2)
                     :valinta-nayta #(if % (laatupoikkeamat/kuvaile-paatostyyppi %)
                                           (if paatosoikeus?
                                             "- Valitse päätös -"
                                             ""))
                     :palstoja 2
                     :muokattava? (constantly (and muokattava? paatosoikeus?))}
                    "Anna päätös")

                  (when (:paatos (:paatos @laatupoikkeama))
                    {:otsikko "Päätöksen selitys"
                     :nimi :paatoksen-selitys
                     :pakollinen? true
                     :tyyppi :text
                     :hae (comp :perustelu :paatos)
                     :koko [80 :auto]
                     :palstoja 2
                     :aseta #(assoc-in %1 [:paatos :perustelu] %2)
                     :muokattava? (constantly (and muokattava? paatosoikeus?))
                     :validoi [[:ei-tyhja "Anna päätöksen selitys"]]})


                  (when (= :sanktio (:paatos (:paatos @laatupoikkeama)))
                    {:otsikko "Sanktiot"
                     :nimi :sanktiot
                     :tyyppi :komponentti
                     :palstoja 2
                     :komponentti (fn [_]
                                    [laatupoikkeaman-sanktiot
                                     (r/wrap (:sanktiot @laatupoikkeama)
                                             #(swap! laatupoikkeama assoc :sanktiot %))
                                     sanktio-virheet
                                     paatosoikeus?])})
                  (when (:tarkastusid @laatupoikkeama)
                    {:rivi? true
                     :uusi-rivi? true
                     :nimi :laatupoikkeama
                     :vihje "Tallentaa muutokset ja avaa tarkastuksen, jonka pohjalta laatupoikkeama on tehty."
                     :tyyppi :komponentti
                     :komponentti (fn [_]
                                    [napit/yleinen
                                    "Avaa tarkastus"
                                    (fn []
                                      (tallenna-laatupoikkeama @laatupoikkeama (:nakyma optiot))
                                      (avaa-tarkastus (:tarkastusid @laatupoikkeama)))
                                    {:ikoni (ikonit/livicon-arrow-left)}])})))]
             @laatupoikkeama]])))))))
